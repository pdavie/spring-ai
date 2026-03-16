/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.solr;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.schema.FieldTypeDefinition;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

/**
 * Solr-based vector store implementation using the dense_vector field type.
 *
 * <p>
 * The store uses a Solr collection to persist vector embeddings along with their
 * associated document content and metadata. The implementation leverages Solr's vector
 * similarity search capabilities for efficient similarity search operations.
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Automatic schema initialization with configurable collection creation</li>
 * <li>Support for multiple similarity functions: Cosine, Euclidean, and Dot Product</li>
 * <li>Metadata filtering using Solr query strings</li>
 * <li>Configurable similarity thresholds for search results</li>
 * <li>Batch processing support with configurable strategies</li>
 * <li>Observation and metrics support through Micrometer</li>
 * </ul>
 *
 * <p>
 * Basic usage example:
 * </p>
 * <pre>{@code
 * SolrVectorStore vectorStore = SolrVectorStore.builder(solrClient, embeddingModel)
 *     .initializeSchema(true)
 *     .build();
 *
 * // Add documents
 * vectorStore.add(List.of(
 *     new Document("content1", Map.of("key1", "value1")),
 *     new Document("content2", Map.of("key2", "value2"))
 * ));
 *
 * // Search with filters
 * List<Document> results = vectorStore.similaritySearch(
 *     SearchRequest.query("search text")
 *         .withTopK(5)
 *         .withSimilarityThreshold(0.7)
 *         .withFilterExpression("key1 == 'value1'")
 * );
 * }</pre>
 *
 * <p>
 * Advanced configuration example:
 * </p>
 * <pre>{@code
 * SolrVectorStoreOptions options = new SolrVectorStoreOptions();
 * options.setIndexName("custom_vectors");
 * options.setSimilarity(SimilarityFunction.dot_product);
 * options.setDimensions(1536);
 *
 * SolrVectorStore vectorStore = SolrVectorStore.builder(solrClient, embeddingModel)
 *     .options(options)
 *     .initializeSchema(true)
 *     .build();
 * }</pre>
 *
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>Solr 9.0 or later</li>
 * <li>The 'vector-search' module must be enabled in Solr</li>
 * <li>Index mapping with id (string), content (text), metadata (object), and embedding
 * (dense_vector) fields</li>
 * </ul>
 *
 * @author Jemin Huh
 * @author Wei Jiang
 * @author Laura Trotta
 * @author Soby Chacko
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
public class SolrVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	private static String VECTOR_FIELD_NAME = "embedding";

	private static final Logger logger = LoggerFactory.getLogger(SolrVectorStore.class);

	private static final Map<SimilarityFunction, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(
			SimilarityFunction.cosine, VectorStoreSimilarityMetric.COSINE, SimilarityFunction.euclidean,
			VectorStoreSimilarityMetric.EUCLIDEAN, SimilarityFunction.dot_product, VectorStoreSimilarityMetric.DOT);

	private final SolrClient solrClient;

	private final SolrVectorStoreOptions options;

	private final FilterExpressionConverter filterExpressionConverter;

	private final boolean initializeSchema;

	protected SolrVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(Objects.requireNonNullElse(builder.httpSolrClientBuilder, builder.cloudSolrClientBuilder),
				"Must specify either httpSolrClientBuilder or cloudSolrClientBuilder");

		this.initializeSchema = builder.initializeSchema;
		this.options = builder.options;
		this.filterExpressionConverter = builder.filterExpressionConverter;

		this.solrClient = builder.cloudSolrClientBuilder != null ? builder.cloudSolrClientBuilder.build()
				: builder.httpSolrClientBuilder.build();
	}

	@Override
	public void doAdd(List<Document> documents) {
		if (this.initializeSchema) {
			afterPropertiesSet();
		}

		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
				this.batchingStrategy);

		try {
			var response = this.solrClient.add(this.options.getIndexName(), documents.stream().map(d -> {
				var s = new SolrInputDocument();
				s.addField("id", d.getId());
				s.addField("content", d.getText());
				for (var entry : d.getMetadata().entrySet()) {
					s.addField(entry.getKey(), entry.getValue());
				}
				final float[] embedding = embeddings.get(documents.indexOf(d));
				List<Float> embeddingList = IntStream.range(0, embedding.length)
					.mapToObj(i -> embedding[i])
					.collect(Collectors.toList());
				s.addField(VECTOR_FIELD_NAME, embeddingList);
				return s;
			}).collect(Collectors.toList()));

			if (response.getStatus() != 0) {
				throw new IllegalStateException("Failed to add documents to Solr");
			}
			this.solrClient.commit(this.options.getIndexName());
		}
		catch (SolrServerException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void doDelete(List<String> idList) {
		try {
			var response = this.solrClient.deleteById(idList);
			if (response.getStatus() != 0) {
				throw new IllegalStateException("Delete operation failed", response.getException());
			}
		}
		catch (SolrServerException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void doDelete(Filter.Expression filterExpression) {
		// For the index to be present, either it must be pre-created or set the
		// initializeSchema to true.
		if (!indexExists()) {
			throw new IllegalArgumentException("Index not found");
		}

		try {
			var response = this.solrClient.deleteByQuery(this.options.getIndexName(),
					getSolrQueryString(filterExpression));
			if (response.getStatus() != 0) {
				throw new IllegalStateException("Delete operation failed", response.getException());
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest searchRequest) {
		if (this.initializeSchema) {
			afterPropertiesSet();
		}

		try {
			float[] vectors = this.embeddingModel.embed(searchRequest.getQuery());
			String vectorString = IntStream.range(0, vectors.length)
				.mapToObj(i -> String.valueOf(vectors[i]))
				.collect(Collectors.joining(",", "[", "]"));

			var solrParams = new ModifiableSolrParams();
			solrParams.add("q", String.format("{!vectorSimilarity f=%s topK=%d}%s", VECTOR_FIELD_NAME,
					searchRequest.getTopK(), vectorString));
			if (searchRequest.hasFilterExpression()) {
				solrParams.add("fq", getSolrQueryString(searchRequest.getFilterExpression()));
			}
			solrParams.add("fl", "*,score");
			solrParams.add("rows", String.valueOf(searchRequest.getTopK()));

			var response = this.solrClient.query(this.options.getIndexName(), solrParams);
			return response.getResults()
				.stream()
				.map(this::toDocument)
				.filter(d -> d.getScore() >= searchRequest.getSimilarityThreshold())
				.collect(Collectors.toList());
		}
		catch (SolrServerException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	private String getSolrQueryString(Filter.Expression filterExpression) {
		return Objects.isNull(filterExpression) ? "*"
				: this.filterExpressionConverter.convertExpression(filterExpression);

	}

	private Document toDocument(SolrDocument hit) {
		String id = (String) hit.getFieldValue("id");
		String content = (String) hit.getFieldValue("content");
		Map<String, Object> metadata = hit.getFieldValueMap();
		metadata.remove("id");
		metadata.remove("content");
		metadata.remove(VECTOR_FIELD_NAME);

		Document document = new Document(id, content, metadata);
		Document.Builder documentBuilder = document.mutate();
		if (hit.get("score") != null) {
			float score = (Float) hit.get("score");
			double normalizedScore = normalizeSimilarityScore(score);
			documentBuilder.score(normalizedScore);
		}
		return documentBuilder.build();
	}

	private double normalizeSimilarityScore(double score) {
		if (this.options.getSimilarity().equals(SimilarityFunction.euclidean)) {
			return 1.0 / (1.0 + score);
		}
		return score;
	}

	public boolean indexExists() {
		try {
			List<String> collections = CollectionAdminRequest.listCollections(this.solrClient);
			return collections != null && collections.contains(this.options.getIndexName());
		}
		catch (SolrServerException | IOException e) {
			throw new RuntimeException("Failed to check if index exists", e);
		}
	}

	public void createIndexMapping() {
		try {
			// Create collection if it doesn't exist
			if (!indexExists()) {
				var createRequest = CollectionAdminRequest.createCollection(this.options.getIndexName(), 1, 1);
				createRequest.process(this.solrClient);
			}

			// Add field type for dense vector
			FieldTypeDefinition fieldTypeDefinition = new FieldTypeDefinition();
			Map<String, Object> attributes = new java.util.HashMap<>();
			attributes.put("name", "knn_vector_" + this.embeddingModel.dimensions());
			attributes.put("class", "solr.DenseVectorField");
			attributes.put("vectorDimension", this.embeddingModel.dimensions());
			attributes.put("similarityFunction", this.options.getSimilarity().name());
			attributes.put("knnAlgorithm", "hnsw");
			fieldTypeDefinition.setAttributes(attributes);

			new SchemaRequest.AddFieldType(fieldTypeDefinition).process(this.solrClient, this.options.getIndexName());

			new SchemaRequest.AddField(Map.of("name", "content", "type", "text_general", "stored", true))
				.process(this.solrClient, this.options.getIndexName());

			new SchemaRequest.AddField(Map.of("name", VECTOR_FIELD_NAME, "type",
					"knn_vector_" + this.embeddingModel.dimensions(), "indexed", true, "stored", true))
				.process(this.solrClient, this.options.getIndexName());

			// Dynamic field for metadata
			new SchemaRequest.AddDynamicField(
					Map.of("name", "*", "type", "text_general", "indexed", true, "stored", true))
				.process(this.solrClient, this.options.getIndexName());

		}
		catch (SolrServerException | IOException e) {
			// It might fail if fields already exist, we can ignore those or check first
			logger.warn("Schema initialization might have partially failed or schema already exists: {}",
					e.getMessage());
		}
	}

	@Override
	public void afterPropertiesSet() {
		if (!this.initializeSchema) {
			return;
		}
		if (!indexExists()) {
			createIndexMapping();
		}
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder(VectorStoreProvider.SOLR.value(), operationName)
			.collectionName(this.options.getIndexName())
			.dimensions(this.embeddingModel.dimensions())
			.similarityMetric(getSimilarityMetric());
	}

	private String getSimilarityMetric() {
		if (!SIMILARITY_TYPE_MAPPING.containsKey(this.options.getSimilarity())) {
			return this.options.getSimilarity().name();
		}
		return SIMILARITY_TYPE_MAPPING.get(this.options.getSimilarity()).value();
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.solrClient;
		return Optional.of(client);
	}

	/**
	 * Creates a new builder instance for ElasticsearchVectorStore.
	 * @return a new ElasticsearchBuilder instance
	 */
	public static Builder builder(EmbeddingModel embeddingModel) {
		return new Builder(embeddingModel);
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private SolrVectorStoreOptions options = new SolrVectorStoreOptions();

		private boolean initializeSchema = false;

		private FilterExpressionConverter filterExpressionConverter = new SolrAiSearchFilterExpressionConverter();

		private Http2SolrClient.Builder httpSolrClientBuilder;

		private CloudSolrClient.Builder cloudSolrClientBuilder;

		/**
		 * @param embeddingModel the Embedding Model to be used
		 */
		public Builder(EmbeddingModel embeddingModel) {
			super(embeddingModel);
		}

		/**
		 * Sets the Http2SolrClient.Builder for the builder instance. Either this or
		 * cloudSolrClientBuilder must be provided.
		 * @param builder the Http2SolrClient.Builder instance to use; must not be null
		 * @return the current builder instance
		 * @throws IllegalArgumentException if the provided builder is null
		 */
		public Builder http2SolrClientBuilder(@NonNull Http2SolrClient.Builder builder) {
			Assert.notNull(builder, "http2SolrClientBuilder must not be null");
			this.httpSolrClientBuilder = builder;
			return this;
		}

		/**
		 * Sets the CloudSolrClient.Builder for the builder instance. Either this or
		 * http2SolrClientBuilder must be provided.
		 * @param builder the CloudSolrClient.Builder instance to use
		 * @return the current builder instance
		 * @throws IllegalArgumentException if the provided builder is null
		 */
		public Builder cloudSolrClientBuilder(@NonNull CloudSolrClient.Builder builder) {
			Assert.notNull(builder, "cloudSolrClientBuilder must not be null");
			this.cloudSolrClientBuilder = builder;
			return this;
		}

		/**
		 * Sets the Solr vector store options.
		 * @param options the vector store options to use
		 * @return the builder instance
		 * @throws IllegalArgumentException if options is null
		 */
		public Builder options(SolrVectorStoreOptions options) {
			Assert.notNull(options, "options must not be null");
			this.options = options;
			return this;
		}

		/**
		 * Sets whether to initialize the schema.
		 * @param initializeSchema true to initialize schema, false otherwise
		 * @return the builder instance
		 */
		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		/**
		 * Sets the filter expression converter.
		 * @param converter the filter expression converter to use
		 * @return the builder instance
		 * @throws IllegalArgumentException if converter is null
		 */
		public Builder filterExpressionConverter(FilterExpressionConverter converter) {
			Assert.notNull(converter, "filterExpressionConverter must not be null");
			this.filterExpressionConverter = converter;
			return this;
		}

		/**
		 * Builds the SolrVectorStore instance.
		 * @return a new SolrVectorStore instance
		 * @throws IllegalStateException if the builder is in an invalid state
		 */
		@Override
		public SolrVectorStore build() {
			return new SolrVectorStore(this);
		}

	}

}
