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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import reactor.core.publisher.Mono;

import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

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

	public IdolApi(String baseUrl, String indexBaseUrl, String communityBaseUrl, String database) {
		this(baseUrl, indexBaseUrl, communityBaseUrl, database, IdolVectorStore.DEFAULT_VECTOR_FIELD);
	}

	public IdolApi(String baseUrl, String indexBaseUrl, String communityBaseUrl, String database, String vectorField) {
		this(WebClient.builder().baseUrl(baseUrl).build(), WebClient.builder().baseUrl(indexBaseUrl).build(),
				WebClient.builder().baseUrl(communityBaseUrl).build(), database, vectorField);
	}

	public IdolApi(WebClient webClient, String database) {
		this(webClient, webClient, webClient, database, IdolVectorStore.DEFAULT_VECTOR_FIELD);
	}

	public IdolApi(WebClient webClient, WebClient indexWebClient, WebClient communityWebClient, String database) {
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

	public Mono<QueryResponse> query(QueryRequest request) {
		Mono<String> securityInfoMono = Mono.justOrEmpty(request.username())
			.flatMap(this::getSecurityInfo)
			.defaultIfEmpty("");

		return securityInfoMono.flatMap(securityInfo -> this.webClient.get().uri(uriBuilder -> {
			uriBuilder.path("/")
				.queryParam("action", "Query")
				.queryParam("Text", request.text())
				.queryParam("FieldText", request.fieldText())
				.queryParam("MaxResults", request.maxResults())
				.queryParam("TotalResults", "True")
				.queryParam("ResponseFormat", "JSON");

			if (securityInfo != null && !securityInfo.isEmpty()) {
				String encoded = URLEncoder.encode(securityInfo, StandardCharsets.UTF_8).replace("+", "%2B");
				uriBuilder.queryParam("SecurityInfo", encoded);
			}

			if (request.print() != null) {
				uriBuilder.queryParam("Print", request.print());
			}

			if (request.printFields() != null) {
				uriBuilder.queryParam("PrintFields", request.printFields());
			}

			return uriBuilder.build();
		}).retrieve().bodyToMono(QueryResponse.class));
	}

	public Mono<String> getSecurityInfo(String username) {
		return this.communityWebClient.get().uri(uriBuilder -> {
			uriBuilder.path("/")
				.queryParam("action", "UserRead")
				.queryParam("SecurityInfo", "true")
				.queryParam("DeferLogin", "true")
				.queryParam("ResponseFormat", "SimpleJson")
				.queryParam("UserName", username);
			return uriBuilder.build();
		})
			.retrieve()
			.bodyToMono(UserReadResponse.class)
			.map(response -> response.autnresponse().responsedata().securityinfo());
	}

	public Mono<Void> addDocument(AddDocumentRequest request) {
		String idxContent = formatIdx(request.documents(), this.database);
		byte[] body = (idxContent + "\r\n\r\n").getBytes();

		return this.indexWebClient.post().uri(uriBuilder -> {
			uriBuilder.path("/DREADDDATA");
			return uriBuilder.build();
		})
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.contentLength(body.length)
			.bodyValue(body)
			.retrieve()
			.bodyToMono(Void.class);
	}

	public Mono<Void> delete(List<String> idList) {
		String docs = idList.stream()
			.map(id -> URLEncoder.encode(id, StandardCharsets.UTF_8))
			.collect(Collectors.joining("+"));

		return this.indexWebClient.get().uri(uriBuilder -> {
			uriBuilder.path("/DREDELETEREF").queryParam("DREDbName", this.database);
			return URI.create(uriBuilder.build().toString() + "&Docs=" + docs);
		}).retrieve().bodyToMono(Void.class);
	}

	private String formatIdx(List<IdolDocument> documents, String database) {
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

			if (doc.vector() != null) {
				sb.append("#DREFIELD ")
					.append(this.vectorField)
					.append("=\"")
					.append(vectorToString(doc.vector()))
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

			sb.append("#DREDBNAME ").append(database).append("\n");
			sb.append("#DREENDDOC\n");
		}
		sb.append("#DREENDDATAREFERENCE");
		return sb.toString();
	}

	private String vectorToString(float[] vector) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < vector.length; i++) {
			sb.append(vector[i]);
			if (i < vector.length - 1) {
				sb.append(",");
			}
		}
		return sb.toString();
	}

	public record QueryRequest(String text, String fieldText, Integer maxResults, String username, String print,
			String printFields, String vectorField) {

		public QueryRequest(String text, String fieldText, Integer maxResults) {
			this(text, fieldText, maxResults, null, null, null, null);
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record QueryResponse(@JsonProperty("autnresponse") AutnResponse autnResponse) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AutnResponse(@JsonProperty("responsedata") ResponseData responseData) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ResponseData(@JsonProperty("hit") List<Hit> hits) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Hit(@JsonProperty("id") String id, @JsonProperty("reference") String reference,
			@JsonProperty("weight") Double weight, @JsonProperty("content") Content content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Content(@JsonProperty("DOCUMENT") List<Map<String, List<String>>> documents) {
	}

	public record AddDocumentRequest(List<IdolDocument> documents, String username) {

		public AddDocumentRequest(List<IdolDocument> documents) {
			this(documents, null);
		}

	}

	public record IdolDocument(String reference, float[] vector, Map<String, Object> metadata, String content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record UserReadResponse(@JsonProperty("autnresponse") UserReadAutnResponse autnresponse) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record UserReadAutnResponse(@JsonProperty("responsedata") UserReadResponseData responsedata) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record UserReadResponseData(@JsonProperty("securityinfo") String securityinfo) {
	}

}
