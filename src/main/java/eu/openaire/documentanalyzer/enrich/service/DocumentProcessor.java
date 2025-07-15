package eu.openaire.documentanalyzer.enrich.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import eu.openaire.documentanalyzer.common.model.Content;

public interface DocumentProcessor {

    JsonNode generateMetadata(JsonNode template, Content content);

    ArrayNode translate(ArrayNode content, String language);

    ArrayNode translate(ArrayNode content);
}
