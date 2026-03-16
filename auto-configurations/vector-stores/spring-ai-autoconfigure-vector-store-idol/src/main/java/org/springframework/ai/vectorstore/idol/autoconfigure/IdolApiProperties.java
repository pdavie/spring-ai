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

package org.springframework.ai.vectorstore.idol.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for IDOL API client.
 *
 * @author pdavie
 */
@ConfigurationProperties(IdolApiProperties.CONFIG_PREFIX)
public class IdolApiProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.idol.client";

	private String url = "http://localhost:9000";

	private String indexUrl;

	private String communityUrl;

	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getIndexUrl() {
		return this.indexUrl;
	}

	public void setIndexUrl(String indexUrl) {
		this.indexUrl = indexUrl;
	}

	public String getCommunityUrl() {
		return this.communityUrl;
	}

	public void setCommunityUrl(String communityUrl) {
		this.communityUrl = communityUrl;
	}

}
