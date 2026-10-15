package dev.mindforge.infrastructure.config;

import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.application.service.IngestionService;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.domain.port.EventPublisher;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.ProgressNotifier;
import dev.mindforge.infrastructure.event.SpringEventPublisher;
import dev.mindforge.infrastructure.parsing.DocxParser;
import dev.mindforge.infrastructure.parsing.MarkdownParser;
import dev.mindforge.infrastructure.parsing.ParserRegistry;
import dev.mindforge.infrastructure.parsing.PdfParser;
import dev.mindforge.infrastructure.parsing.PlainTextParser;
import dev.mindforge.infrastructure.security.UploadSanitizer;

/** Wires document upload: the parser registry, the upload sanitizer, domain events and {@link IngestionService}. */
@Configuration
public class IngestionConfig {

    /** A new document format is one more entry here. */
    @Bean
    ParserRegistry parserRegistry() {
        return new ParserRegistry(Map.of(
            MarkdownParser.MIME_TYPE, new MarkdownParser(),
            PdfParser.MIME_TYPE, new PdfParser(),
            DocxParser.MIME_TYPE, new DocxParser(),
            PlainTextParser.MIME_TYPE, new PlainTextParser()));
    }

    /** Admits exactly the MIME types a parser is registered for. */
    @Bean
    UploadSanitizer uploadSanitizer(AppProperties properties, ParserRegistry parserRegistry) {
        return new UploadSanitizer(properties.getUpload().getMaxSize().toBytes(), parserRegistry.mimeTypes());
    }

    @Bean
    EventPublisher eventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SpringEventPublisher(applicationEventPublisher);
    }

    @Bean
    IngestionService ingestionService(UploadSanitizer uploadSanitizer, ParserRegistry parserRegistry,
                                      DocumentRepository documentRepository, IngestRunRepository ingestRunRepository,
                                      EventPublisher eventPublisher, ProgressNotifier progressNotifier,
                                      TransactionOperations transactionOperations) {
        return new IngestionService(uploadSanitizer, parserRegistry, documentRepository, ingestRunRepository,
            eventPublisher, progressNotifier, transactionOperations);
    }
}
