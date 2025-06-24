package eu.openaire.resourcesregistry.model;

public class Content {

    protected String metadata;
    protected String text;

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text.strip();
    }

    @Override
    public String toString() {
        return """
                === Metadata ===
                %s
                ================
                
                === Text ===
                %s
                ============
                """.formatted(
                this.metadata, this.text
        );
    }
}
