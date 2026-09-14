package dev.mindforge.infrastructure.config;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.agent.ClaimExtractor;
import dev.mindforge.agent.LinkChecker;
import dev.mindforge.agent.PageWriter;
import dev.mindforge.agent.RelevanceGuard;
import dev.mindforge.agent.SupersessionDetector;
import dev.mindforge.agent.WikiReviewer;
import dev.mindforge.application.service.IngestPipeline;
import dev.mindforge.application.service.LintService;
import dev.mindforge.application.service.RunWorker;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.domain.port.DocumentRepository;
import dev.mindforge.domain.port.EventPublisher;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.ai.PermitGateway;
import dev.mindforge.infrastructure.ai.PromptLoader;
import dev.mindforge.infrastructure.event.RunWorkerTriggers;
import dev.mindforge.infrastructure.event.SseProgressNotifier;

/** Wires the ingest pipeline: model services behind the background permit pool, the run worker and progress. */
@Configuration
@EnableScheduling
public class PipelineConfig {

    private static final String PROMPT_LOCALE = "pl";

    @Bean
    ProcessingSettings processingSettings(AppProperties properties) {
        AppProperties.Ai.Model models = properties.getAi().getModel();
        return ProcessingSettings.withModels(Map.of(
            ModelTier.SMALL, models.getSmall(), ModelTier.LARGE, models.getLarge(), ModelTier.VISION, models.getVision()));
    }

    @Bean
    PromptLoader promptLoader() {
        return new PromptLoader(PROMPT_LOCALE);
    }

    /** The one permit pool every {@code INGEST} and {@code LINT} model call takes from. */
    @Bean
    Semaphore backgroundPermits(AppProperties properties) {
        return new Semaphore(properties.getAi().getBackgroundPermits());
    }

    @Bean
    Executor runExecutor() {
        return command -> Thread.ofVirtual().name("ingest-run").start(command);
    }

    @Bean
    RelevanceGuard relevanceGuard(AIGateway aiGateway, Semaphore backgroundPermits, PromptLoader promptLoader) {
        return new RelevanceGuard(new PermitGateway(aiGateway, backgroundPermits), promptLoader);
    }

    @Bean
    ClaimExtractor claimExtractor(AIGateway aiGateway, Semaphore backgroundPermits, PromptLoader promptLoader,
                                  ProcessingSettings processingSettings) {
        return new ClaimExtractor(new PermitGateway(aiGateway, backgroundPermits), promptLoader,
            processingSettings.maxClaimsPerExtractCall());
    }

    @Bean
    PageWriter pageWriter(AIGateway aiGateway, Semaphore backgroundPermits, PromptLoader promptLoader) {
        return new PageWriter(new PermitGateway(aiGateway, backgroundPermits), promptLoader);
    }

    @Bean
    LinkChecker linkChecker(AIGateway aiGateway, Semaphore backgroundPermits, PromptLoader promptLoader) {
        return new LinkChecker(new PermitGateway(aiGateway, backgroundPermits), promptLoader);
    }

    @Bean
    SupersessionDetector supersessionDetector(AIGateway aiGateway, Semaphore backgroundPermits,
                                              PromptLoader promptLoader) {
        return new SupersessionDetector(new PermitGateway(aiGateway, backgroundPermits), promptLoader);
    }

    @Bean
    WikiReviewer wikiReviewer(AIGateway aiGateway, Semaphore backgroundPermits, PromptLoader promptLoader) {
        return new WikiReviewer(new PermitGateway(aiGateway, backgroundPermits), promptLoader);
    }

    @Bean
    SseProgressNotifier progressNotifier() {
        return new SseProgressNotifier();
    }

    @Bean
    IngestPipeline ingestPipeline(DocumentRepository documentRepository, WikiStore wikiStore,
                                  IngestRunRepository ingestRunRepository, RelevanceGuard relevanceGuard,
                                  ClaimExtractor claimExtractor, PageWriter pageWriter, LinkChecker linkChecker,
                                  SupersessionDetector supersessionDetector, SseProgressNotifier progressNotifier,
                                  TransactionOperations transactionOperations, ProcessingSettings processingSettings) {
        return new IngestPipeline(documentRepository, wikiStore, ingestRunRepository, relevanceGuard, claimExtractor,
            pageWriter, linkChecker, supersessionDetector, progressNotifier, transactionOperations,
            processingSettings);
    }

    @Bean
    LintService lintService(WikiStore wikiStore, IngestRunRepository ingestRunRepository, LinkChecker linkChecker,
                            WikiReviewer wikiReviewer, EventPublisher eventPublisher,
                            SseProgressNotifier progressNotifier, TransactionOperations transactionOperations,
                            ProcessingSettings processingSettings) {
        return new LintService(wikiStore, ingestRunRepository, linkChecker, wikiReviewer, eventPublisher,
            progressNotifier, transactionOperations, processingSettings);
    }

    @Bean
    RunWorker runWorker(IngestRunRepository ingestRunRepository, IngestPipeline ingestPipeline,
                        LintService lintService, SseProgressNotifier progressNotifier, EventPublisher eventPublisher,
                        TransactionOperations transactionOperations, Executor runExecutor) {
        return new RunWorker(ingestRunRepository, ingestPipeline, lintService, progressNotifier, eventPublisher,
            transactionOperations, runExecutor);
    }

    @Bean
    RunWorkerTriggers runWorkerTriggers(RunWorker runWorker, Executor runExecutor) {
        return new RunWorkerTriggers(runWorker, runExecutor);
    }
}
