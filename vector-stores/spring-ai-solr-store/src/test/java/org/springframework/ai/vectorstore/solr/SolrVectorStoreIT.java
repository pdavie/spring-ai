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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.response.CollectionAdminResponse;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.SolrContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.test.vectorstore.BaseVectorStoreTests;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class SolrVectorStoreIT extends BaseVectorStoreTests {

	private static final String SOLR_IMAGE = "solr:9.8";

	private static final String TEST_COLLECTION = "spring-ai-document-index";

	@Container
	private static final SolrContainer SOLR_CONTAINER = new SolrContainer(DockerImageName.parse(SOLR_IMAGE))
		.withZookeeper(true);

	static {
		SOLR_CONTAINER.start();
	}

	private final List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	@BeforeAll
	public static void beforeAll() {
		Awaitility.setDefaultPollInterval(Duration.ofSeconds(2));
		Awaitility.setDefaultPollDelay(Duration.ZERO);
	}

	private String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private ApplicationContextRunner getContextRunner() {
		return new ApplicationContextRunner().withUserConfiguration(TestApplication.class);
	}

	@BeforeEach
	void cleanDatabase() {
		getContextRunner().run(context -> {
			// deleting indices and data before following tests
			SolrClient solrClient = context.getBean(SolrClient.class);
			CollectionAdminResponse response = CollectionAdminRequest.deleteCollection(TEST_COLLECTION)
				.process(solrClient);
			assertThat(response).isNotNull();
			assertThat(response.getStatus()).isEqualTo(0);
		});
	}

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		getContextRunner().run(context -> {
			VectorStore vectorStore = context.getBean("vectorStore_cosine", VectorStore.class);
			testFunction.accept(vectorStore);
		});
	}

	@Test
	public void addAndDeleteDocumentsTest() {
		getContextRunner().run(context -> {
			SolrVectorStore vectorStore = context.getBean("vectorStore_cosine", SolrVectorStore.class);
			SolrClient solrClient = context.getBean(SolrClient.class);
			long documentCount = getDocumentCount(solrClient, TEST_COLLECTION);
			assertThat(documentCount).isEqualTo(0);

			vectorStore.add(this.documents);
			documentCount = getDocumentCount(solrClient, TEST_COLLECTION);
			assertThat(documentCount).isEqualTo(3);

			vectorStore.doDelete(List.of("1", "2", "3"));
			documentCount = getDocumentCount(solrClient, TEST_COLLECTION);
			assertThat(documentCount).isEqualTo(0);
		});
	}

	private static long getDocumentCount(SolrClient solrClient, final String collectionName)
			throws SolrServerException, IOException {
		var query = new SolrQuery("*:*");
		query.setRows(0);
		var response = solrClient.query(collectionName, query);
		return response.getResults().getNumFound();
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "cosine", "euclidean", "dot_product" })
	public void addAndSearchTest(String similarityFunction) {

		getContextRunner().run(context -> {

			SolrVectorStore vectorStore = context.getBean("vectorStore_" + similarityFunction, SolrVectorStore.class);

			vectorStore.add(this.documents);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThresholdAll().build()),
						hasSize(1));

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("Great Depression").topK(1).similarityThresholdAll().build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThresholdAll().build()),
						hasSize(0));
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "cosine", "euclidean", "dot_product" })
	public void searchWithFilters(String similarityFunction) {

		getContextRunner().run(context -> {
			SolrVectorStore vectorStore = context.getBean("vectorStore_" + similarityFunction, SolrVectorStore.class);

			var bgDocument = new Document("1", "The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2020, "activationDate", new Date(1000)));
			var nlDocument = new Document("2", "The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "NL", "activationDate", new Date(2000)));
			var bgDocument2 = new Document("3", "The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2023, "activationDate", new Date(3000)));

			vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("The World").topK(5).similarityThresholdAll().build()),
						hasSize(3));

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'NL'")
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'BG'")
				.build());

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'BG' && year == 2020")
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country in ['BG']")
				.build());

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country in ['BG','NL']")
				.build());

			assertThat(results).hasSize(3);

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country not in ['BG']")
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("NOT(country not in ['BG'])")
				.build());

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression(
						"activationDate > " + ZonedDateTime.parse("1970-01-01T00:00:02Z").toInstant().toEpochMilli())
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument2.getId());

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(SearchRequest.builder().query("The World").topK(1).build()),
						hasSize(0));
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "cosine", "l2_norm", "dot_product" })
	public void documentUpdateTest(String similarityFunction) {

		getContextRunner().run(context -> {
			SolrVectorStore vectorStore = context.getBean("vectorStore_" + similarityFunction, SolrVectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Map.of("meta1", "meta1"));
			vectorStore.add(List.of(document));

			Awaitility.await()
				.until(() -> vectorStore
					.similaritySearch(SearchRequest.builder().query("Spring").similarityThresholdAll().topK(5).build()),
						hasSize(1));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").similarityThresholdAll().topK(5).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner", Map.of("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));
			SearchRequest fooBarSearchRequest = SearchRequest.builder()
				.query("FooBar")
				.topK(5)
				.similarityThresholdAll()
				.build();

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(fooBarSearchRequest).get(0).getText(),
						equalTo("The World is Big and Salvation Lurks Around the Corner"));

			results = vectorStore.similaritySearch(fooBarSearchRequest);

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			// Remove all documents from the store
			vectorStore.delete(List.of(document.getId()));

			Awaitility.await().until(() -> vectorStore.similaritySearch(fooBarSearchRequest), hasSize(0));

		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "cosine", "l2_norm", "dot_product" })
	public void searchThresholdTest(String similarityFunction) {
		getContextRunner().run(context -> {
			SolrVectorStore vectorStore = context.getBean("vectorStore_" + similarityFunction, SolrVectorStore.class);

			vectorStore.add(this.documents);

			SearchRequest query = SearchRequest.builder()
				.query("Great Depression")
				.topK(50)
				.similarityThresholdAll()
				.build();

			Awaitility.await().until(() -> vectorStore.similaritySearch(query), hasSize(3));

			List<Document> fullResult = vectorStore.similaritySearch(query);

			List<Double> scores = fullResult.stream().map(Document::getScore).toList();

			assertThat(scores).hasSize(3);

			double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("Great Depression")
				.topK(50)
				.similarityThreshold(similarityThreshold)
				.build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(2).getId());
			assertThat(resultDoc.getText()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());
			assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(50).similarityThresholdAll().build()),
						hasSize(0));
		});
	}

	@Test
	public void overDefaultSizeTest() {

		var overDefaultSize = 12;

		getContextRunner().run(context -> {

			SolrVectorStore vectorStore = context.getBean("vectorStore_cosine", SolrVectorStore.class);

			var testDocs = new ArrayList<Document>();
			for (int i = 0; i < overDefaultSize; i++) {
				testDocs.add(new Document(String.valueOf(i), "Great Depression " + i, Map.of()));
			}
			vectorStore.add(testDocs);

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThresholdAll().build()),
						hasSize(1));

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("Great Depression")
				.topK(overDefaultSize)
				.similarityThresholdAll()
				.build());

			assertThat(results).hasSize(overDefaultSize);

			// Remove all documents from the store
			vectorStore.delete(testDocs.stream().map(Document::getId).toList());

			Awaitility.await()
				.until(() -> vectorStore.similaritySearch(
						SearchRequest.builder().query("Great Depression").topK(1).similarityThresholdAll().build()),
						hasSize(0));
		});
	}

	@Test
	public void getNativeClientTest() {
		getContextRunner().run(context -> {
			SolrVectorStore vectorStore = context.getBean("vectorStore_cosine", SolrVectorStore.class);

			// Test successful native client retrieval
			Optional<SolrClient> nativeClient = vectorStore.getNativeClient();
			assertThat(nativeClient).isPresent();

			// Verify client functionality
			SolrClient client = nativeClient.get();
			SolrQuery solrQuery = new SolrQuery();
			solrQuery.set("q", "*:*");
			var docs = client.query("vectorStore_cosine", solrQuery);
			assertThat(docs).isNotNull();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Bean("vectorStore_cosine")
		public SolrVectorStore vectorStoreDefault(EmbeddingModel embeddingModel, Http2SolrClient.Builder builder) {
			return SolrVectorStore.builder(embeddingModel)
				.http2SolrClientBuilder(builder)
				.initializeSchema(true)
				.build();
		}

		@Bean("vectorStore_euclidean")
		public SolrVectorStore vectorStoreEuclidean(EmbeddingModel embeddingModel, Http2SolrClient.Builder builder) {
			SolrVectorStoreOptions options = new SolrVectorStoreOptions();
			options.setIndexName("index_euclidean");
			options.setSimilarity(SimilarityFunction.euclidean);
			return SolrVectorStore.builder(embeddingModel)
				.http2SolrClientBuilder(builder)
				.initializeSchema(true)
				.options(options)
				.build();
		}

		@Bean("vectorStore_dot_product")
		public SolrVectorStore vectorStoreDotProduct(EmbeddingModel embeddingModel, Http2SolrClient.Builder builder) {
			SolrVectorStoreOptions options = new SolrVectorStoreOptions();
			options.setIndexName("index_dot_product");
			options.setSimilarity(SimilarityFunction.dot_product);
			return SolrVectorStore.builder(embeddingModel)
				.http2SolrClientBuilder(builder)
				.initializeSchema(true)
				.options(options)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());
		}

		@Bean
		public Http2SolrClient.Builder http2SolrClientBuilder() {
			return new Http2SolrClient.Builder(
					"http://" + SOLR_CONTAINER.getHost() + ":" + SOLR_CONTAINER.getSolrPort() + "/solr");
		}

	}

}
