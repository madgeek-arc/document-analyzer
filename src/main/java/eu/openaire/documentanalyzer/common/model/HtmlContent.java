package eu.openaire.documentanalyzer.common.model;

public class HtmlContent extends Content {

    private String html;

    public HtmlContent() {
    }

    public static HtmlContent of(String html, String text) {
        HtmlContent content = new HtmlContent();
        content.setHtml(html);
        content.setText(text);
        return content;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    @Override
    public String toString() {
        return """
                === HTML Content ===
                %s
                ====================
                """.formatted(
                this.html
        );
    }
}
