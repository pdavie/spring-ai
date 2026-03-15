/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.vectorstore.idol;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.util.Assert;

/**
 * A Spring AI vector store implementation using OpenText IDOL.
 *
 * @author pdavie
 */
public class IdolVectorStore extends AbstractObservationVectorStore {

	public static final String METADATA_IDOL_USER = "idol_user";

	public static final String DEFAULT_VECTOR_FIELD = "embedding";

	private final IdolApi idolApi;

	private final FilterExpressionConverter filterExpressionConverter;

	private final IdolVectorStoreOptions options;

	protected IdolVectorStore(Builder builder) {
		super(builder);
		Assert.notNull(builder.idolApi, "IdolApi must not be null");
		this.idolApi = builder.idolApi;
		this.filterExpressionConverter = builder.filterExpressionConverter;
		this.options = builder.options;
	}

	public static Builder builder(IdolApi idolApi, EmbeddingModel embeddingModel) {
		return new Builder(idolApi, embeddingModel);
	}

	@Override
	public void doAdd(List<Document> documents) {
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
				this.batchingStrategy);

		List<IdolApi.IdolDocument> idolDocuments = new java.util.ArrayList<>(documents.size());
		for (int i = 0; i < documents.size(); i++) {
			Document document = documents.get(i);
			float[] embedding = embeddings.get(i);
			idolDocuments
				.add(new IdolApi.IdolDocument(document.getId(), embedding, document.getMetadata(), document.getText()));
		}

		String username = documents.stream()
			.map(doc -> (String) doc.getMetadata().get(METADATA_IDOL_USER))
			.filter(u -> u != null)
			.findFirst()
			.orElse(null);

		this.idolApi.addDocument(new IdolApi.AddDocumentRequest(idolDocuments, username)).block();
	}

	@Override
	public void doDelete(List<String> idList) {
		this.idolApi.delete(idList).block();
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		float[] embedding = this.embeddingModel.embed(request.getQuery());
		String vectorString = embeddingToVectorString(embedding);

		String vectorField = this.options.getVectorField();

		String fieldText = "";
		if (request.hasFilterExpression()) {
			fieldText = this.filterExpressionConverter.convertExpression(request.getFilterExpression());
		}

		String username = null;
		if (request.hasFilterExpression()) {
			username = findUsername(request.getFilterExpression());
		}

		String print = "None";
		String printFields = null;

		if (request instanceof IdolSearchRequest idolRequest) {
			print = idolRequest.getPrint();
			printFields = idolRequest.getPrintFields();
			if (idolRequest.getVectorField() != null) {
				vectorField = idolRequest.getVectorField();
			}
		}

		String idolText = String.format("VECTOR{%s:0}:%s", vectorString, vectorField);

		IdolApi.QueryRequest queryRequest = new IdolApi.QueryRequest(idolText, fieldText, request.getTopK(), username,
				print, printFields, vectorField);

		IdolApi.QueryResponse response = this.idolApi.query(queryRequest).block();

		if (response == null || response.autnResponse() == null || response.autnResponse().responseData() == null
				|| response.autnResponse().responseData().hits() == null) {
			return List.of();
		}

		return response.autnResponse().responseData().hits().stream().map(hit -> {
			String content = "";
			Map<String, Object> metadata = new java.util.HashMap<>();
			if (hit.content() != null && hit.content().documents() != null && !hit.content().documents().isEmpty()) {
				Map<String, List<String>> docFields = hit.content().documents().get(0);
				for (Map.Entry<String, List<String>> entry : docFields.entrySet()) {
					if (entry.getKey().equalsIgnoreCase("DRECONTENT")) {
						content = String.join("\n", entry.getValue());
					}
					else {
						if (entry.getValue().size() == 1) {
							metadata.put(entry.getKey(), entry.getValue().get(0));
						}
						else {
							metadata.put(entry.getKey(), entry.getValue());
						}
					}
				}
			}
			return new Document(hit.reference(), content, metadata);
		}).collect(Collectors.toList());
	}

	private String findUsername(Filter.Expression expression) {
		if (expression.type() == Filter.ExpressionType.EQ && expression.left() instanceof Filter.Key key
				&& key.key().equals(METADATA_IDOL_USER) && expression.right() instanceof Filter.Value value) {
			return String.valueOf(value.value());
		}
		if (expression.left() instanceof Filter.Expression leftExpr) {
			String u = findUsername(leftExpr);
			if (u != null) {
				return u;
			}
		}
		if (expression.right() instanceof Filter.Expression rightExpr) {
			String u = findUsername(rightExpr);
			if (u != null) {
				return u;
			}
		}
		if (expression.left() instanceof Filter.Group group) {
			String u = findUsername(group.content());
			if (u != null) {
				return u;
			}
		}
		if (expression.right() instanceof Filter.Group group) {
			return findUsername(group.content());
		}
		return null;
	}

	private String embeddingToVectorString(float[] embedding) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < embedding.length; i++) {
			sb.append(embedding[i]);
			if (i < embedding.length - 1) {
				sb.append(",");
			}
		}
		return sb.toString();
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder("idol", operationName);
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final IdolApi idolApi;

		private IdolVectorStoreOptions options = new IdolVectorStoreOptions();

		private FilterExpressionConverter filterExpressionConverter = new IdolFilterExpressionConverter();

		public Builder(IdolApi idolApi, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(idolApi, "IdolApi must not be null");
			this.idolApi = idolApi;
		}

		public Builder filterExpressionConverter(FilterExpressionConverter filterExpressionConverter) {
			Assert.notNull(filterExpressionConverter, "filterExpressionConverter must not be null");
			this.filterExpressionConverter = filterExpressionConverter;
			return this;
		}

		public Builder options(IdolVectorStoreOptions options) {
			Assert.notNull(options, "IdolVectorStoreOptions must not be null");
			this.options = options;
			return this;
		}

		public Builder vectorField(String vectorField) {
			Assert.hasText(vectorField, "vectorField must not be empty");
			this.options.setVectorField(vectorField);
			return this;
		}

		@Override
		public IdolVectorStore build() {
			return new IdolVectorStore(this);
		}

	}

}
