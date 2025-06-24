package eu.openaire.resourcesregistry.extract.service;

import eu.openaire.resourcesregistry.model.Content;

import java.io.IOException;

public interface ContentExtractor {

    Content extract(byte[] content) throws IOException;
}
