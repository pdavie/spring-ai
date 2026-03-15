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
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.SolrContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Solr Vector Store with RAG.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class SolrVectorStoreRAGIT {

	private static final String SOLR_IMAGE = "solr:9.8";

	private static final String TEST_COLLECTION = "solr-rag-index";

	@Container
	private static final SolrContainer SOLR_CONTAINER = new SolrContainer(DockerImageName.parse(SOLR_IMAGE))
		.withZookeeper(true)
		.withCommand("solr", "start", "-f", "-c");

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

	@Test
	void solrRagTest() {
		getContextRunner().run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			ChatModel chatModel = context.getBean(ChatModel.class);

			// 1. Prepare documents from Solr website content
			List<Document> solrDocs = List.of(
					new Document("solr-about", getText("classpath:/test/data/solr-about.txt"),
							Map.of("source", "solr-website")),
					new Document("solr-features", getText("classpath:/test/data/solr-features.txt"),
							Map.of("source", "solr-website")));

			// 2. Add documents to Solr Vector Store
			vectorStore.add(solrDocs);

			// Wait for indexing
			Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.builder().query("What is Apache Solr?").topK(1).build());
				return !results.isEmpty();
			});

			// 3. Run a quick similarity search to find targeted known documents
			List<Document> searchResults = vectorStore
				.similaritySearch(SearchRequest.builder().query("vector search capabilities in Solr").topK(1).build());

			assertThat(searchResults).isNotEmpty();
			assertThat(searchResults.get(0).getId()).isEqualTo("solr-features");
			assertThat(searchResults.get(0).getText()).contains("vector search capabilities");

			// 4. Bonus points: RAG conversation
			String userQueryText = "What are the vector search capabilities of Apache Solr?";
			Query userQuery = new Query(userQueryText);

			// Retrieve context
			List<Document> contextDocs = vectorStore
				.similaritySearch(SearchRequest.builder().query(userQueryText).topK(2).build());

			// Augment query
			ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder().allowEmptyContext(true).build();
			Query augmentedQuery = augmenter.augment(userQuery, contextDocs);

			// Generate response
			AssistantMessage response = chatModel.call(new Prompt(augmentedQuery.text())).getResult().getOutput();

			assertThat(response.getText()).containsIgnoringCase("vector");
			assertThat(response.getText()).containsIgnoringCase("Solr");

			System.out.println("RAG Response: " + response.getText());

			// Clean up
			SolrClient solrClient = context.getBean(SolrClient.class);
			CollectionAdminRequest.deleteCollection(TEST_COLLECTION).process(solrClient);
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Bean
		public SolrClient solrClient() {
			return new Http2SolrClient.Builder(
					"http://" + SOLR_CONTAINER.getHost() + ":" + SOLR_CONTAINER.getSolrPort() + "/solr")
				.build();
		}

		@Bean
		public VectorStore vectorStore(EmbeddingModel embeddingModel) {
			SolrVectorStoreOptions options = new SolrVectorStoreOptions();
			options.setIndexName(TEST_COLLECTION);
			return SolrVectorStore.builder(embeddingModel)
				.http2SolrClientBuilder(new Http2SolrClient.Builder(
						"http://" + SOLR_CONTAINER.getHost() + ":" + SOLR_CONTAINER.getSolrPort() + "/solr"))
				.options(options)
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());
		}

		@Bean
		public ChatModel chatModel() {
			return OpenAiChatModel.builder()
				.openAiApi(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build())
				.build();
		}

	}

}
