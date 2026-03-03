package eu.openaire.documentanalyzer.enrich.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmDocumentContentProcessorTest {

    private final ObjectMapper mapper = new ObjectMapper();

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
}
