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

package eu.openaire.documentanalyzer.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.openaire.documentanalyzer.analyze.service.DocumentAnalyzerService;
import eu.openaire.documentanalyzer.enrich.service.DocumentContentProcessor;
import eu.openaire.documentanalyzer.enrich.service.LlmDocumentContentProcessor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

@AutoConfiguration
public class DocumentAnalyzerAutoConfiguration {

    @Bean
    // override spring-ai static object mapper configuration to bypass Duration serialization issue
    ApplicationRunner patchSpringAiObjectMapper() {
        return args -> {
            ObjectMapper staticMapper = ModelOptionsUtils.OBJECT_MAPPER; // <-- static mapper
            staticMapper.registerModule(new JavaTimeModule());
            staticMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            staticMapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
            staticMapper.findAndRegisterModules();
        };
    }

    @ConditionalOnClass(ChatClient.class)
    @ConditionalOnProperty("system-prompt")
    @Bean
    ChatClient chatClient(ChatClient.Builder builder,
                          @Value("${system-prompt}") String systemPrompt) {
        return builder.defaultSystem(systemPrompt)
                .build();
    }

    @ConditionalOnClass(DocumentContentProcessor.class)
    @Bean
    DocumentContentProcessor documentContentProcessor(ChatModel chatModel, ObjectMapper mapper) {
        return new LlmDocumentContentProcessor(chatModel, mapper);
    }

    @ConditionalOnClass(DocumentAnalyzerService.class)
    @Bean
    DocumentAnalyzerService documentAnalyzerService(DocumentContentProcessor documentContentProcessor) throws NoSuchAlgorithmException, KeyManagementException {
        return new DocumentAnalyzerService(documentContentProcessor);
    }
}
