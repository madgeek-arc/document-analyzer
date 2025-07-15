package eu.openaire.documentanalyzer.extract.service;

import eu.openaire.documentanalyzer.common.model.PdfContent;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
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
