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

/**
 * Configuration options for the IDOL vector store.
 *
 * @author pdavie
 * @since 1.0.0
 */
public class IdolVectorStoreOptions {

	/**
	 * Default maximum number of documents to send in a single batch.
	 */
	private static final int DEFAULT_MAX_DOCUMENT_BATCH_SIZE = 1000;

	/**
	 * Default port for the IDOL ACI server.
	 */
	private static final int DEFAULT_ACI_PORT = 9000;

	/**
	 * Default port for the IDOL Index server.
	 */
	private static final int DEFAULT_INDEX_PORT = 9001;

	/**
	 * Default port for the IDOL Community server.
	 */
	private static final int DEFAULT_COMMUNITY_PORT = 9030;

	/**
	 * The name of the IDOL database to store the documents.
	 */
	private String database = "Default";

	/**
	 * The name of the field to store the vectors.
	 */
	private String vectorField = IdolVectorStore.DEFAULT_VECTOR_FIELD;

	/**
	 * Maximum number of documents to send in a single batch.
	 */
	private int maxDocumentBatchSize = DEFAULT_MAX_DOCUMENT_BATCH_SIZE;

	/**
	 * The host of the IDOL ACI server (e.g. for Query).
	 */
	private String aciHost = "localhost";

	/**
	 * The port of the IDOL ACI server.
	 */
	private int aciPort = DEFAULT_ACI_PORT;

	/**
	 * The host of the IDOL Index server (e.g. for DREADDDATA).
	 */
	private String indexHost = "localhost";

	/**
	 * The port of the IDOL Index server.
	 */
	private int indexPort = DEFAULT_INDEX_PORT;

	/**
	 * The host of the IDOL Community server (e.g. for DLS).
	 */
	private String communityHost = "localhost";

	/**
	 * The port of the IDOL Community server.
	 */
	private int communityPort = DEFAULT_COMMUNITY_PORT;

	/**
	 * Get the name of the IDOL database.
	 * @return the database name
	 */
	public String getDatabase() {
		return this.database;
	}

	/**
	 * Set the name of the IDOL database.
	 * @param databaseName name of the database
	 */
	public void setDatabase(final String databaseName) {
		this.database = databaseName;
	}

	/**
	 * Get the name of the field to store the vectors.
	 * @return the vector field name
	 */
	public String getVectorField() {
		return this.vectorField;
	}

	/**
	 * Set the name of the field to store the vectors.
	 * @param vectorFieldName name of the vector field
	 */
	public void setVectorField(final String vectorFieldName) {
		this.vectorField = vectorFieldName;
	}

	/**
	 * Get the maximum number of documents to send in a single batch.
	 * @return the maximum document batch size
	 */
	public int getMaxDocumentBatchSize() {
		return this.maxDocumentBatchSize;
	}

	/**
	 * Set the maximum number of documents to send in a single batch.
	 * @param batchSize the maximum document batch size
	 */
	public void setMaxDocumentBatchSize(final int batchSize) {
		this.maxDocumentBatchSize = batchSize;
	}

	/**
	 * Get the host of the IDOL ACI server.
	 * @return the ACI host
	 */
	public String getAciHost() {
		return this.aciHost;
	}

	/**
	 * Set the host of the IDOL ACI server.
	 * @param host the ACI host
	 */
	public void setAciHost(final String host) {
		this.aciHost = host;
	}

	/**
	 * Get the port of the IDOL ACI server.
	 * @return the ACI port
	 */
	public int getAciPort() {
		return this.aciPort;
	}

	/**
	 * Set the port of the IDOL ACI server.
	 * @param port the ACI port
	 */
	public void setAciPort(final int port) {
		this.aciPort = port;
	}

	/**
	 * Get the host of the IDOL Index server.
	 * @return the Index host
	 */
	public String getIndexHost() {
		return this.indexHost;
	}

	/**
	 * Set the host of the IDOL Index server.
	 * @param host the Index host
	 */
	public void setIndexHost(final String host) {
		this.indexHost = host;
	}

	/**
	 * Get the port of the IDOL Index server.
	 * @return the Index port
	 */
	public int getIndexPort() {
		return this.indexPort;
	}

	/**
	 * Set the port of the IDOL Index server.
	 * @param port the Index port
	 */
	public void setIndexPort(final int port) {
		this.indexPort = port;
	}

	/**
	 * Get the host of the IDOL Community server.
	 * @return the Community host
	 */
	public String getCommunityHost() {
		return this.communityHost;
	}

	/**
	 * Set the host of the IDOL Community server.
	 * @param host the Community host
	 */
	public void setCommunityHost(final String host) {
		this.communityHost = host;
	}

	/**
	 * Get the port of the IDOL Community server.
	 * @return the Community port
	 */
	public int getCommunityPort() {
		return this.communityPort;
	}

	/**
	 * Set the port of the IDOL Community server.
	 * @param port the Community port
	 */
	public void setCommunityPort(final int port) {
		this.communityPort = port;
	}

}
