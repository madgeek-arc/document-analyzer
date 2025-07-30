package eu.openaire.documentanalyzer.enrich.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import eu.openaire.documentanalyzer.common.model.Content;

public interface DocumentContentProcessor {

    default <T extends Content> JsonNode enrich(JsonNode template, T content, EnrichMethod method) throws JsonProcessingException {
        return method.perform(template, content);
    }

    <T extends Content> ArrayNode extractInformation(T content);

    <T extends Content> JsonNode generate(JsonNode template, T content);

    ArrayNode translate(ArrayNode content, String language);

    ArrayNode translate(ArrayNode content);
}
