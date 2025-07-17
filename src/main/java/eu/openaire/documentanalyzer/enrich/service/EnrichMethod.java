package eu.openaire.documentanalyzer.enrich.service;

import com.fasterxml.jackson.databind.JsonNode;
import eu.openaire.documentanalyzer.common.model.Content;

@FunctionalInterface
public interface EnrichMethod {

    JsonNode perform(JsonNode template, Content content);

}
