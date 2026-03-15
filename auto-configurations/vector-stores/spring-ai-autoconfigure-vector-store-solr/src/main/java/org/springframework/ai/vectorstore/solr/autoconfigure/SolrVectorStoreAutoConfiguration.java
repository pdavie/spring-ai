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

package org.springframework.ai.vectorstore.solr.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import org.apache.solr.client.solrj.SolrClient;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.solr.SolrVectorStore;
import org.springframework.ai.vectorstore.solr.SolrVectorStoreOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Solr Vector Store.
 *
 * @author Wei Jiang
 * @author Peter Davie
 */
@AutoConfiguration
@ConditionalOnClass({ SolrVectorStore.class, EmbeddingModel.class, SolrClient.class })
@EnableConfigurationProperties(SolrVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.SOLR,
		matchIfMissing = true)
public class SolrVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(BatchingStrategy.class)
	public BatchingStrategy solrBatchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public SolrVectorStore vectorStore(EmbeddingModel embeddingModel, SolrVectorStoreProperties properties,
			SolrClient solrClient, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy solrBatchingStrategy) {

		SolrVectorStoreOptions options = new SolrVectorStoreOptions();
		options.setIndexName(properties.getIndexName());
		options.setDimensions(properties.getDimensions());
		options.setSimilarity(properties.getSimilarity());

		return SolrVectorStore.builder(solrClient, embeddingModel)
			.options(options)
			.initializeSchema(properties.isInitializeSchema())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
			.batchingStrategy(solrBatchingStrategy)
			.build();
	}

}
