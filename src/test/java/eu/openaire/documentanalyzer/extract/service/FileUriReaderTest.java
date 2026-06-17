/*
 * Copyright 2026 OpenAIRE AMKE
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.openaire.documentanalyzer.extract.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUriReaderTest {

    private final FileUriReader reader = new FileUriReader();

    @Test
    void read_existingFile_returnsBytesAndUri(@TempDir Path dir) throws IOException {
        byte[] expected = "hello from file".getBytes();
        Path file = dir.resolve("test.txt");
        Files.write(file, expected);

        UriReader.Data data = reader.read(file.toUri());

        assertThat(data.data()).isEqualTo(expected);
        assertThat(data.uri()).isEqualTo(file.toUri());
    }

    @Test
    void read_nonExistentFile_throwsIOException() {
        URI uri = URI.create("file:///no/such/file.txt");

        assertThatThrownBy(() -> reader.read(uri))
                .isInstanceOf(IOException.class);
    }

    @Test
    void read_directoryUri_throwsIOException(@TempDir Path dir) {
        assertThatThrownBy(() -> reader.read(dir.toUri()))
                .isInstanceOf(IOException.class);
    }
}
