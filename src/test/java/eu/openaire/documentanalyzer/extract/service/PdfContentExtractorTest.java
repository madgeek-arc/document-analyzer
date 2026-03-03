package eu.openaire.documentanalyzer.extract.service;

import eu.openaire.documentanalyzer.common.model.PdfContent;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfContentExtractorTest {

    private final PdfContentExtractor extractor = new PdfContentExtractor();

    private byte[] createPdfWithText(String text) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(100, 700);
                cs.showText(text);
                cs.endText();
            }
            document.save(baos);
        }
        return baos.toByteArray();
    }

    private byte[] createEmptyPdf() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(baos);
        }
        return baos.toByteArray();
    }

    @Test
    void extract_withValidPdf_returnsNonNullContent() throws IOException {
        byte[] pdfBytes = createPdfWithText("Hello PDF World");
        PdfContent content = extractor.extract(pdfBytes);
        assertThat(content).isNotNull();
    }

    @Test
    void extract_withValidPdf_extractsText() throws IOException {
        byte[] pdfBytes = createPdfWithText("Hello PDF World");
        PdfContent content = extractor.extract(pdfBytes);
        assertThat(content.getText()).contains("Hello PDF World");
    }

    @Test
    void extract_withEmptyPdf_returnsBlankText() throws IOException {
        byte[] pdfBytes = createEmptyPdf();
        PdfContent content = extractor.extract(pdfBytes);
        assertThat(content).isNotNull();
        assertThat(content.getText()).isBlank();
    }

    @Test
    void extract_withInvalidBytes_throwsIOException() {
        byte[] invalidBytes = "this is not a valid pdf file".getBytes();
        assertThatThrownBy(() -> extractor.extract(invalidBytes))
                .isInstanceOf(IOException.class);
    }

    @Test
    void extract_returnsMetadataField() throws IOException {
        byte[] pdfBytes = createPdfWithText("Some content");
        PdfContent content = extractor.extract(pdfBytes);
        // metadata may be empty string but should not be null
        assertThat(content.getMetadata()).isNotNull();
    }
}
