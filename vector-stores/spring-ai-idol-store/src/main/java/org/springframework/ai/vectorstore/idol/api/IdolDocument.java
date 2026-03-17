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

import java.util.Map;

/**
 * Represents a document in IDOL.
 *
 * @param reference the document reference
 * @param embedding the document vector
 * @param metadata the document metadata
 * @param content the document content
 * @author pdavie
 */
public record IdolDocument(String reference, float[] embedding, Map<String, Object> metadata, String content) {
}
