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

import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for IDOL Vector Store.
 *
 * @author pdavie
 */
@ConfigurationProperties(IdolVectorStoreProperties.CONFIG_PREFIX)
public class IdolVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.idol";

	private String database = "Default";

	private String vectorField = "embedding";

	private int maxDocumentBatchSize = 1000;

	private String aciHost = "localhost";

	private int aciPort = 9000;

	private String indexHost = "localhost";

	private int indexPort = 9001;

	private String communityHost = "localhost";

	private int communityPort = 9030;

	public String getDatabase() {
		return this.database;
	}

	public void setDatabase(String database) {
		this.database = database;
	}

	public String getVectorField() {
		return this.vectorField;
	}

	public void setVectorField(String vectorField) {
		this.vectorField = vectorField;
	}

	public int getMaxDocumentBatchSize() {
		return this.maxDocumentBatchSize;
	}

	public void setMaxDocumentBatchSize(int maxDocumentBatchSize) {
		this.maxDocumentBatchSize = maxDocumentBatchSize;
	}

	public String getAciHost() {
		return this.aciHost;
	}

	public void setAciHost(String aciHost) {
		this.aciHost = aciHost;
	}

	public int getAciPort() {
		return this.aciPort;
	}

	public void setAciPort(int aciPort) {
		this.aciPort = aciPort;
	}

	public String getIndexHost() {
		return this.indexHost;
	}

	public void setIndexHost(String indexHost) {
		this.indexHost = indexHost;
	}

	public int getIndexPort() {
		return this.indexPort;
	}

	public void setIndexPort(int indexPort) {
		this.indexPort = indexPort;
	}

	public String getCommunityHost() {
		return this.communityHost;
	}

	public void setCommunityHost(String communityHost) {
		this.communityHost = communityHost;
	}

	public int getCommunityPort() {
		return this.communityPort;
	}

	public void setCommunityPort(int communityPort) {
		this.communityPort = communityPort;
	}

}
