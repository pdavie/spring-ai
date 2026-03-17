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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.idol.api.AddDocumentRequest;
import org.springframework.ai.vectorstore.idol.api.IdolDocument;
import org.springframework.ai.vectorstore.idol.api.QueryRequest;
import org.springframework.ai.vectorstore.idol.api.QueryResponse;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A Spring AI vector store implementation using OpenText IDOL.
 *
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li><b>ACI Server:</b> Required for performing vector searches (Query action).
 * Configuration of host and port is required.</li>
 * <li><b>Index Server:</b> Required for indexing or deleting documents (DREADDDATA and
 * DREDELETEREF actions). Configuration of host and port is required.</li>
 * </ul>
 *
 * @author pdavie
 */
public class IdolVectorStore extends AbstractObservationVectorStore {

	/**
	 * Metadata key for the IDOL user.
	 */
	public static final String METADATA_IDOL_USER = "idol_user";

	/**
	 * Default name of the field to store the vectors. Vector is defined as a Vector field
	 * by default in modern IDOL installations.
	 */
	public static final String DEFAULT_VECTOR_FIELD = "Vector";

	/**
	 * Maximum number of documents to return in a delete by filter operation.
	 */
	private static final int DELETE_BY_FILTER_MAX_HITS = 10000;

	/**
	 * The IDOL API client.
	 */
	private final IdolApi idolApi;

	/**
	 * The filter expression converter.
	 */
	private final FilterExpressionConverter filterExpressionConverter;

	/**
	 * The IDOL vector store options.
	 */
	private final IdolVectorStoreOptions options;

	/**
	 * Create a new IDOL vector store.
	 * @param builder the builder
	 */
	protected IdolVectorStore(Builder builder) {
		super(builder);
		Assert.notNull(builder.idolApi, "IdolApi must not be null");
		this.idolApi = builder.idolApi;
		this.filterExpressionConverter = builder.filterExpressionConverter;
		this.options = builder.options;
	}

	/**
	 * Get a new IDOL vector store builder.
	 * @param idolApi the IDOL API client
	 * @param embeddingModel the embedding model
	 * @return the builder
	 */
	public static Builder builder(IdolApi idolApi, EmbeddingModel embeddingModel) {
		return new Builder(idolApi, embeddingModel);
	}

	@Override
	public final void doAdd(List<Document> documents) {
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);

