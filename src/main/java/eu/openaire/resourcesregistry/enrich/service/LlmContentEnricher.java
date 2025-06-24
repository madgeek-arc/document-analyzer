package eu.openaire.resourcesregistry.enrich.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.openaire.resourcesregistry.model.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class LlmContentEnricher implements ContentEnricher {

    private static final Logger logger = LoggerFactory.getLogger(LlmContentEnricher.class);

    private final LlmClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonTemplateLoader templateLoader = new JsonTemplateLoader();

    public LlmContentEnricher(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public JsonNode enrich(Content content) {
        JsonNode json = llmClient.generateMetadata(templateLoader.load(), content);
        ArrayNode sentences = getSentences(content.getText());
        if (json.isObject()) {
            ObjectNode obj = (ObjectNode) json;
            obj.put("text", content.getText());
            obj.set("content", sentences);
            if (obj.get("metadata").get("language").asText().startsWith("en")) {
                obj.set("content_en", sentences);
            } else {
                ArrayNode translatedSentences = llmClient.translate(sentences);
                obj.set("content_en", translatedSentences);
            }
        }

        return json;

    }

    private ArrayNode getSentences(String text) {
        // Pattern to match sentences ending in ., ! or ?
        Pattern pattern = Pattern.compile("([^.!?\\n]*[.!?]?\\n?)");
        Matcher matcher = pattern.matcher(text);

        ArrayNode arrayNode = mapper.createArrayNode();

        while (matcher.find()) {
            String sentence = matcher.group(1).trim();
            if (!sentence.isEmpty()) {
                arrayNode.add(sentence);
            }
        }
        return arrayNode;
    }

}
