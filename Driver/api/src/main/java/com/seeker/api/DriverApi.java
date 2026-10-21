package com.seeker.api;

import com.seeker.applier.ApplierRunner;
import com.seeker.chatevaluator.ChatEvaluatorRunner;
import com.seeker.collector.linkedin.application.LinkedInApplication;
import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.ScopeItem;
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
    private final LinkedInApplication linkedin;
    private final WorkflowOrchestrator orchestrator;
    private final ApplierRunner applier;

    public DriverApi(Path projectRoot) throws IOException {
        this(projectRoot, ignored -> { });
    }

    public DriverApi(Path projectRoot, Consumer<CollectionProgress> progress) throws IOException {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.linkedin = new LinkedInApplication(this.projectRoot, progress);
        this.orchestrator = new WorkflowOrchestrator(this.projectRoot, progress);
        this.applier = new ApplierRunner(this.projectRoot);
    }

    public LoginResult login() throws IOException {
        return linkedin.login();
    }

    public CollectionReport collectBatch() throws IOException {
        return linkedin.collectBatch();
    }

    public WorkflowResult processBatch(String source) throws IOException {
        return orchestrator.processBatch(source);
    }

    public WorkflowResult processUrls(String... urls) throws IOException {
        return orchestrator.processUrls(urls);
    }

    public WorkflowResult resume(String runId) {
        return orchestrator.resume(runId);
    }

    public StageResult jobFacts(String runId, String source, List<ScopeItem> scope) {
        return orchestrator.jobFacts(runId, source, scope);
    }

    public StageResult finishAnalysis(String runId, String source, List<ScopeItem> scope) {
        return orchestrator.finishAnalysis(runId, source, scope);
    }

    public ApplierRunner.Result prepareApplication(String url) throws Exception {
        return applier.run(url);
    }

    public String evaluateWithChatGPT(String url) throws Exception {
        return ChatEvaluatorRunner.run(projectRoot, url);
    }
}
