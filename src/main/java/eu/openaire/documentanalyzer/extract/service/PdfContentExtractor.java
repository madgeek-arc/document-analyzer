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

import eu.openaire.documentanalyzer.common.model.PdfContent;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class PdfContentExtractor implements ContentExtractor {

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int PDF_SIGNATURE_SEARCH_LIMIT = 1024;

    /**
     * Returns whether the data is a PDF document, i.e. whether the {@code %PDF-} header appears within its
     * first 1024 bytes.
     */
    public static boolean isPdf(byte[] data) {
        if (data == null) {
            return false;
        }
        int limit = Math.min(data.length, PDF_SIGNATURE_SEARCH_LIMIT) - PDF_SIGNATURE.length;
        for (int i = 0; i <= limit; i++) {
            if (Arrays.equals(data, i, i + PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length)) {
                return true;
            }
        }
        return false;
    }

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
