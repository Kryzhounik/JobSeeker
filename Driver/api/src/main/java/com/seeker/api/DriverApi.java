package com.seeker.api;

import com.seeker.applier.ApplierRunner;
import com.seeker.codex.UsageLimitExceededException;
import com.seeker.chatevaluator.ChatEvaluatorRunner;
import com.seeker.chatevaluator.GptEvaluation;
import com.seeker.collector.linkedin.application.LinkedInApplication;
import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.ScopeItem;
import com.seeker.collector.linkedin.login.LoginResult;
import com.seeker.orchestrator.StageResult;
import com.seeker.orchestrator.WorkflowOrchestrator;
import com.seeker.orchestrator.WorkflowProgress;
import com.seeker.orchestrator.WorkflowResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Java entry point delegating to the existing module APIs. */
public final class DriverApi {
    private final Path projectRoot;
    private final LinkedInApplication linkedin;
    private final WorkflowOrchestrator orchestrator;
    private final ApplierRunner applier;

    public DriverApi(Path projectRoot) throws IOException {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.linkedin = new LinkedInApplication(this.projectRoot);
        this.orchestrator = new WorkflowOrchestrator(this.projectRoot);
        this.applier = new ApplierRunner(this.projectRoot);
    }

    public LoginResult login() throws IOException {
        return linkedin.login();
    }

    public CollectionReport collectBatch() throws IOException {
        return linkedin.collectBatch();
    }

    public CollectionReport collectBatch(Consumer<CollectionProgress> progress) throws IOException {
        return linkedin.collectBatch(progress);
    }

    public WorkflowResult processBatch(String source) throws IOException, UsageLimitExceededException {
        return orchestrator.processBatch(source);
    }

    public WorkflowResult processBatch(String source, Consumer<WorkflowProgress> progress)
            throws IOException, UsageLimitExceededException {
        return orchestrator.processBatch(source, progress);
    }

    public WorkflowResult processUrls(String... urls) throws IOException, UsageLimitExceededException {
        return orchestrator.processUrls(urls);
    }

    public WorkflowResult processUrls(List<String> urls, Consumer<WorkflowProgress> progress)
            throws IOException, UsageLimitExceededException {
        return orchestrator.processUrls(urls, progress);
    }

    public WorkflowResult checkRecommendations() throws IOException, UsageLimitExceededException {
        return orchestrator.checkRecommendations();
    }

    public WorkflowResult checkRecommendations(Consumer<WorkflowProgress> progress)
            throws IOException, UsageLimitExceededException {
        return orchestrator.checkRecommendations(progress);
    }

    public WorkflowResult resume(String runId) throws UsageLimitExceededException {
        return orchestrator.resume(runId);
    }

    public WorkflowResult resume(String runId, Consumer<WorkflowProgress> progress) throws UsageLimitExceededException {
        return orchestrator.resume(runId, progress);
    }

    public StageResult jobFacts(String runId, String source, List<ScopeItem> scope) throws UsageLimitExceededException {
        return orchestrator.jobFacts(runId, source, scope);
    }

    public StageResult jobFacts(String runId, String source, List<ScopeItem> scope,
                                Consumer<WorkflowProgress> progress) throws UsageLimitExceededException {
        return orchestrator.jobFacts(runId, source, scope, progress);
    }

    public StageResult finishAnalysis(String runId, String source, List<ScopeItem> scope) throws UsageLimitExceededException {
        return orchestrator.finishAnalysis(runId, source, scope);
    }

    public StageResult finishAnalysis(String runId, String source, List<ScopeItem> scope,
                                      Consumer<WorkflowProgress> progress) throws UsageLimitExceededException {
        return orchestrator.finishAnalysis(runId, source, scope, progress);
    }

    public ApplierRunner.Result prepareApplication(String url)
            throws IOException, InterruptedException, UsageLimitExceededException {
        return applier.run(url);
    }

    public String evaluateWithChatGPT(String url) throws Exception {
        return ChatEvaluatorRunner.run(projectRoot, url);
    }

    public GptEvaluation.Result evaluateWithChatGPT(String url, int jobId) throws Exception {
        return ChatEvaluatorRunner.run(projectRoot, url, jobId);
    }
}
