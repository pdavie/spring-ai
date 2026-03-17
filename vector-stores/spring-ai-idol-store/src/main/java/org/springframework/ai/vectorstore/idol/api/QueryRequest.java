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

package org.springframework.ai.vectorstore.idol.api;

import org.springframework.lang.Nullable;

/**
 * Request for performing a query in IDOL.
 *
 * @param text the query text
 * @param fieldText the field text (filter)
 * @param maxResults the maximum number of results to return
 * @param username the optional username for the operation
 * @param print the print mode
 * @param printFields the fields to print
 * @param vectorField the name of the vector field
 * @author pdavie
 */
public record QueryRequest(String text, String fieldText, int maxResults, String username, String print,
		String printFields, String vectorField, @Nullable Boolean saveState) {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String text;

		private String fieldText;

		private int maxResults = 1000;

		private String username;

		private String print = "Fields";

		private String printFields;

		private String vectorField;

		private Boolean saveState;

		public Builder text(String text) {
			this.text = text;
			return this;
		}

		public Builder fieldText(String fieldText) {
			this.fieldText = fieldText;
			return this;
		}

		public Builder maxResults(int maxResults) {
			this.maxResults = maxResults;
			return this;
		}

		public Builder username(String username) {
			this.username = username;
			return this;
		}

		public Builder print(String print) {
			this.print = print;
			return this;
		}

		public Builder printFields(String printFields) {
			this.printFields = printFields;
			return this;
		}

		public Builder vectorField(String vectorField) {
			this.vectorField = vectorField;
			return this;
		}

		public Builder saveState(Boolean saveState) {
			this.saveState = saveState;
			return this;
		}

		public QueryRequest build() {
			return new QueryRequest(this.text, this.fieldText, this.maxResults, this.username, this.print,
					this.printFields, this.vectorField, this.saveState);
		}

	}

}
