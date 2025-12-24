/*
 * Copyright 2025 OpenAIRE AMKE
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

package eu.openaire.documentanalyzer.enrich.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import eu.openaire.documentanalyzer.common.model.Content;

public interface DocumentContentProcessor {

    default <T extends Content> JsonNode enrich(JsonNode template, T content, EnrichMethod method) throws JsonProcessingException {
        return method.perform(template, content);
    }

    <T extends Content> ArrayNode extractInformation(T content);

    <T extends Content> JsonNode generate(JsonNode template, T content);

    ArrayNode translate(ArrayNode content, String language);

    ArrayNode translate(ArrayNode content);
}
