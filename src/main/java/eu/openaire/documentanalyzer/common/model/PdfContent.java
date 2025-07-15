package eu.openaire.documentanalyzer.common.model;

public class PdfContent extends Content {

    public static PdfContent of(String metadata, String text) {
        PdfContent content = new PdfContent();
        content.setMetadata(metadata);
        content.setText(text);
        return content;
    }

    @Override
    public String toString() {
        return """
                === PDF Metadata ===
                %s
                ====================
                
                === PDF Content ===
                %s
                ===================
                """.formatted(
            this.metadata, this.text
        );
    }
}
