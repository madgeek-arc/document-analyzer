/*
 * Copyright 2025 OpenAIRE AMKE
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

import eu.openaire.documentanalyzer.common.model.PdfContent;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;

public class PdfContentExtractor implements ContentExtractor {

    @Override
    public PdfContent extract(byte[] data) throws IOException {
        try (PDDocument document = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            // Extract metadata
            PDDocumentInformation info = document.getDocumentInformation();

            StringBuilder builder = new StringBuilder();
            for (String key : info.getMetadataKeys()) {
                builder.append(key)
                        .append(": ")
                        .append(info.getCustomMetadataValue(key))
                        .append('\n');
            }

            return PdfContent.of(builder.toString(), text);
        }
    }
}
