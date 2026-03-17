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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

import org.springframework.ai.vectorstore.idol.api.AddDocumentRequest;
import org.springframework.ai.vectorstore.idol.api.IdolDocument;
import org.springframework.ai.vectorstore.idol.api.QueryRequest;
import org.springframework.ai.vectorstore.idol.api.QueryResponse;
import org.springframework.ai.vectorstore.idol.api.UserReadResponse;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Client for OpenText IDOL ACI actions.
 *
 * @author pdavie
 */
public class IdolApi {

	private final WebClient webClient;

	private final WebClient indexWebClient;

	private final WebClient communityWebClient;

	private final String database;

	private final String vectorField;

	public IdolApi(String baseUrl, String database) {
		this(baseUrl, database, IdolVectorStore.DEFAULT_VECTOR_FIELD);
	}

	public IdolApi(String baseUrl, String database, String vectorField) {
		this(baseUrl, baseUrl, baseUrl, database, vectorField);
	}

	public IdolApi(String baseUrl, String indexBaseUrl, String communityBaseUrl, final String database) {
		this(baseUrl, indexBaseUrl, communityBaseUrl, database, IdolVectorStore.DEFAULT_VECTOR_FIELD);
	}

	public IdolApi(String baseUrl, String indexBaseUrl, String communityBaseUrl, String database, String vectorField) {
		this(WebClient.builder().baseUrl(baseUrl).build(), WebClient.builder().baseUrl(indexBaseUrl).build(),
				WebClient.builder().baseUrl(communityBaseUrl).build(), database, vectorField);
	}

	public IdolApi(WebClient webClient, String database) {
		this(webClient, webClient, webClient, database, IdolVectorStore.DEFAULT_VECTOR_FIELD);
	}

	public IdolApi(WebClient webClient, WebClient indexWebClient, WebClient communityWebClient, final String database) {
		this(webClient, indexWebClient, communityWebClient, database, IdolVectorStore.DEFAULT_VECTOR_FIELD);
	}

	public IdolApi(WebClient webClient, WebClient indexWebClient, WebClient communityWebClient, String database,
			String vectorField) {
		Assert.notNull(webClient, "WebClient must not be null");
		Assert.notNull(indexWebClient, "Index WebClient must not be null");
		Assert.notNull(communityWebClient, "Community WebClient must not be null");
		Assert.hasText(database, "Database must not be empty");
		Assert.hasText(vectorField, "Vector field must not be empty");
		this.webClient = webClient;
		this.indexWebClient = indexWebClient;
		this.communityWebClient = communityWebClient;
		this.database = database;
		this.vectorField = vectorField;
	}

	/**
	 * Perform a similarity query against IDOL.
	 * @param request the query request
	 * @return the query response
	 */
	public Mono<QueryResponse> query(QueryRequest request) {
		Mono<String> securityInfoMono = Mono.justOrEmpty(request.username())
			.flatMap(this::getSecurityInfo)
			.defaultIfEmpty("");

		return securityInfoMono.flatMap(securityInfo -> {
			WebClient client = this.webClient;
			return client.get().uri(ignored -> {
				UriComponentsBuilder componentsBuilder = UriComponentsBuilder.fromPath("/")
					.queryParam("action", "Query")
					.queryParam("Text", request.text())
					.queryParam("FieldText", request.fieldText())
					.queryParam("MaxResults", request.maxResults())
					.queryParam("TotalResults", "True")
					.queryParam("ResponseFormat", "SimpleJson");

				if (securityInfo != null && !securityInfo.isEmpty()) {
					try {
						String encoded = URLEncoder.encode(securityInfo, StandardCharsets.UTF_8.name())
							.replace("+", "%2B");
						componentsBuilder.queryParam("SecurityInfo", encoded);
					}
					catch (Exception e) {
						throw new RuntimeException(e);
					}
				}

				if (request.print() != null) {
					componentsBuilder.queryParam("Print", request.print());
				}

				if (request.printFields() != null) {
					componentsBuilder.queryParam("PrintFields", request.printFields());
				}

				if (request.saveState() != null && request.saveState()) {
					componentsBuilder.queryParam("SaveState", "True");
				}

				return componentsBuilder.build().toUri();
			}).retrieve().bodyToMono(QueryResponse.class);
		});
	}

