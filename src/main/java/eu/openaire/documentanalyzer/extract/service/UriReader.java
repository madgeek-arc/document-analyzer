package eu.openaire.documentanalyzer.extract.service;

import java.io.IOException;
import java.net.URI;

public interface UriReader {

    byte[] read(URI uri) throws IOException;

}
