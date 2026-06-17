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

package eu.openaire.documentanalyzer.enrich.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import eu.openaire.documentanalyzer.common.model.Content;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmDocumentContentProcessorTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ChatClient chatClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private LlmDocumentContentProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new LlmDocumentContentProcessor(chatClient, mapper);
    }

    // ── splitArrayNode (existing tests) ──────────────────────────────────────

    @Test
    void splitArrayNode_emptyArray_returnsEmptyList() {
        ArrayNode array = mapper.createArrayNode();
        List<ArrayNode> result = LlmDocumentContentProcessor.splitArrayNode(array, 400, mapper);
        assertThat(result).isEmpty();
    }

    @Test
    void splitArrayNode_fewerElementsThanChunkSize_returnsSingleChunk() {
        ArrayNode array = mapper.createArrayNode();
        array.add("a");
        array.add("b");
        array.add("c");

        List<ArrayNode> result = LlmDocumentContentProcessor.splitArrayNode(array, 400, mapper);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).hasSize(3);
    }

    @Test
    void splitArrayNode_exactlyChunkSize_returnsSingleChunk() {
        ArrayNode array = mapper.createArrayNode();
        for (int i = 0; i < 400; i++) {
            array.add("item" + i);
        }

        List<ArrayNode> result = LlmDocumentContentProcessor.splitArrayNode(array, 400, mapper);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).hasSize(400);
    }

    @Test
    void splitArrayNode_moreElementsThanChunkSize_returnsMultipleChunks() {
        ArrayNode array = mapper.createArrayNode();
        for (int i = 0; i < 5; i++) {
            array.add("item" + i);
        }

        List<ArrayNode> result = LlmDocumentContentProcessor.splitArrayNode(array, 2, mapper);

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).hasSize(2);
        assertThat(result.get(1)).hasSize(2);
        assertThat(result.get(2)).hasSize(1);
    }

    @Test
    void splitArrayNode_preservesElementOrder() {
        ArrayNode array = mapper.createArrayNode();
        array.add("first");
        array.add("second");
        array.add("third");

        List<ArrayNode> result = LlmDocumentContentProcessor.splitArrayNode(array, 2, mapper);

        assertThat(result.get(0).get(0).asText()).isEqualTo("first");
        assertThat(result.get(0).get(1).asText()).isEqualTo("second");
        assertThat(result.get(1).get(0).asText()).isEqualTo("third");
    }

    @Test
    void splitArrayNode_totalElementsPreserved() {
        ArrayNode array = mapper.createArrayNode();
        for (int i = 0; i < 10; i++) {
            array.add(i);
        }

        List<ArrayNode> chunks = LlmDocumentContentProcessor.splitArrayNode(array, 3, mapper);

        int total = chunks.stream().mapToInt(ArrayNode::size).sum();
        assertThat(total).isEqualTo(10);
    }

    // ── extractInformation ───────────────────────────────────────────────────

    @Test
    void extractInformation_validArrayResponse_returnsArrayNode() {
        when(chatClient.prompt().advisors(any(SimpleLoggerAdvisor.class)).user(anyString()).call()
                .chatResponse().getResult().getOutput().getText())
                .thenReturn("[\"paragraph1\",\"paragraph2\"]");

        Content content = new Content();
        content.setText("some text");

        ArrayNode result = processor.extractInformation(content);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).asText()).isEqualTo("paragraph1");
    }

    @Test
    void extractInformation_markdownWrappedJson_isUnwrapped() {
        when(chatClient.prompt().advisors(any(SimpleLoggerAdvisor.class)).user(anyString()).call()
                .chatResponse().getResult().getOutput().getText())
                .thenReturn("```json\n[\"paragraph1\"]\n```");

        Content content = new Content();
        content.setText("some text");

        ArrayNode result = processor.extractInformation(content);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
    }

    @Test
    void extractInformation_invalidJsonAllRetries_returnsNull() {
        when(chatClient.prompt().advisors(any(SimpleLoggerAdvisor.class)).user(anyString()).call()
                .chatResponse().getResult().getOutput().getText())
                .thenReturn("{{{invalid");

        Content content = new Content();
        content.setText("some text");

        ArrayNode result = processor.extractInformation(content);

        assertThat(result).isNull();
    }

    @Test
    void extractInformation_retriesOnInvalidJson_succeedsOnRetry() {
        when(chatClient.prompt().advisors(any(SimpleLoggerAdvisor.class)).user(anyString()).call()
                .chatResponse().getResult().getOutput().getText())
                .thenReturn("{{{invalid")
                .thenReturn("[\"recovered\"]");

        Content content = new Content();
        content.setText("some text");

        ArrayNode result = processor.extractInformation(content);

        assertThat(result).isNotNull();
        assertThat(result.get(0).asText()).isEqualTo("recovered");
    }

    // ── generate ─────────────────────────────────────────────────────────────

    @Test
    void generate_validObjectResponse_returnsJsonNode() throws Exception {
        when(chatClient.prompt().advisors(any(SimpleLoggerAdvisor.class)).user(anyString()).call()
                .chatResponse().getResult().getOutput().getText())
                .thenReturn("{\"title\":\"Test Title\"}");

        Content content = new Content();
        content.setText("document text");
        JsonNode template = mapper.readTree("{\"title\":\"\"}");

        JsonNode result = processor.generate(template, content);

        assertThat(result).isNotNull();
        assertThat(result.get("title").asText()).isEqualTo("Test Title");
    }

    @Test
    void generate_invalidJsonAllRetries_returnsNull() throws Exception {
        when(chatClient.prompt().advisors(any(SimpleLoggerAdvisor.class)).user(anyString()).call()
                .chatResponse().getResult().getOutput().getText())
                .thenReturn("{{{invalid");

        Content content = new Content();
        content.setText("document text");
        JsonNode template = mapper.readTree("{\"title\":\"\"}");

        JsonNode result = processor.generate(template, content);

        assertThat(result).isNull();
    }

    // ── translate ─────────────────────────────────────────────────────────────

    @Test
    void translate_singleChunk_returnsTranslatedElements() {
        when(chatClient.prompt().advisors(any(SimpleLoggerAdvisor.class)).user(anyString()).call()
                .chatResponse().getResult().getOutput().getText())
                .thenReturn("[\"hola\",\"mundo\"]");

        ArrayNode input = mapper.createArrayNode();
        input.add("hello");
        input.add("world");

        ArrayNode result = processor.translate(input, "Spanish");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).asText()).isEqualTo("hola");
        assertThat(result.get(1).asText()).isEqualTo("mundo");
    }

    @Test
    void translate_multipleChunks_mergesAllResults() {
        when(chatClient.prompt().advisors(any(SimpleLoggerAdvisor.class)).user(anyString()).call()
                .chatResponse().getResult().getOutput().getText())
                .thenReturn("[\"chunk1_translated\"]")
                .thenReturn("[\"chunk2_translated\"]");

        ArrayNode input = mapper.createArrayNode();
        for (int i = 0; i < 401; i++) {
            input.add("item" + i);
        }

        ArrayNode result = processor.translate(input, "French");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).asText()).isEqualTo("chunk1_translated");
        assertThat(result.get(1).asText()).isEqualTo("chunk2_translated");
    }
}
