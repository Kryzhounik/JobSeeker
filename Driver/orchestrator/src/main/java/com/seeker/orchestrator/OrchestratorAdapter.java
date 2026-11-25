package com.seeker.orchestrator;

import com.seeker.codex.UsageLimitExceededException;
import com.seeker.tools.api.ProgressSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Command declarations; the API owns the workflow and CliRunner owns output. */
@Command(name = "orchestrator", mixinStandardHelpOptions = true)
public final class OrchestratorAdapter {
    private final OrchestratorApi api;

    public OrchestratorAdapter(OrchestratorApi api) {
        this.api = api;
    }

    @Command(name = "process-batch", mixinStandardHelpOptions = true)
    public WorkflowResult processBatch(
            @Parameters(index = "0", arity = "0..1", paramLabel = "source") String source,
            @Option(names = "--_cb", hidden = true) Consumer<ProgressSnapshot> progress
    ) throws IOException, UsageLimitExceededException {
        return api.processBatch(source, progress);
    }

    @Command(name = "process-urls", mixinStandardHelpOptions = true)
    public WorkflowResult processUrls(
            @Parameters(index = "0..*", arity = "1..*", paramLabel = "URL") List<String> urls,
            @Option(names = "--_cb", hidden = true) Consumer<ProgressSnapshot> progress
    ) throws IOException, UsageLimitExceededException {
        return api.processUrls(urls, progress);
    }

    @Command(name = "check-recommendations", mixinStandardHelpOptions = true)
    public WorkflowResult checkRecommendations(
            @Option(names = "--_cb", hidden = true) Consumer<ProgressSnapshot> progress
    ) throws IOException, UsageLimitExceededException {
        return api.checkRecommendations(progress);
    }

    @Command(name = "resume", mixinStandardHelpOptions = true)
    public WorkflowResult resume(
            @Parameters(index = "0", paramLabel = "run-id") String runId,
            @Option(names = "--_cb", hidden = true) Consumer<ProgressSnapshot> progress
    ) throws UsageLimitExceededException {
        return api.resume(runId, progress);
    }

    @Command(name = "job-facts", mixinStandardHelpOptions = true)
    public StageResult jobFacts(
            @Parameters(index = "0", paramLabel = "run-id") String runId,
            @Parameters(index = "1", paramLabel = "source") String source,
            @Parameters(index = "2", arity = "1", split = ",", paramLabel = "job-ids") String[] jobIds,
            @Option(names = "--_cb", hidden = true) Consumer<ProgressSnapshot> progress
    ) throws UsageLimitExceededException {
        return api.jobFacts(runId, source, jobIds, progress);
    }

    @Command(name = "finish-analysis", mixinStandardHelpOptions = true)
    public StageResult finishAnalysis(
            @Parameters(index = "0", paramLabel = "run-id") String runId,
            @Parameters(index = "1", paramLabel = "source") String source,
            @Parameters(index = "2", arity = "1", split = ",", paramLabel = "job-ids") String[] jobIds,
            @Option(names = "--_cb", hidden = true) Consumer<ProgressSnapshot> progress
    ) throws UsageLimitExceededException {
        return api.finishAnalysis(runId, source, jobIds, progress);
    }
}
