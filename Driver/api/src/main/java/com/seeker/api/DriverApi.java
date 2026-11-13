package com.seeker.api;

import com.seeker.applier.ApplierRunner;
import com.seeker.applier.ApplierApi;
import com.seeker.codex.UsageLimitExceededException;
import com.seeker.chatevaluator.ChatEvaluatorRunner;
import com.seeker.chatevaluator.GptEvaluation;
import com.seeker.collector.core.CollectorApi;
import com.seeker.tools.api.ProgressSnapshot;
import com.seeker.collector.common.CollectionReport;
import com.seeker.collector.common.ScopeItem;
import com.seeker.collector.linkedin.login.LoginResult;
import com.seeker.orchestrator.StageResult;
import com.seeker.orchestrator.WorkflowOrchestrator;
import com.seeker.orchestrator.WorkflowResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Java entry point delegating to the existing module APIs. */
public final class DriverApi {
    private final Path projectRoot;
    private final CollectorApi linkedin;
    private final WorkflowOrchestrator orchestrator;
    private final ApplierApi applier;

    public DriverApi(Path projectRoot) throws IOException {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.linkedin = new CollectorApi(this.projectRoot);
        this.orchestrator = new WorkflowOrchestrator(this.projectRoot);
        this.applier = new ApplierApi(this.projectRoot);
    }

    public LoginResult login() throws IOException {
        return linkedin.login();
    }

    public CollectionReport collectBatch() throws IOException {
        return linkedin.collectBatch();
    }

    public CollectionReport collectBatch(Consumer<ProgressSnapshot> progress) throws IOException {
        return linkedin.collectBatch(progress);
    }

    public WorkflowResult processBatch(String source) throws IOException, UsageLimitExceededException {
        return orchestrator.processBatch(source);
    }

    public WorkflowResult processBatch(String source, Consumer<ProgressSnapshot> progress)
            throws IOException, UsageLimitExceededException {
        return orchestrator.processBatch(source, progress);
    }

    public WorkflowResult processUrls(String... urls) throws IOException, UsageLimitExceededException {
        return orchestrator.processUrls(urls);
    }

    public WorkflowResult processUrls(List<String> urls, Consumer<ProgressSnapshot> progress)
            throws IOException, UsageLimitExceededException {
        return orchestrator.processUrls(urls, progress);
    }

    public WorkflowResult checkRecommendations() throws IOException, UsageLimitExceededException {
        return orchestrator.checkRecommendations();
    }

    public WorkflowResult checkRecommendations(Consumer<ProgressSnapshot> progress)
            throws IOException, UsageLimitExceededException {
        return orchestrator.checkRecommendations(progress);
    }

    public WorkflowResult resume(String runId) throws UsageLimitExceededException {
        return orchestrator.resume(runId);
    }

    public WorkflowResult resume(String runId, Consumer<ProgressSnapshot> progress) throws UsageLimitExceededException {
        return orchestrator.resume(runId, progress);
    }

    public StageResult jobFacts(String runId, String source, List<ScopeItem> scope) throws UsageLimitExceededException {
        return orchestrator.jobFacts(runId, source, scope);
    }

    public StageResult jobFacts(String runId, String source, List<ScopeItem> scope,
                                Consumer<ProgressSnapshot> progress) throws UsageLimitExceededException {
        return orchestrator.jobFacts(runId, source, scope, progress);
    }

    public StageResult finishAnalysis(String runId, String source, List<ScopeItem> scope) throws UsageLimitExceededException {
        return orchestrator.finishAnalysis(runId, source, scope);
    }

    public StageResult finishAnalysis(String runId, String source, List<ScopeItem> scope,
                                      Consumer<ProgressSnapshot> progress) throws UsageLimitExceededException {
        return orchestrator.finishAnalysis(runId, source, scope, progress);
    }

    public ApplierRunner.Result prepareApplication(String url)
            throws IOException, InterruptedException, UsageLimitExceededException {
        return applier.prepare(url);
    }

    public String coverLetter(long id)
            throws IOException, InterruptedException, UsageLimitExceededException {
        return applier.coverLetter(id);
    }

    public String evaluateWithChatGPT(String url) throws Exception {
        return ChatEvaluatorRunner.run(projectRoot, url);
    }

    public GptEvaluation.Result evaluateWithChatGPT(String url, int jobId) throws Exception {
        return ChatEvaluatorRunner.run(projectRoot, url, jobId);
    }
}
