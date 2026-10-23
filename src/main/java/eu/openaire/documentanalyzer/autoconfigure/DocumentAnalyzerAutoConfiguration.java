/*
 * Copyright 2026 OpenAIRE AMKE
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
import eu.openaire.documentanalyzer.extract.service.HttpReaderSettings;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.util.StringUtils;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

@EnableRetry
@AutoConfiguration
@EnableConfigurationProperties(DocumentAnalyzerProperties.class)
public class DocumentAnalyzerAutoConfiguration {

//    @Bean
//    // override spring-ai static object mapper configuration to bypass Duration serialization issue
//    ApplicationRunner patchSpringAiObjectMapper() {
//        return args -> {
//            ObjectMapper staticMapper = ModelOptionsUtils.OBJECT_MAPPER; // <-- static mapper
//            staticMapper.registerModule(new JavaTimeModule());
//            staticMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//            staticMapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
//            staticMapper.findAndRegisterModules();
//        };
//    }

    /**
     * LLM-backed processor. When {@code system-prompt} is set, it is sent as the system message of every request.
     */
    @Bean
    @ConditionalOnMissingBean
    DocumentContentProcessor documentContentProcessor(ChatClient.Builder chatClientBuilder,
                                                      ObjectMapper mapper,
                                                      @Value("${system-prompt:}") String systemPrompt) {
        if (StringUtils.hasText(systemPrompt)) {
            chatClientBuilder.defaultSystem(systemPrompt);
        }
        return new LlmDocumentContentProcessor(chatClientBuilder.build(), mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    DocumentAnalyzerService documentAnalyzerService(
            DocumentContentProcessor documentContentProcessor,
            DocumentAnalyzerProperties properties,
            @Value("${scraping.request-delay-ms:500}") long requestDelayMs) throws NoSuchAlgorithmException, KeyManagementException {
        HttpReaderSettings settings = new HttpReaderSettings(
                requestDelayMs,
                properties.getHttp().getConnectTimeout(),
                properties.getHttp().getRequestTimeout(),
                properties.getHttp().isTrustAllCertificates(),
                properties.isAllowPrivateAddresses());
        return new DocumentAnalyzerService(documentContentProcessor, settings);
    }
}
