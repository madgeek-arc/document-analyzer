package eu.openaire.documentanalyzer.enrich.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import eu.openaire.documentanalyzer.common.model.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LlmContentProcessor implements DocumentProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LlmContentProcessor.class);

    private final ChatModel chatModel;
    private final ContentMetadataRequestBuilder contentBuilder;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmContentProcessor(ChatModel chatModel, ContentMetadataRequestBuilder contentBuilder) {
        this.chatModel = chatModel;
        this.contentBuilder = contentBuilder;
    }

    @Override
    public JsonNode generateMetadata(JsonNode template, Content content) {
        JsonNode response = null;
        ChatClient.ChatClientRequestSpec client = ChatClient
                .create(chatModel)
                .prompt()
                .advisors(new SimpleLoggerAdvisor());
        try {
            response = requestJson(client, contentBuilder.request(template, content));
        } catch (RuntimeException e) {
            return null;
        }
        return response;
    }

    @Override
    public ArrayNode translate(ArrayNode content) {
        return translate(content, "English");
    }

    @Override
    public ArrayNode translate(ArrayNode content, String language) {
        String requestTemplate = """
        Translate the following JSON list to %s and return it in the same format.
        You will only translate the content, you will not add any explanation or comment.
        
        %s
        """.formatted(language, "%s");
        List<ArrayNode> chunks = splitArrayNode(content, 400, mapper);

        ArrayNode translated = mapper.createArrayNode();

        ChatResponse response;
        ChatClient.ChatClientRequestSpec client = ChatClient.create(chatModel).prompt()
                .advisors(new SimpleLoggerAdvisor());

        for (int i = 0; i < chunks.size(); i++) {
            logger.debug("Translating: [{} / {}]", i+1, chunks.size());
            String request = String.format(requestTemplate, chunks.get(i).toPrettyString());

            JsonNode json = requestJson(client, request);
            if (!json.isArray()) {
                throw new RuntimeException("JSON response should be an array.");
            }
            ArrayNode translatedChunk = (ArrayNode) json;

            for (JsonNode node : translatedChunk) {
                translated.add(node);
            }
        }

        return translated;
    }

    JsonNode requestJson(ChatClient.ChatClientRequestSpec client, String request) {
        ChatResponse chatResponse;
        JsonNode json = null;
        // retry up to 3 times if response is truncated
        for (int j = 0; j < 3; j++) {
            try {
                chatResponse = client.user(request).call().chatResponse();
                json = map(chatResponse.getResult().getOutput().getText());
                return json;
            } catch (JsonProcessingException e) {
                logger.warn(e.getMessage(), e);
                client.user("The JSON response you provided was invalid, I will ask you in the following prompt to resend it.").call();
            }
        }
        throw new RuntimeException("Could not retrieve response in valid JSON format. Aborting..");
    }

    private String stripJsonCodeFormatting(String jsonString) {
        if (jsonString != null) {
            jsonString = jsonString.replaceAll("```json\n|```", "");
        }
        return jsonString;
    }

    private JsonNode map(String json) throws JsonProcessingException {
        String responseText = stripJsonCodeFormatting(json);
        return mapper.readTree(responseText);
    }

    public static List<ArrayNode> splitArrayNode(ArrayNode original, int chunkSize, ObjectMapper objectMapper) {
        List<ArrayNode> chunks = new ArrayList<>();
        int total = original.size();

        for (int i = 0; i < total; i += chunkSize) {
            ArrayNode chunk = objectMapper.createArrayNode();
            for (int j = i; j < i + chunkSize && j < total; j++) {
                JsonNode node = original.get(j);
                chunk.add(node);
            }
            chunks.add(chunk);
        }

        return chunks;
    }

}
