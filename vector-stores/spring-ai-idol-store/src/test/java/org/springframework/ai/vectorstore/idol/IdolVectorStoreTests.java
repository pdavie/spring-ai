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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.idol.api.AddDocumentRequest;
import org.springframework.ai.vectorstore.idol.api.AutnResponse;
import org.springframework.ai.vectorstore.idol.api.Content;
import org.springframework.ai.vectorstore.idol.api.Hit;
import org.springframework.ai.vectorstore.idol.api.IdolDocument;
import org.springframework.ai.vectorstore.idol.api.QueryRequest;
import org.springframework.ai.vectorstore.idol.api.QueryResponse;
import org.springframework.ai.vectorstore.idol.api.ResponseData;
import org.springframework.ai.vectorstore.idol.api.UserReadAutnResponse;
import org.springframework.ai.vectorstore.idol.api.UserReadResponse;
import org.springframework.ai.vectorstore.idol.api.UserReadResponseData;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IdolVectorStore}.
 *
 * @author pdavie
 */
@ExtendWith(MockitoExtension.class)
class IdolVectorStoreTests {

	@Mock
	private IdolApi idolApi;

	@Mock
	private EmbeddingModel embeddingModel;

	@Mock
	private WebClient.RequestBodyUriSpec requestBodyUriSpec;

	@Mock
	private WebClient.RequestBodySpec requestBodySpec;

	@Mock
	private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

	@Mock
	private WebClient.RequestHeadersSpec requestHeadersSpec;

	@Mock
	private WebClient.ResponseSpec responseSpec;

	@Test
	void addDocuments() {
		when(this.idolApi.addDocument(any())).thenReturn(Mono.empty());
		IdolVectorStore vectorStore = IdolVectorStore.builder(this.idolApi, this.embeddingModel).build();

		Document doc = new Document("1", "content", Map.of("key", "value"));
		when(this.embeddingModel.embed(any(List.class), any(), any())).thenReturn(List.of(new float[] { 0.1f, 0.2f }));

		vectorStore.add(List.of(doc));

		ArgumentCaptor<AddDocumentRequest> captor = ArgumentCaptor.forClass(AddDocumentRequest.class);
		verify(this.idolApi).addDocument(captor.capture());

		AddDocumentRequest request = captor.getValue();
		assertThat(request.documents()).hasSize(1);
		assertThat(request.documents().get(0).reference()).isEqualTo("1");
		assertThat(request.documents().get(0).content()).isEqualTo("content");
		assertThat(request.documents().get(0).metadata()).containsEntry("key", "value");
	}

