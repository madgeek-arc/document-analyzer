package eu.openaire.resourcesregistry.enrich.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import eu.openaire.resourcesregistry.model.Content;

public interface LlmClient {

    JsonNode generateMetadata(JsonNode template, Content content);

    ArrayNode translate(ArrayNode content);
}
