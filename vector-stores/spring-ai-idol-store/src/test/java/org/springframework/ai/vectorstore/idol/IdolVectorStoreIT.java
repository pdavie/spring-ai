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

package org.springframework.ai.vectorstore.idol;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link IdolVectorStore} using WireMock.
 *
 * @author pdavie
 */
@WireMockTest
class IdolVectorStoreIT {

	private IdolVectorStore vectorStore;

	private EmbeddingModel embeddingModel;

	private String baseUrl;

	@BeforeEach
	void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
		this.baseUrl = wmRuntimeInfo.getHttpBaseUrl();
		this.embeddingModel = mock(EmbeddingModel.class);
		when(this.embeddingModel.embed(any(java.util.List.class), any(), any())).thenAnswer(invocation -> {
			java.util.List<Document> docs = invocation.getArgument(0);
			return java.util.Collections.nCopies(docs.size(), new float[] { 1.0f, 2.0f });
		});

		IdolApi idolApi = new IdolApi(this.baseUrl, this.baseUrl, this.baseUrl, "Science", "vector");
		this.vectorStore = IdolVectorStore.builder(idolApi, this.embeddingModel).build();
	}

	@Test
	void testAddDocument() {
		WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/DREADDDATA"))
			.withQueryParam("LanguageType", WireMock.equalTo("EnglishUTF8"))
			.willReturn(WireMock.aResponse().withStatus(200)));

		Document doc = new Document("392348A0", "Using a technique called test tube evolution...",
				Map.of("authorname1", "Brown", "authorname2", "Edgar", "title", "Dr."));

		this.vectorStore.add(Collections.singletonList(doc));

		WireMock.verify(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/DREADDDATA"))
			.withQueryParam("LanguageType", WireMock.equalTo("EnglishUTF8"))
			.withHeader("Content-Type", WireMock.equalTo("application/octet-stream"))
			.withRequestBody(WireMock.containing("#DREREFERENCE 392348A0\n"))
			.withRequestBody(WireMock.containing("#DREFIELD authorname1=\"Brown\"\n"))
			.withRequestBody(WireMock.containing("#DREFIELD authorname2=\"Edgar\"\n"))
			.withRequestBody(WireMock.containing("#DREFIELD title=\"Dr.\"\n"))
			.withRequestBody(WireMock.containing("#DREFIELD vector=\"1.0,2.0\"\n"))
			.withRequestBody(WireMock.containing("#DRETITLE\nDr.\n"))
			.withRequestBody(WireMock.containing("#DRECONTENT\nUsing a technique called test tube evolution...\n"))
			.withRequestBody(WireMock.containing("#DREDBNAME Science\n"))
			.withRequestBody(WireMock.containing("#DREENDDOC\n"))
			.withRequestBody(WireMock.containing("#DREENDDATAREFERENCE\r\n\r\n")));
	}

	@Test
	void testBatchAddDocuments() {
		WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/DREADDDATA"))
			.withQueryParam("LanguageType", WireMock.equalTo("EnglishUTF8"))
			.willReturn(WireMock.aResponse().withStatus(200)));

		Document doc1 = new Document("ref1", "content1", Map.of("title", "title1"));
		Document doc2 = new Document("ref2", "content2", Map.of("title", "title2"));

		this.vectorStore.add(Arrays.asList(doc1, doc2));

		WireMock.verify(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/DREADDDATA"))
			.withQueryParam("LanguageType", WireMock.equalTo("EnglishUTF8"))
			.withRequestBody(WireMock.containing("#DREREFERENCE ref1\n"))
			.withRequestBody(WireMock.containing("#DREFIELD title=\"title1\"\n"))
			.withRequestBody(WireMock.containing("#DREFIELD vector=\"1.0,2.0\"\n"))
			.withRequestBody(WireMock.containing("#DRETITLE\ntitle1\n"))
			.withRequestBody(WireMock.containing("#DRECONTENT\ncontent1\n"))
			.withRequestBody(WireMock.containing("#DREDBNAME Science\n"))
			.withRequestBody(WireMock.containing("#DREENDDOC\n"))
			.withRequestBody(WireMock.containing("#DREREFERENCE ref2\n"))
			.withRequestBody(WireMock.containing("#DREFIELD title=\"title2\"\n"))
			.withRequestBody(WireMock.containing("#DRETITLE\ntitle2\n"))
			.withRequestBody(WireMock.containing("#DRECONTENT\ncontent2\n"))
			.withRequestBody(WireMock.containing("#DREENDDATAREFERENCE\r\n\r\n")));
	}

}