	@Test
	void testIdxFormatting() {
		WebClient webClient = org.mockito.Mockito.mock(WebClient.class);
		when(webClient.post()).thenReturn(this.requestBodyUriSpec);
		when(this.requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(this.requestBodySpec);
		when(this.requestBodySpec.contentType(any())).thenReturn(this.requestBodySpec);
		when(this.requestBodySpec.contentLength(any(Long.class))).thenReturn(this.requestBodySpec);
		when(this.requestBodySpec.bodyValue(any())).thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);
		when(this.responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

		IdolApi api = new IdolApi(webClient, webClient, webClient, "testdb", "custom_vector_field");

		IdolDocument doc = new IdolDocument("ref1", new float[] { 0.1f },
				Map.of("meta1", "val1", "title", "Custom Title"), "content1");
		api.addDocument(new IdolApi.AddDocumentRequest(List.of(doc), null)).block();

		ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
		verify(this.requestBodySpec).bodyValue(bodyCaptor.capture());

		String body = new String(bodyCaptor.getValue());
		assertThat(body).contains("#DREREFERENCE ref1");
		assertThat(body).contains("#DREFIELD meta1=\"val1\"");
		assertThat(body).contains("#DREFIELD title=\"Custom Title\"");
		assertThat(body).contains("#DREFIELD custom_vector_field=\"0.1\"");
		assertThat(body).contains("#DRETITLE\nCustom Title");
		assertThat(body).contains("#DRECONTENT\ncontent1");
		assertThat(body).contains("#DREDBNAME testdb");
		assertThat(body).contains("#DREENDDOC");
		assertThat(body).contains("#DREENDDATAREFERENCE\r\n\r\n");

		ArgumentCaptor<Long> lengthCaptor = ArgumentCaptor.forClass(Long.class);
		verify(this.requestBodySpec).contentLength(lengthCaptor.capture());
		assertThat(lengthCaptor.getValue()).isEqualTo((long) body.getBytes().length);
	}

	@Test
	void similaritySearch() {
		IdolVectorStore vectorStore = IdolVectorStore.builder(this.idolApi, this.embeddingModel).build();

		when(this.embeddingModel.embed("query")).thenReturn(new float[] { 0.1f, 0.2f });

		IdolApi.QueryResponse response = new IdolApi.QueryResponse(new IdolApi.AutnResponse(IdolApi.ResponseData.builder()
			.hits(List.of(new IdolApi.Hit("123", "my-document-id", 0.9,
					new IdolApi.Content(Map.of("DRECONTENT", List.of("content1"), "meta1", List.of("val1"))))))
			.build()));

		when(this.idolApi.query(any())).thenReturn(Mono.just(response));

		List<Document> results = vectorStore.similaritySearch(SearchRequest.builder().query("query").topK(1).build());

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo("my-document-id");
		assertThat(results.get(0).getText()).isEqualTo("content1");
		assertThat(results.get(0).getMetadata()).containsEntry("meta1", "val1");

		ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
		verify(this.idolApi).query(captor.capture());
		assertThat(captor.getValue().text()).contains("VECTOR{0.1,0.2:0}:Vector");
		assertThat(captor.getValue().print()).isEqualTo("None");
	}

	@Test
	void similaritySearchWithPrintFields() {
		IdolVectorStore vectorStore = IdolVectorStore.builder(this.idolApi, this.embeddingModel).build();

		when(this.embeddingModel.embed("query")).thenReturn(new float[] { 0.1f, 0.2f });

		IdolApi.QueryResponse response = new IdolApi.QueryResponse(
				new IdolApi.AutnResponse(new IdolApi.ResponseDataResponseData.builder().hits(List.of()).build()));

		when(this.idolApi.query(any())).thenReturn(Mono.just(response));

		IdolSearchRequest searchRequest = IdolSearchRequest.idolBuilder()
			.query("query")
			.topK(1)
			.print("PrintFields")
			.printFields("meta1,meta2")
			.build();

		vectorStore.similaritySearch(searchRequest);

		ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
		verify(this.idolApi).query(captor.capture());
		assertThat(captor.getValue().print()).isEqualTo("PrintFields");
		assertThat(captor.getValue().printFields()).isEqualTo("meta1,meta2");
	}

	@Test
	void similaritySearchWithCustomVectorField() {
		IdolVectorStore vectorStore = IdolVectorStore.builder(this.idolApi, this.embeddingModel)
			.vectorField("store_vector_field")
			.build();

		when(this.embeddingModel.embed("query")).thenReturn(new float[] { 0.1f, 0.2f });

		IdolApi.QueryResponse response = new IdolApi.QueryResponse(
				new IdolApi.AutnResponse(new IdolApi.ResponseData.builder().hits(List.of()).build()));

		when(this.idolApi.query(any())).thenReturn(Mono.just(response));

		// Test using store's default custom vector field
		vectorStore.similaritySearch("query");

		ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
		verify(this.idolApi).query(captor.capture());
		assertThat(captor.getValue().text()).contains("VECTOR{0.1,0.2:0}:store_vector_field");
		assertThat(captor.getValue().vectorField()).isEqualTo("store_vector_field");

		// Test overriding vector field in request
		IdolSearchRequest searchRequest = IdolSearchRequest.idolBuilder()
			.query("query")
			.vectorField("request_vector_field")
			.build();

		vectorStore.similaritySearch(searchRequest);

		verify(this.idolApi, org.mockito.Mockito.atLeastOnce()).query(captor.capture());
		assertThat(captor.getValue().text()).contains("VECTOR{0.1,0.2:0}:request_vector_field");
		assertThat(captor.getValue().vectorField()).isEqualTo("request_vector_field");
	}

	@Test
	void similaritySearchWithOptions() {
		IdolVectorStoreOptions options = new IdolVectorStoreOptions();
		options.setVectorField("options_vector_field");

		IdolVectorStore vectorStore = IdolVectorStore.builder(this.idolApi, this.embeddingModel)
			.options(options)
			.build();

		when(this.embeddingModel.embed("query")).thenReturn(new float[] { 0.1f, 0.2f });

		IdolApi.QueryResponse response = new IdolApi.QueryResponse(
				new IdolApi.AutnResponse(new IdolApi.ResponseData.builder().hits(List.of()).build()));

		when(this.idolApi.query(any())).thenReturn(Mono.just(response));

		vectorStore.similaritySearch("query");

		ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
		verify(this.idolApi).query(captor.capture());
		assertThat(captor.getValue().text()).contains("VECTOR{0.1,0.2:0}:options_vector_field");
	}

	@Test
	void similaritySearchWithSecurityInfo() {
		IdolVectorStore vectorStore = IdolVectorStore.builder(this.idolApi, this.embeddingModel).build();

		when(this.embeddingModel.embed("query")).thenReturn(new float[] { 0.1f, 0.2f });

		IdolApi.QueryResponse response = new IdolApi.QueryResponse(new IdolApi.AutnResponse(
				new IdolApi.ResponseData.builder().hits(List.of(new Hit("ref1", "1", 0.9, new Content(Map.of())))).build()));

		when(this.idolApi.query(any())).thenReturn(Mono.just(response));

		Filter.Expression expression = new Filter.Expression(Filter.ExpressionType.EQ,
				new Filter.Key(IdolVectorStore.METADATA_IDOL_USER), new Filter.Value("pdavie"));

		vectorStore.similaritySearch(SearchRequest.builder().query("query").filterExpression(expression).build());

		ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
		verify(this.idolApi).query(captor.capture());
		assertThat(captor.getValue().username()).isEqualTo("pdavie");
	}

	@Test
	void testGetSecurityInfo() {
		WebClient webClient = org.mockito.Mockito.mock(WebClient.class);
		WebClient communityWebClient = org.mockito.Mockito.mock(WebClient.class);
		when(communityWebClient.get()).thenReturn(this.requestHeadersUriSpec);
		when(this.requestHeadersUriSpec.uri(any(java.util.function.Function.class)))
			.thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);

		UserReadResponse response = new UserReadResponse(
				new UserReadAutnResponse(new UserReadResponseData("test+token")));
		when(this.responseSpec.bodyToMono(UserReadResponse.class)).thenReturn(Mono.just(response));

		IdolApi api = new IdolApi(webClient, webClient, communityWebClient, "testdb", "embedding");

		String token = api.getSecurityInfo("pdavie").block();

		assertThat(token).isEqualTo("test+token");
	}

