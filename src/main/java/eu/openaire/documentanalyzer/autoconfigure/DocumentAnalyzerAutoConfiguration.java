package eu.openaire.documentanalyzer.autoconfigure;

import eu.openaire.documentanalyzer.analyze.service.DocumentAnalyzerService;
import eu.openaire.documentanalyzer.enrich.service.DocumentContentProcessor;
import eu.openaire.documentanalyzer.enrich.service.LlmDocumentContentProcessor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

@AutoConfiguration
public class DocumentAnalyzerAutoConfiguration {

    @ConditionalOnClass(ChatClient.class)
    @ConditionalOnProperty("system-prompt")
    @Bean
    ChatClient chatClient(ChatClient.Builder builder,
                          @Value("${system-prompt}") String systemPrompt) {
        return builder.defaultSystem(systemPrompt)
                .build();
    }

    @ConditionalOnClass(DocumentContentProcessor.class)
    @Bean
    DocumentContentProcessor documentContentProcessor(ChatModel chatModel) {
        return new LlmDocumentContentProcessor(chatModel);
    }

    @ConditionalOnClass(DocumentAnalyzerService.class)
    @Bean
    DocumentAnalyzerService documentAnalyzerService(DocumentContentProcessor documentContentProcessor) throws NoSuchAlgorithmException, KeyManagementException {
        return new DocumentAnalyzerService(documentContentProcessor);
    }
}