		int batchSize = this.options.getMaxDocumentBatchSize();
		for (int i = 0; i < documents.size(); i += batchSize) {
			int end = Math.min(i + batchSize, documents.size());
			List<Document> batch = documents.subList(i, end);

			List<IdolDocument> idolDocuments = new ArrayList<>(batch.size());
			for (int j = 0; j < batch.size(); j++) {
				Document document = batch.get(j);
				float[] embedding = embeddings.get(i + j);
				idolDocuments
					.add(new IdolDocument(document.getId(), embedding, document.getMetadata(), document.getText()));
			}

			String username = batch.stream()
				.map(doc -> (String) doc.getMetadata().get(METADATA_IDOL_USER))
				.filter(u -> u != null)
				.findFirst()
				.orElse(null);

			this.idolApi.addDocument(new AddDocumentRequest(idolDocuments, username)).block();
		}
	}

	@Override
	public final void doDelete(List<String> idList) {
		this.idolApi.delete(idList).block();
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "filterExpression must not be null");

		String fieldText = this.filterExpressionConverter.convertExpression(filterExpression);

		// We use SaveState=True to get a state token for all documents matching the
		// filter, then delete by state ID. This is more efficient than deleting by
		// reference for large numbers of documents.
		QueryRequest queryRequest = QueryRequest.builder()
			.text("*")
			.fieldText(fieldText)
			.maxResults(100000)
			.saveState(true)
			.build();

		QueryResponse response = this.idolApi.query(queryRequest).block();

		if (response != null && response.autnResponse() != null && response.autnResponse().responseData() != null) {
			String stateId = response.autnResponse().responseData().state();
			if (StringUtils.hasText(stateId)) {
				this.idolApi.deleteByState(stateId).block();
			}
		}
	}

	@Override
	public final List<Document> doSimilaritySearch(SearchRequest request) {
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

		QueryRequest queryRequest = QueryRequest.builder()
			.text(idolText)
			.fieldText(fieldText)
			.maxResults(request.getTopK())
			.username(username)
			.print(print)
			.printFields(printFields)
			.vectorField(vectorField)
			.build();

		QueryResponse response = this.idolApi.query(queryRequest).block();

		if (response == null || response.autnResponse() == null || response.autnResponse().responseData() == null
				|| response.autnResponse().responseData().hits() == null) {
			return List.of();
		}

		return response.autnResponse().responseData().hits().stream().map(hit -> {
			String contentResult = "";
			Map<String, Object> metadata = new HashMap<>();
			if (hit.content() != null && hit.content().document() != null) {
				Map<String, Object> docFields = hit.content().document();
				for (Map.Entry<String, Object> entry : docFields.entrySet()) {
					Object value = entry.getValue();
					Object firstValue = value;
					if (value instanceof List && !((List<?>) value).isEmpty()) {
						firstValue = ((List<?>) value).get(0);
					}

					if (entry.getKey().equalsIgnoreCase("DRECONTENT")) {
						contentResult = String.valueOf(firstValue);
					}
					else {
						metadata.put(entry.getKey(), firstValue);
					}
				}
			}

			double score = (hit.weight() != null) ? hit.weight() / 100.0 : 0.0;
			metadata.put(DocumentMetadata.DISTANCE.value(), 1.0 - score);

			return Document.builder().id(hit.reference()).text(contentResult).metadata(metadata).score(score).build();
		}).collect(Collectors.toList());
	}

	/**
	 * Find the username in the filter expression.
	 * @param expression the filter expression
	 * @return the username or null if not found
	 */
	@Nullable
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

	/**
	 * Convert an embedding to a vector string.
	 * @param embedding the embedding
	 * @return the vector string
	 */
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
	public final VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder(VectorStoreProvider.IDOL.value(), operationName)
			.collectionName(this.options.getDatabase())
			.dimensions(this.embeddingModel.dimensions())
			.fieldName(this.options.getVectorField());
	}

	@Override
	public final <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.idolApi;
		return Optional.of(client);
	}

	public static final class Builder extends AbstractVectorStoreBuilder<Builder> {

		/**
		 * The IDOL API client.
		 */
		private final IdolApi idolApi;

		/**
		 * The IDOL vector store options.
		 */
		private IdolVectorStoreOptions options = new IdolVectorStoreOptions();

		/**
		 * The filter expression converter.
		 */
		private FilterExpressionConverter filterExpressionConverter = new IdolFilterExpressionConverter();

		/**
		 * Create a new builder.
		 * @param idolApi the IDOL API client
		 * @param embeddingModel the embedding model
		 */
		public Builder(final IdolApi idolApi, final EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(idolApi, "IdolApi must not be null");
			this.idolApi = idolApi;
		}

		/**
		 * Set the filter expression converter.
		 * @param converter the converter
		 * @return the builder
		 */
		public Builder filterExpressionConverter(FilterExpressionConverter converter) {
			Assert.notNull(converter, "filterExpressionConverter must not be null");
			this.filterExpressionConverter = converter;
			return this;
		}

		/**
		 * Set the IDOL vector store options.
		 * @param optionsParam the options
		 * @return the builder
		 */
		public Builder options(IdolVectorStoreOptions optionsParam) {
			Assert.notNull(optionsParam, "IdolVectorStoreOptions must not be null");
			this.options = optionsParam;
			return this;
		}

		/**
		 * Set the name of the field to store the vectors.
		 * @param vectorFieldParam the vector field name
		 * @return the builder
		 */
		public Builder vectorField(String vectorFieldParam) {
			Assert.hasText(vectorFieldParam, "vectorField must not be empty");
			this.options.setVectorField(vectorFieldParam);
			return this;
		}

		@Override
		public IdolVectorStore build() {
			return new IdolVectorStore(this);
		}

	}

}