	@Test
	void testQueryWithSecurityInfo() {
		WebClient webClient = org.mockito.Mockito.mock(WebClient.class);
		WebClient communityWebClient = org.mockito.Mockito.mock(WebClient.class);

		when(webClient.get()).thenReturn(this.requestHeadersUriSpec);
		when(this.requestHeadersUriSpec.uri(any(java.util.function.Function.class)))
			.thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);
		when(this.responseSpec.bodyToMono(QueryResponse.class)).thenReturn(Mono.empty());

		// Mock community client for security info
		WebClient.RequestHeadersUriSpec communityHeadersUriSpec = org.mockito.Mockito
			.mock(WebClient.RequestHeadersUriSpec.class);
		WebClient.RequestHeadersSpec communityHeadersSpec = org.mockito.Mockito
			.mock(WebClient.RequestHeadersSpec.class);
		WebClient.ResponseSpec communityResponseSpec = org.mockito.Mockito.mock(WebClient.ResponseSpec.class);

		when(communityWebClient.get()).thenReturn(communityHeadersUriSpec);
		when(communityHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(communityHeadersSpec);
		when(communityHeadersSpec.retrieve()).thenReturn(communityResponseSpec);
		when(communityResponseSpec.bodyToMono(UserReadResponse.class)).thenReturn(
				Mono.just(new UserReadResponse(new UserReadAutnResponse(new UserReadResponseData("test+token")))));

		IdolApi api = new IdolApi(webClient, webClient, communityWebClient, "testdb", "embedding");

		IdolApi.QueryRequest request = QueryRequest.builder()
			.text("text")
			.fieldText("fieldText")
			.maxResults(10)
			.username("pdavie")
			.vectorField("embedding")
			.build();
		api.query(request).block();

		ArgumentCaptor<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>> uriCaptor = ArgumentCaptor
			.forClass(java.util.function.Function.class);
		verify(this.requestHeadersUriSpec).uri(uriCaptor.capture());

		org.springframework.web.util.UriBuilder uriBuilder = org.springframework.web.util.UriComponentsBuilder
			.fromPath("/");
		java.net.URI uri = uriCaptor.getValue().apply(uriBuilder);

		// Spring's UriBuilder encodes the manually encoded string again.
		// test+token -> test%2Btoken -> test%252Btoken
		assertThat(uri.toString()).contains("SecurityInfo=test%252Btoken");
	}

