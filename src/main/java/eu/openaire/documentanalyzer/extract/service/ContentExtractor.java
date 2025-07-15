package eu.openaire.documentanalyzer.extract.service;

import eu.openaire.documentanalyzer.common.model.Content;

import java.io.IOException;

public interface ContentExtractor {

    Content extract(byte[] data) throws IOException;
}
