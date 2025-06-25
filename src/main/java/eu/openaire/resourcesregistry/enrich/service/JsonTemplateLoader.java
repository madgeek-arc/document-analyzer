package eu.openaire.resourcesregistry.enrich.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class JsonTemplateLoader {

    private static final Logger logger = LoggerFactory.getLogger(JsonTemplateLoader.class);

    private JsonNode template;

    public JsonTemplateLoader() {
        // no-arg constructor
    }

    public JsonNode load() {
        if (template == null) {
            ObjectMapper mapper = new ObjectMapper();
            try (InputStream is = JsonTemplateLoader.class.getClassLoader().getResourceAsStream("template.json")) {

                if (is != null) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    logger.debug("JSON Template:\n{}", content);
                    this.template = mapper.readTree(content);
                } else {
                    logger.error("Could not read template.json");
                    throw new IOException("Could not read template.json");
                }
            } catch (IOException e) {
                return null;
            }
        }
        return template;
    }
}