	@Test
	void similaritySearchWithFilter() {
		IdolVectorStore vectorStore = IdolVectorStore.builder(this.idolApi, this.embeddingModel).build();

		when(this.embeddingModel.embed("query")).thenReturn(new float[] { 0.1f, 0.2f });
		when(this.idolApi.query(any())).thenReturn(Mono.empty());

		Filter.Expression expression = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("genre"),
				new Filter.Value("drama"));

		vectorStore.similaritySearch(SearchRequest.builder().query("query").filterExpression(expression).build());

		ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
		verify(this.idolApi).query(captor.capture());
		assertThat(captor.getValue().fieldText()).isEqualTo("MATCH{drama}:genre");
	}

	@Test
	void deleteDocumentsByFilter() {
		IdolVectorStore vectorStore = IdolVectorStore.builder(this.idolApi, this.embeddingModel).build();

		Filter.Expression expression = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("genre"),
				new Filter.Value("drama"));

		QueryResponse response = new QueryResponse(
				new AutnResponse(ResponseData.builder().state("state-token-123").build()));
		when(this.idolApi.query(any())).thenReturn(Mono.just(response));
		when(this.idolApi.deleteByState("state-token-123")).thenReturn(Mono.empty());

		vectorStore.delete(expression);

		ArgumentCaptor<QueryRequest> queryCaptor = ArgumentCaptor.forClass(QueryRequest.class);
		verify(this.idolApi).query(queryCaptor.capture());
		assertThat(queryCaptor.getValue().saveState()).isTrue();
		assertThat(queryCaptor.getValue().fieldText()).isEqualTo("MATCH{drama}:genre");

		verify(this.idolApi).deleteByState("state-token-123");
	}

	@Test
	void testDeleteByStateRequest() {
		WebClient webClient = org.mockito.Mockito.mock(WebClient.class);
		when(webClient.get()).thenReturn(this.requestHeadersUriSpec);
		when(this.requestHeadersUriSpec.uri(any(java.util.function.Function.class)))
			.thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);
		when(this.responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

		IdolApi api = new IdolApi(webClient, webClient, webClient, "testdb", "embedding");
		api.deleteByState("state-token-abc").block();

		ArgumentCaptor<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>> uriCaptor = ArgumentCaptor
			.forClass(java.util.function.Function.class);
		verify(this.requestHeadersUriSpec).uri(uriCaptor.capture());

		org.springframework.web.util.UriBuilder uriBuilder = org.springframework.web.util.UriComponentsBuilder
			.fromPath("/");
		java.net.URI uri = uriCaptor.getValue().apply(uriBuilder);

		assertThat(uri.toString()).contains("DREDELETEDOC");
		assertThat(uri.toString()).contains("StateId=state-token-abc");
	}

	@Test
	void testQueryWithSaveState() {
		WebClient webClient = org.mockito.Mockito.mock(WebClient.class);
		when(webClient.get()).thenReturn(this.requestHeadersUriSpec);
		when(this.requestHeadersUriSpec.uri(any(java.util.function.Function.class)))
			.thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);
		when(this.responseSpec.bodyToMono(QueryResponse.class)).thenReturn(Mono.empty());

		IdolApi api = new IdolApi(webClient, webClient, webClient, "testdb", "embedding");
		QueryRequest request = QueryRequest.builder().text("*").saveState(true).build();
		api.query(request).block();

		ArgumentCaptor<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>> uriCaptor = ArgumentCaptor
			.forClass(java.util.function.Function.class);
		verify(this.requestHeadersUriSpec).uri(uriCaptor.capture());

		org.springframework.web.util.UriBuilder uriBuilder = org.springframework.web.util.UriComponentsBuilder
			.fromPath("/");
		java.net.URI uri = uriCaptor.getValue().apply(uriBuilder);

		assertThat(uri.toString()).contains("SaveState=True");
	}

	@Test
	void deleteDocuments() {
		when(this.idolApi.delete(any())).thenReturn(Mono.empty());
		IdolVectorStore vectorStore = IdolVectorStore.builder(this.idolApi, this.embeddingModel).build();

		vectorStore.delete(List.of("1", "2"));

		verify(this.idolApi).delete(List.of("1", "2"));
	}

	@Test
	void testDeleteRequest() {
		WebClient webClient = org.mockito.Mockito.mock(WebClient.class);
		when(webClient.get()).thenReturn(this.requestHeadersUriSpec);
		when(this.requestHeadersUriSpec.uri(any(java.util.function.Function.class)))
			.thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);
		when(this.responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

		IdolApi api = new IdolApi(webClient, webClient, webClient, "testdb", "embedding");
		api.delete(List.of("ref 1", "ref+2")).block();

		ArgumentCaptor<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>> uriCaptor = ArgumentCaptor
			.forClass(java.util.function.Function.class);
		verify(this.requestHeadersUriSpec).uri(uriCaptor.capture());

		org.springframework.web.util.UriBuilder uriBuilder = org.springframework.web.util.UriComponentsBuilder
			.fromPath("/");
		java.net.URI uri = uriCaptor.getValue().apply(uriBuilder);

		assertThat(uri.toString()).contains("DREDELETEREF");
		assertThat(uri.toString()).contains("Docs=ref+1+ref%2B2");
		assertThat(uri.toString()).contains("DREDbName=testdb");
	}

	@Test
	void testSeparateClients() {
		WebClient aciClient = org.mockito.Mockito.mock(WebClient.class);
		WebClient indexClient = org.mockito.Mockito.mock(WebClient.class);
		WebClient communityClient = org.mockito.Mockito.mock(WebClient.class);

		when(aciClient.get()).thenReturn(this.requestHeadersUriSpec);
		when(this.requestHeadersUriSpec.uri(any(java.util.function.Function.class)))
			.thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);
		when(this.responseSpec.bodyToMono(QueryResponse.class)).thenReturn(Mono.empty());

		when(indexClient.post()).thenReturn(this.requestBodyUriSpec);
		when(this.requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(this.requestBodySpec);
		when(this.requestBodySpec.contentType(any())).thenReturn(this.requestBodySpec);
		when(this.requestBodySpec.contentLength(any(Long.class))).thenReturn(this.requestBodySpec);
		when(this.requestBodySpec.bodyValue(any())).thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);
		when(this.responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

		IdolApi api = new IdolApi(aciClient, indexClient, communityClient, "testdb", "embedding");

		// ACI operation should use aciClient
		api.query(org.springframework.ai.vectorstore.idol.api.QueryRequest.builder().text("test").maxResults(1).build())
			.block();
		verify(aciClient).get();
		org.mockito.Mockito.verifyNoInteractions(indexClient);

		// Index operation should use indexClient
		api.addDocument(new org.springframework.ai.vectorstore.idol.api.AddDocumentRequest(
				List.of(new IdolDocument("1", null, null, "text")), null))
			.block();
		verify(indexClient).post();
	}

	@Test
	void testTextEncoding() {
		WebClient webClient = org.mockito.Mockito.mock(WebClient.class);
		when(webClient.get()).thenReturn(this.requestHeadersUriSpec);
		when(this.requestHeadersUriSpec.uri(any(java.util.function.Function.class)))
			.thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);
		when(this.responseSpec.bodyToMono(QueryResponse.class)).thenReturn(Mono.empty());

		IdolApi api = new IdolApi(webClient, webClient, webClient, "testdb", "embedding");
		QueryRequest request = QueryRequest.builder().text("four,five,six").build();
		api.query(request).block();

		ArgumentCaptor<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>> uriCaptor = ArgumentCaptor
			.forClass(java.util.function.Function.class);
		verify(this.requestHeadersUriSpec).uri(uriCaptor.capture());

		org.springframework.web.util.UriBuilder uriBuilder = org.springframework.web.util.UriComponentsBuilder
			.fromPath("/");
		java.net.URI uri = uriCaptor.getValue().apply(uriBuilder);

		// four,five,six -> four,five,six (Standard transport-level encoding often leaves
		// commas)
		assertThat(uri.toString()).contains("Text=four,five,six");
	}

	@Test
	void testFieldTextEncoding() {
		WebClient webClient = org.mockito.Mockito.mock(WebClient.class);
		when(webClient.get()).thenReturn(this.requestHeadersUriSpec);
		when(this.requestHeadersUriSpec.uri(any(java.util.function.Function.class)))
			.thenReturn(this.requestHeadersSpec);
		when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);
		when(this.responseSpec.bodyToMono(QueryResponse.class)).thenReturn(Mono.empty());

		IdolApi api = new IdolApi(webClient, webClient, webClient, "testdb", "embedding");
		// FieldText values are encoded by IdolFilterExpressionConverter at application
		// level
		String encodedFieldText = "MATCH{four%2Cfive%2Csix}:MyField";
		QueryRequest request = QueryRequest.builder().text("*").fieldText(encodedFieldText).build();
		api.query(request).block();

		ArgumentCaptor<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>> uriCaptor = ArgumentCaptor
			.forClass(java.util.function.Function.class);
		verify(this.requestHeadersUriSpec).uri(uriCaptor.capture());

		org.springframework.web.util.UriBuilder uriBuilder = org.springframework.web.util.UriComponentsBuilder
			.fromPath("/");
		java.net.URI uri = uriCaptor.getValue().apply(uriBuilder);

		// MATCH{four%2Cfive%2Csix}:MyField -> MATCH%7Bfour%252Cfive%252Csix%7D%3AMyField
		// (Double encoding for the comma: , -> %2C -> %252C)
		assertThat(uri.toString()).contains("FieldText=MATCH%7Bfour%252Cfive%252Csix%7D:MyField");
	}

	@Test
	void similaritySearchWithEncoding() {
		IdolVectorStore vectorStore = IdolVectorStore.builder(this.idolApi, this.embeddingModel).build();

		when(this.embeddingModel.embed("query")).thenReturn(new float[] { 0.1f, 0.2f });
		when(this.idolApi.query(any())).thenReturn(Mono.empty());

		Filter.Expression expression = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("genre"),
				new Filter.Value("four,five,six"));

		vectorStore.similaritySearch(SearchRequest.builder().query("query").filterExpression(expression).build());

		ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
		verify(this.idolApi).query(captor.capture());
		// The converter should have encoded the value
		assertThat(captor.getValue().fieldText()).isEqualTo("MATCH{four%2Cfive%2Csix}:genre");
	}

}
