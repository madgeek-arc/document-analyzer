package eu.openaire.documentanalyzer.extract.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FileUriReader implements UriReader {

    @Override
    public byte[] read(URI uri) throws IOException {
        Path path = Path.of(uri);
        return Files.readAllBytes(path);
    }
}
