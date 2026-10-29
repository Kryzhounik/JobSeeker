package com.seeker.orchestrator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.seeker.codex.CodexClient;
import com.seeker.codex.UsageLimitExceededException;
import com.seeker.collector.linkedin.collection.ScopeItem;
import com.seeker.collector.linkedin.config.ProjectPaths;
import com.seeker.collector.linkedin.python.PythonRuntime;
import com.seeker.python.JpyRuntime;
import org.jpy.PyModule;
import org.jpy.PyObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class JpyProcessingGateway implements ProcessingGateway {
    private static final Gson GSON = new Gson();

    private final ProjectPaths paths;
    private final PythonRuntime runtime;

    JpyProcessingGateway(Path projectRoot) throws IOException {
        this.paths = ProjectPaths.fromProjectRoot(projectRoot);
        this.runtime = PythonRuntime.load(
                paths.collectorRoot().resolve("java_linkedin/runtime.properties")
        );
    }

    @Override
    public void updateBatch(String runId, String status, String stage, String message) {
        try (PyModule bridge = bridge();
             PyObject ignored = bridge.callMethod(
                     "update_batch", runId, status, stage, message, paths.databasePath().toString())) {
            // Update the existing collection row, never a second batch registry.
        }
    }

    @Override
    public List<ScopeItem> loadCollectedScope(String runId) {
        try (PyModule bridge = bridge();
             PyObject response = bridge.callMethod(
                     "load_collected_scope",
                     runId,
                     paths.databasePath().toString()
             )) {
            if (!response.isString()) {
                throw new IllegalStateException(
                        "Collected scope bridge returned " + response.repr()
                                + " instead of String"
                );
            }
            ScopeItem[] items = GSON.fromJson(response.getStringValue(), ScopeItem[].class);
            if (items == null) {
                throw new IllegalStateException("Collected scope returned null JSON");
            }
            return List.of(items);
        }
    }

    @Override
    public Map<String, String> loadStatuses(String source, List<ScopeItem> scope) {
        try (PyModule bridge = bridge();
             PyObject response = bridge.callMethod(
                     "load_statuses",
                     source,
                     GSON.toJson(scope.stream().map(ScopeItem::jobId).toList()),
                     paths.databasePath().toString()
             )) {
            return GSON.fromJson(response.getStringValue(),
                    new TypeToken<Map<String, String>>() {}.getType());
        }
    }

    @Override
    public AgentFilterResult runRelevanceFilter(String runId, List<ScopeItem> scope)
            throws UsageLimitExceededException {
        try (PyModule bridge = bridge()) {
            String response = CodexClient.call(bridge,
                     "run_agent_filter",
                     runId,
                     GSON.toJson(scope),
                     paths.databasePath().toString()
             );
            AgentFilterResult result = GSON.fromJson(
                    response,
                    AgentFilterResult.class
            );
            if (result == null) {
                throw new IllegalStateException("Agent filter returned null JSON");
            }
            return result;
        }
    }

    @Override
    public void markRelevance(
            String source, List<ScopeItem> scope, List<String> nonrelevantIds
    ) {
        try (PyModule bridge = bridge();
             PyObject ignored = bridge.callMethod(
                     "mark_relevance",
                     paths.databasePath().toString(),
                     source,
                     GSON.toJson(scope.stream().map(ScopeItem::jobId).toList()),
                     GSON.toJson(nonrelevantIds)
             )) {
            // The bridge updates all statuses in one SQLite transaction.
        }
    }

    @Override
    public String extractJobFacts(
            String runId,
            String source,
            String jobId,
            String target,
            String threadId
    ) throws UsageLimitExceededException {
        try (PyModule bridge = bridge()) {
            String response = CodexClient.call(bridge,
                     "run_job_facts",
                     runId,
                     source,
                     jobId,
                     target,
                     threadId == null ? "" : threadId,
                     paths.databasePath().toString()
             );
            String returnedThreadId = response.strip();
            if (returnedThreadId.isEmpty()) {
                throw new IllegalStateException("Job facts returned no thread ID");
            }
            if (threadId != null && !threadId.equals(returnedThreadId)) {
                throw new IllegalStateException("Job facts changed thread ID");
            }
            return returnedThreadId;
        }
    }

    @Override
    public void initializeAnalysisRun(String runId, int parallelAgents, int vacanciesPerAgent) {
        try (PyModule bridge = bridge();
             PyObject ignored = bridge.callMethod(
                     "start_analysis",
                     runId,
                     parallelAgents,
                     vacanciesPerAgent,
                     paths.databasePath().toString()
             )) {
            // Python persists the effective analyzer settings for this run.
        }
    }

    @Override
    public String evaluateCandidateFit(
            String runId,
            String source,
            String jobId,
            String target,
            String threadId
    ) throws UsageLimitExceededException {
        try (PyModule bridge = bridge()) {
            String response = CodexClient.call(bridge,
                     "run_candidate_fit",
                     runId,
                     source,
                     jobId,
                     target,
                     threadId == null ? "" : threadId,
                     paths.databasePath().toString()
             );
            String returnedThreadId = response.strip();
            if (returnedThreadId.isEmpty()) {
                return threadId;
            }
            if (threadId != null && !threadId.equals(returnedThreadId)) {
                throw new IllegalStateException("Candidate fit changed thread ID");
            }
            return returnedThreadId;
        }
    }

    @Override
    public void calculateJobInterest(String source, String jobId) {
        try (PyModule bridge = bridge();
             PyObject ignored = bridge.callMethod(
                     "run_job_interest",
                     source,
                     jobId,
                     paths.databasePath().toString()
             )) {
            // Existing Python code scores the selected JSON file.
        }
    }

    @Override
    public int saveScored(String source, List<String> jobIds) {
        try (PyModule bridge = bridge();
             PyObject response = bridge.callMethod(
                     "save_scored",
                     source,
                     GSON.toJson(jobIds),
                     paths.databasePath().toString()
             )) {
            return response.getIntValue();
        }
    }

    private PyModule bridge() {
        JpyRuntime.ensureStarted(
                runtime.pythonLibrary(),
                runtime.jpyLibrary(),
                runtime.jdlLibrary(),
                runtime.pythonHome(),
                runtime.pythonExecutable(),
                paths.driverRoot()
        );
        return PyModule.importModule("orchestrator.python_bridge");
    }
}