	/**
	 * Get security info for a user.
	 * @param username the username
	 * @return the security info string
	 */
	public Mono<String> getSecurityInfo(String username) {
		return this.communityWebClient.get()
			.uri(ignore -> UriComponentsBuilder.fromPath("/")
				.queryParam("action", "UserRead")
				.queryParam("SecurityInfo", "true")
				.queryParam("DeferLogin", "true")
				.queryParam("ResponseFormat", "SimpleJson")
				.queryParam("UserName", username)
				.build()
				.toUri())
			.retrieve()
			.bodyToMono(UserReadResponse.class)
			.map(response -> response.autnresponse().responsedata().securityinfo());
	}

	/**
	 * Add documents to IDOL.
	 * @param request the add document request
	 * @return a mono that completes when done
	 */
	public Mono<Void> addDocument(AddDocumentRequest request) {
		String idxContent = formatIdx(request.documents(), this.database);
		byte[] body = (idxContent + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);

		return this.indexWebClient.post()
			.uri(ignore -> UriComponentsBuilder.fromPath("/DREADDDATA")
				.queryParam("LanguageType", "EnglishUTF8")
				.build()
				.toUri())
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.contentLength(body.length)
			.bodyValue(body)
			.retrieve()
			.bodyToMono(Void.class);
	}

	/**
	 * Delete documents from IDOL by reference.
	 * @param idList the list of references to delete
	 * @return a mono that completes when done
	 */
	public Mono<Void> delete(List<String> idList) {
		String docs = idList.stream().map(id -> {
			try {
				return URLEncoder.encode(id, StandardCharsets.UTF_8.name());
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).collect(Collectors.joining("+"));

		return this.indexWebClient.get()
			.uri(ignore -> URI.create(UriComponentsBuilder.fromPath("/DREDELETEREF")
				.queryParam("DREDbName", this.database)
				.build()
				.toUriString() + "&Docs=" + docs))
			.retrieve()
			.bodyToMono(Void.class);
	}

	/**
	 * Delete documents from IDOL by state ID.
	 * @param stateId the state ID identifying the documents to delete
	 * @return a mono that completes when done
	 */
	public Mono<Void> deleteByState(String stateId) {
		Assert.hasText(stateId, "State ID must not be empty");
		return this.indexWebClient.get()
			.uri(ignore -> UriComponentsBuilder.fromPath("/DREDELETEDOC")
				.queryParam("StateId", stateId)
				.build()
				.toUri())
			.retrieve()
			.bodyToMono(Void.class);
	}

	private String formatIdx(List<IdolDocument> documents, String docsDatabase) {
		StringBuilder sb = new StringBuilder();
		for (IdolDocument doc : documents) {
			sb.append("#DREREFERENCE ").append(doc.reference()).append("\n");

			if (doc.metadata() != null) {
				for (Map.Entry<String, Object> entry : doc.metadata().entrySet()) {
					if (entry.getValue() != null) {
						sb.append("#DREFIELD ")
							.append(entry.getKey())
							.append("=\"")
							.append(entry.getValue())
							.append("\"\n");
					}
				}
			}

			if (doc.embedding() != null) {
				sb.append("#DREFIELD ")
					.append(this.vectorField)
					.append("=\"")
					.append(vectorToString(doc.embedding()))
					.append("\"\n");
			}

			String title = doc.reference();
			if (doc.metadata() != null) {
				for (Map.Entry<String, Object> entry : doc.metadata().entrySet()) {
					if (entry.getKey().equalsIgnoreCase("title")) {
						title = String.valueOf(entry.getValue());
						break;
					}
				}
			}

			if (doc.content() != null && !doc.content().isEmpty()) {
				sb.append("#DRETITLE\n").append(title).append("\n");
				sb.append("#DRECONTENT\n").append(doc.content()).append("\n");
			}

			sb.append("#DREDBNAME ").append(docsDatabase).append("\n");
			sb.append("#DREENDDOC\n");
		}
		sb.append("#DREENDDATAREFERENCE\r\n\r\n");
		return sb.toString();
	}

	String vectorToString(float[] vector) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < vector.length; i++) {
			sb.append(vector[i]);
			if (i < vector.length - 1) {
				sb.append(",");
			}
		}
		return sb.toString();
	}

	private String idolEncode(String value) {
		if (value == null) {
			return null;
		}
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
