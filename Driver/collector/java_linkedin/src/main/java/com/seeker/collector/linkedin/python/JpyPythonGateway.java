package com.seeker.collector.linkedin.python;

import com.google.gson.reflect.TypeToken;
import com.seeker.collector.linkedin.collection.PageReport;
import com.seeker.collector.linkedin.collection.Preview;
import com.seeker.collector.linkedin.collection.PreviewDecision;
import com.seeker.collector.linkedin.collection.ProcessResult;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.linkedin.config.ProjectPaths;
import com.seeker.collector.linkedin.support.JsonSupport;
import com.seeker.python.JpyRuntime;
import com.seeker.easyreasoning.EasyReasoningApi;
import org.jpy.PyModule;
import org.jpy.PyObject;

import java.lang.reflect.Type;
import java.util.List;

public final class JpyPythonGateway implements PythonGateway {
    public List<com.seeker.collector.linkedin.collection.VacancySnapshot> loadSemanticBatch(String runId) {
        return JsonSupport.GSON.fromJson(callString("load_semantic_batch", runId),
                new TypeToken<List<com.seeker.collector.linkedin.collection.VacancySnapshot>>() { }.getType());
    }

    public void saveSemanticBatchLink(String runId, long duplicateId, long originalId) {
        callVoid("save_semantic_batch_link", runId, duplicateId, originalId);
    }
    private static final Type STRING_LIST = new TypeToken<List<String>>() {
    }.getType();

    private final PyModule bridge;
    private final EasyReasoningApi reasoning;
    private boolean closed;

    public JpyPythonGateway(ProjectPaths paths, PythonRuntime runtime, EasyReasoningApi reasoning) {
        this.reasoning = reasoning;
        JpyRuntime.ensureStarted(
                runtime.pythonLibrary(),
                runtime.jpyLibrary(),
                runtime.jdlLibrary(),
                runtime.pythonHome(),
                runtime.pythonExecutable(),
                paths.driverRoot(),
                paths.collectorRoot()
        );
        this.bridge = PyModule.importModule(
                "collector.java_linkedin.python_bridge"
        );
        callVoid(
                "init_session",
                paths.driverRoot().toString(),
                paths.databasePath().toString(),
                paths.rawDirectory().toString()
        );

    }

    @Override
    public void startRun(String runId, CollectorConfig config) {
        callVoid("start_run", runId, JsonSupport.GSON.toJson(config));
    }

    @Override
    public void logPage(String runId, PageReport page) {
        callVoid("log_page", runId, JsonSupport.GSON.toJson(page));
    }

    @Override
    public void finishRun(
            String runId,
            String status,
            String stopReason,
            int acceptedCount,
            String message
    ) {
        callVoid(
                "finish_run",
                runId,
                status,
                stopReason,
                acceptedCount,
                message
        );
    }

    @Override
    public List<String> priorityCompanyIds() {
        return JsonSupport.GSON.fromJson(
                callString("priority_company_ids"),
                STRING_LIST
        );
    }

    @Override
    public PreviewDecision decidePreview(Preview preview) {
        return JsonSupport.GSON.fromJson(
                callString("decide_preview", JsonSupport.GSON.toJson(preview)),
                PreviewDecision.class
        );
    }

    @Override
    public ProcessResult processHtml(Preview preview, String html, boolean easyApply) {
        return JsonSupport.GSON.fromJson(
                callString(
                        "process_html",
                        JsonSupport.GSON.toJson(preview),
                        html,
                        easyApply,
                        reasoning
                ),
                ProcessResult.class
        );
    }

    @Override
    public com.seeker.collector.linkedin.collection.VacancySnapshot semanticSnapshot(String source, String jobId) {
        return JsonSupport.GSON.fromJson(callString("semantic_snapshot", source, jobId),
                com.seeker.collector.linkedin.collection.VacancySnapshot.class);
    }

    @Override
    public void saveSemanticDuplicate(long duplicateId, long originalId) {
        callVoid("save_semantic_duplicate", duplicateId, originalId);
    }

    @Override
    public void logOutcome(Preview preview, String status, String reason) {
        callVoid(
                "log_outcome",
                JsonSupport.GSON.toJson(preview),
                status,
                reason
        );
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            callVoid("close_session");
        } finally {
            bridge.close();
        }
    }

    private String callString(String name, Object... args) {
        try (PyObject result = bridge.callMethod(name, args)) {
            if (!result.isString()) {
                throw new IllegalStateException(
                        "Python function " + name + " returned " + result.repr()
                                + " instead of String"
                );
            }
            return result.getStringValue();
        }
    }

    private void callVoid(String name, Object... args) {
        try (PyObject ignored = bridge.callMethod(name, args)) {
            // Python side owns the operation; no transport value is required.
        }
    }

}
