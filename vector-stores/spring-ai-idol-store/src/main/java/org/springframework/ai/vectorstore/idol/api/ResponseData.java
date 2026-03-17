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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Response data from an IDOL action.
 *
 * @param hits the list of search hits
 * @author pdavie
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResponseData(@Nullable @JsonProperty("hit") List<Hit> hits,
		@Nullable @JsonProperty("state") String state) {

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private List<Hit> hits = List.of();

		@Nullable
		private String state;

		public Builder hits(List<Hit> hits) {
			this.hits = hits;
			return this;
		}

		public Builder state(String state) {
			this.state = state;
			return this;
		}

		public ResponseData build() {
			return new ResponseData(this.hits, this.state);
		}

	}

}
