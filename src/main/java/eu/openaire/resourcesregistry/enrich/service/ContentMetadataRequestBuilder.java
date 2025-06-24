package eu.openaire.resourcesregistry.enrich.service;


import com.fasterxml.jackson.databind.JsonNode;
import eu.openaire.resourcesregistry.model.Content;
import eu.openaire.resourcesregistry.model.HtmlContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ContentMetadataRequestBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ContentMetadataRequestBuilder.class);

    public <T extends Content> String request(JsonNode json, T content) {
        return switch (content) {
            case HtmlContent html -> request(json, html);
            default -> request(json, content.getText());
        };
    }

    public String request(JsonNode json, String documentContent) {
        return """
                Fill up the following JSON template with the content extracted from the provided document.
                If a field is not available, return an empty string or empty list.
                                
                === JSON Template ===
                %s
                =====================
                                
                === Document Content ===
                %s
                ========================
                                
                You will output only a valid JSON object in plain text, without any formatting.
                """.formatted(
                json.toPrettyString(), documentContent
        );
    }

    public String request(JsonNode json, HtmlContent documentContent) {
        String request = request(json, documentContent.toString());
        return """
                %s
                                    
                If the provided HTML Document contains a not found message,
                (e.g. "We're sorry, but the page you were looking for doesn't exist.", "404", "Not Found")
                then return an empty json '{}'
                
                Use only the main content of the html, discard header/top menu and footer.
                """.formatted(request);
    }
}
