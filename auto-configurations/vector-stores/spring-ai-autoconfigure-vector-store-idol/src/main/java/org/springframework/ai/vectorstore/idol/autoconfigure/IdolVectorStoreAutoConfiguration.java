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

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.idol.IdolApi;
import org.springframework.ai.vectorstore.idol.IdolVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * {@link AutoConfiguration Auto-configuration} for IDOL Vector Store.
 *
 * @author pdavie
 */
@AutoConfiguration
@ConditionalOnClass({ EmbeddingModel.class, IdolVectorStore.class })
@EnableConfigurationProperties({ IdolApiProperties.class, IdolVectorStoreProperties.class })
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.IDOL,
		matchIfMissing = true)
public class IdolVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public IdolApi idolApi(IdolApiProperties apiProperties, IdolVectorStoreProperties storeProperties) {
		String baseUrl = apiProperties.getUrl();
		String indexBaseUrl = StringUtils.hasText(apiProperties.getIndexUrl()) ? apiProperties.getIndexUrl() : baseUrl;
		String communityBaseUrl = StringUtils.hasText(apiProperties.getCommunityUrl()) ? apiProperties.getCommunityUrl()
				: baseUrl;

		return new IdolApi(baseUrl, indexBaseUrl, communityBaseUrl, storeProperties.getDatabase(),
				storeProperties.getVectorField());
	}

	@Bean
	@ConditionalOnMissingBean(BatchingStrategy.class)
	BatchingStrategy idolBatchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public IdolVectorStore vectorStore(EmbeddingModel embeddingModel, IdolApi idolApi,
			IdolVectorStoreProperties storeProperties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy idolBatchingStrategy) {
		return IdolVectorStore.builder(idolApi, embeddingModel)
			.vectorField(storeProperties.getVectorField())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
			.batchingStrategy(idolBatchingStrategy)
			.build();
	}

}
