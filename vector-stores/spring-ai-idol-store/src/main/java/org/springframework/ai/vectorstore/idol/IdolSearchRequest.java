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

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.lang.Nullable;

/**
 * A specialized {@link SearchRequest} for IDOL vector search, extending the base request
 * with IDOL-specific parameters.
 *
 * @author pdavie
 */
public final class IdolSearchRequest extends SearchRequest {

	private final String print;

	@Nullable
	private final String printFields;

	@Nullable
	private final String vectorField;

	private IdolSearchRequest(SearchRequest baseRequest, IdolBuilder builder) {
		super(baseRequest);
		this.print = builder.print;
		this.printFields = builder.printFields;
		this.vectorField = builder.vectorField;
	}

	public String getPrint() {
		return this.print;
	}

	@Nullable
	public String getPrintFields() {
		return this.printFields;
	}

	@Nullable
	public String getVectorField() {
		return this.vectorField;
	}

	public static IdolBuilder idolBuilder() {
		return new IdolBuilder();
	}

	public static class IdolBuilder {

		private final SearchRequest.Builder baseBuilder = SearchRequest.builder();

		private String print = "None";

		@Nullable
		private String printFields;

		@Nullable
		private String vectorField;

		public IdolBuilder query(String query) {
			this.baseBuilder.query(query);
			return this;
		}

		public IdolBuilder topK(int topK) {
			this.baseBuilder.topK(topK);
			return this;
		}

		public IdolBuilder similarityThreshold(double threshold) {
			this.baseBuilder.similarityThreshold(threshold);
			return this;
		}

		public IdolBuilder similarityThresholdAll() {
			this.baseBuilder.similarityThresholdAll();
			return this;
		}

		public IdolBuilder filterExpression(String textExpression) {
			this.baseBuilder.filterExpression(textExpression);
			return this;
		}

		public IdolBuilder filterExpression(Filter.Expression expression) {
			this.baseBuilder.filterExpression(expression);
			return this;
		}

		public IdolBuilder print(String print) {
			this.print = print;
			return this;
		}

		public IdolBuilder printFields(String printFields) {
			this.printFields = printFields;
			return this;
		}

		public IdolBuilder vectorField(String vectorField) {
			this.vectorField = vectorField;
			return this;
		}

		public IdolSearchRequest build() {
			SearchRequest parentRequest = this.baseBuilder.build();
			return new IdolSearchRequest(parentRequest, this);
		}

	}

}
