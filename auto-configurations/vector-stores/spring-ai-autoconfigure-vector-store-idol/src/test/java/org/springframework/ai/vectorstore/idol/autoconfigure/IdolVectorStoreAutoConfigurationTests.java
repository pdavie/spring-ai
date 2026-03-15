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

import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.idol.IdolApi;
import org.springframework.ai.vectorstore.idol.IdolVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link IdolVectorStoreAutoConfiguration}.
 *
 * @author pdavie
 */
class IdolVectorStoreAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(IdolVectorStoreAutoConfiguration.class))
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void defaultConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.idol.client.url=http://idol:9000").run(context -> {
			assertThat(context).hasSingleBean(IdolApi.class);
			assertThat(context).hasSingleBean(IdolVectorStore.class);

			IdolApi idolApi = context.getBean(IdolApi.class);
			assertThat(idolApi).isNotNull();
		});
	}

	@Test
	void customConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.vectorstore.idol.client.url=http://idol:9000",
					"spring.ai.vectorstore.idol.client.index-url=http://idol:9001",
					"spring.ai.vectorstore.idol.client.community-url=http://idol:9002",
					"spring.ai.vectorstore.idol.database=MyDatabase")
			.run(context -> {
				assertThat(context).hasSingleBean(IdolApi.class);
				assertThat(context).hasSingleBean(IdolVectorStore.class);
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class TestConfiguration {

		@Bean
		public EmbeddingModel embeddingModel() {
			return mock(EmbeddingModel.class);
		}

	}

}
