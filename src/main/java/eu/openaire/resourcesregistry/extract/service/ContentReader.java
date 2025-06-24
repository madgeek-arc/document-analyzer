package eu.openaire.resourcesregistry.extract.service;

import java.io.IOException;
import java.net.URI;

public interface ContentReader {

    byte[] read(URI uri) throws IOException;

}
