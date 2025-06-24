package eu.openaire.resourcesregistry.enrich.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import eu.openaire.resourcesregistry.model.Content;

public interface ContentEnricher {

    <T extends Content> JsonNode enrich(T content) throws JsonProcessingException;

}
