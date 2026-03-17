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
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * A specialized {@link SearchRequest} for IDOL vector search, extending the base request
 * with IDOL-specific parameters.
 *
 * @author pdavie
 */
public final class IdolSearchRequest extends SearchRequest {

	/**
	 * The print parameter.
	 */
	@NonNull
	private final String print;

	/**
	 * The print fields parameter.
	 */
	@Nullable
	private final String printFields;

	/**
	 * The vector field parameter.
	 */
	@Nullable
	private final String vectorField;

	private IdolSearchRequest(@NonNull final SearchRequest baseRequest, @NonNull final IdolBuilder builder) {
		super(baseRequest);
		this.print = builder.print;
		this.printFields = builder.printFields;
		this.vectorField = builder.vectorField;
	}

	/**
	 * Get the print parameter.
	 * @return the print parameter
	 */
	@NonNull
	public String getPrint() {
		return this.print;
	}

	/**
	 * Get the print fields parameter.
	 * @return the print fields parameter
	 */
	@Nullable
	public String getPrintFields() {
		return this.printFields;
	}

	/**
	 * Get the vector field parameter.
	 * @return the vector field parameter
	 */
	@Nullable
	public String getVectorField() {
		return this.vectorField;
	}

	/**
	 * Get a new IDOL search request builder.
	 * @return the builder
	 */
	@NonNull
	public static IdolBuilder idolBuilder() {
		return new IdolBuilder();
	}

	public static class IdolBuilder {

		/**
		 * The base search request builder.
		 */
		private final SearchRequest.Builder baseBuilder = SearchRequest.builder();

		/**
		 * The print parameter.
		 */
		private String print = "None";

		/**
		 * The print fields parameter.
		 */
		@Nullable
		private String printFields;

		/**
		 * The vector field parameter.
		 */
		@Nullable
		private String vectorField;

		/**
		 * Set the query string.
		 * @param query the query string
		 * @return the builder
		 */
		@NonNull
		public IdolBuilder query(@NonNull final String query) {
			this.baseBuilder.query(query);
			return this;
		}

		/**
		 * Set the top K results to return.
		 * @param topK the number of results
		 * @return the builder
		 */
		@NonNull
		public IdolBuilder topK(final int topK) {
			this.baseBuilder.topK(topK);
			return this;
		}

		/**
		 * Set the similarity threshold.
		 * @param threshold the threshold
		 * @return the builder
		 */
		@NonNull
		public IdolBuilder similarityThreshold(final double threshold) {
			this.baseBuilder.similarityThreshold(threshold);
			return this;
		}

		/**
		 * Set the similarity threshold to accept all results.
		 * @return the builder
		 */
		@NonNull
		public IdolBuilder similarityThresholdAll() {
			this.baseBuilder.similarityThresholdAll();
			return this;
		}

		/**
		 * Set the filter expression as text.
		 * @param textExpression the text expression
		 * @return the builder
		 */
		@NonNull
		public IdolBuilder filterExpression(@NonNull final String textExpression) {
			this.baseBuilder.filterExpression(textExpression);
			return this;
		}

		/**
		 * Set the filter expression.
		 * @param expression the expression
		 * @return the builder
		 */
		@NonNull
		public IdolBuilder filterExpression(@NonNull final Filter.Expression expression) {
			this.baseBuilder.filterExpression(expression);
			return this;
		}

		/**
		 * Set the print parameter.
		 * @param printParam the print parameter
		 * @return the builder
		 */
		@NonNull
		public IdolBuilder print(@NonNull final String printParam) {
			this.print = printParam;
			return this;
		}

		/**
		 * Set the print fields parameter.
		 * @param printFieldsParam the print fields parameter
		 * @return the builder
		 */
		@NonNull
		public IdolBuilder printFields(@Nullable final String printFieldsParam) {
			this.printFields = printFieldsParam;
			return this;
		}

		/**
		 * Set the vector field parameter.
		 * @param vectorFieldParam the vector field parameter
		 * @return the builder
		 */
		@NonNull
		public IdolBuilder vectorField(@Nullable final String vectorFieldParam) {
			this.vectorField = vectorFieldParam;
			return this;
		}

		/**
		 * Build the IDOL search request.
		 * @return the search request
		 */
		@NonNull
		public IdolSearchRequest build() {
			SearchRequest parentRequest = this.baseBuilder.build();
			return new IdolSearchRequest(parentRequest, this);
		}

	}

}
