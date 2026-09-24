package com.seeker.orchestrator;

import com.google.gson.Gson;
import com.seeker.collector.linkedin.collection.ScopeItem;
import com.seeker.collector.linkedin.config.ProjectPaths;
import com.seeker.collector.linkedin.python.PythonRuntime;
import com.seeker.python.JpyRuntime;
import org.jpy.PyInputMode;
import org.jpy.PyModule;
import org.jpy.PyObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

final class JpyAgentFilterGateway implements AgentFilterGateway {
    private static final Gson GSON = new Gson();

    private final ProjectPaths paths;
    private final PythonRuntime runtime;

    JpyAgentFilterGateway(Path projectRoot) throws IOException {
        this.paths = ProjectPaths.fromProjectRoot(projectRoot);
        this.runtime = PythonRuntime.load(
                paths.collectorRoot().resolve("java_linkedin/runtime.properties")
        );
    }

    @Override
    public AgentFilterResult filter(String runId, List<ScopeItem> scope) {
        try (PyModule bridge = bridge();
             PyObject response = bridge.callMethod(
                     "run_agent_filter",
                     runId,
                     GSON.toJson(scope),
                     paths.databasePath().toString()
             )) {
            if (!response.isString()) {
                throw new IllegalStateException(
                        "Agent filter bridge returned " + response.repr()
                                + " instead of String"
                );
            }
            AgentFilterResult result = GSON.fromJson(
                    response.getStringValue(),
                    AgentFilterResult.class
            );
            if (result == null) {
                throw new IllegalStateException("Agent filter returned null JSON");
            }
            return result;
        }
    }

    @Override
    public void markNonrelevant(String source, List<String> jobIds) {
        try (PyModule bridge = bridge();
             PyObject ignored = bridge.callMethod(
                     "mark_nonrelevant",
                     paths.databasePath().toString(),
                     source,
                     GSON.toJson(jobIds)
             )) {
            // The bridge updates all statuses in one SQLite transaction.
        }
    }

    @Override
    public String jobFacts(
            String runId,
            String source,
            String jobId,
            String target,
            String threadId
    ) {
        try (PyModule bridge = bridge();
             PyObject response = bridge.callMethod(
                     "run_job_facts",
                     runId,
                     source,
                     jobId,
                     target,
                     threadId == null ? "" : threadId,
                     paths.databasePath().toString()
             )) {
            if (!response.isString()) {
                throw new IllegalStateException(
                        "Job facts bridge returned " + response.repr()
                                + " instead of String"
                );
            }
            String returnedThreadId = response.getStringValue().strip();
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
    public void startAnalysis(String runId, int vacanciesPerAgent) {
        try (PyModule bridge = bridge();
             PyObject ignored = bridge.callMethod(
                     "start_analysis",
                     runId,
                     vacanciesPerAgent,
                     paths.databasePath().toString()
             )) {
            // Python persists the effective analyzer settings for this run.
        }
    }

    @Override
    public String candidateFit(
            String runId,
            String source,
            String jobId,
            String target,
            String threadId
    ) {
        try (PyModule bridge = bridge();
             PyObject response = bridge.callMethod(
                     "run_candidate_fit",
                     runId,
                     source,
                     jobId,
                     target,
                     threadId == null ? "" : threadId,
                     paths.databasePath().toString()
             )) {
            if (!response.isString()) {
                throw new IllegalStateException(
                        "Candidate fit bridge returned " + response.repr()
                                + " instead of String"
                );
            }
            String returnedThreadId = response.getStringValue().strip();
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
    public void jobInterest(String source, String jobId) {
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
    public void saveScored(String source, List<String> jobIds) {
        try (PyModule bridge = bridge();
             PyObject ignored = bridge.callMethod(
                     "save_scored",
                     source,
                     GSON.toJson(jobIds),
                     paths.databasePath().toString()
             )) {
            // Existing Python code saves the selected scored JSON files.
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
        try (PyObject ignored = PyObject.executeCode(
                "import sys\nsys.stdout = sys.stderr",
                PyInputMode.SCRIPT
        )) {
            // Keep stdout reserved for the Java command's final JSON result.
        }
        return PyModule.importModule("orchestrator.python_bridge");
    }
}
