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
import eu.openaire.documentanalyzer.analyze.service.DocumentAnalyzerService;
import eu.openaire.documentanalyzer.enrich.service.DocumentContentProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentAnalyzerAutoConfigurationTest {

    private final ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);

    private ApplicationContextRunner runner() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(mock(ChatClient.class));
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DocumentAnalyzerAutoConfiguration.class))
                .withBean(ChatClient.Builder.class, () -> chatClientBuilder)
                .withBean(ObjectMapper.class, ObjectMapper::new);
    }

    @Test
    void appliesSystemPromptWhenConfigured() {
        runner().withPropertyValues("system-prompt=You extract documents.")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DocumentContentProcessor.class);
                    verify(chatClientBuilder).defaultSystem("You extract documents.");
                });
    }

    @Test
    void omitsSystemPromptWhenNotConfigured() {
        runner().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(DocumentAnalyzerService.class);
            verify(chatClientBuilder, never()).defaultSystem(anyString());
        });
    }

    @Test
    void backsOffWhenApplicationDefinesProcessor() {
        DocumentContentProcessor custom = mock(DocumentContentProcessor.class);
        runner().withBean(DocumentContentProcessor.class, () -> custom)
                .run(context -> assertThat(context.getBean(DocumentContentProcessor.class)).isSameAs(custom));
    }

    @Test
    void bindsHttpProperties() {
        runner().withPropertyValues(
                        "document-analyzer.http.request-timeout=5s",
                        "document-analyzer.http.trust-all-certificates=true",
                        "document-analyzer.allow-private-addresses=true")
                .run(context -> {
                    DocumentAnalyzerProperties properties = context.getBean(DocumentAnalyzerProperties.class);
                    assertThat(properties.getHttp().getRequestTimeout()).hasSeconds(5);
                    assertThat(properties.getHttp().isTrustAllCertificates()).isTrue();
                    assertThat(properties.isAllowPrivateAddresses()).isTrue();
                });
    }
}
