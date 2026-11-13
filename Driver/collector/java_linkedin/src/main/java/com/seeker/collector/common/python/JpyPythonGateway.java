package com.seeker.collector.common.python;

import com.seeker.collector.common.Preview;
import com.seeker.collector.common.PreviewDecision;
import com.seeker.collector.common.ProcessResult;
import com.seeker.collector.common.ProjectPaths;
import com.seeker.collector.common.JsonSupport;
import com.seeker.python.JpyRuntime;
import com.seeker.python.PythonRuntime;
import com.seeker.collector.common.PreparedVacancy;
import org.jpy.PyModule;
import org.jpy.PyObject;


public final class JpyPythonGateway implements PythonGateway {
    private final PyModule bridge;
    private boolean closed;

    public JpyPythonGateway(ProjectPaths paths, PythonRuntime runtime) {
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
    public void startRun(String runId, Object config) {
        callVoid("start_run", runId, JsonSupport.GSON.toJson(config));
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
    public PreviewDecision decidePreview(Preview preview) {
        return JsonSupport.GSON.fromJson(
                callString("decide_preview", JsonSupport.GSON.toJson(preview)),
                PreviewDecision.class
        );
    }

    @Override
    public ProcessResult processVacancy(PreparedVacancy vacancy) {
        return JsonSupport.GSON.fromJson(
                callString(
                        "process_vacancy",
                        JsonSupport.GSON.toJson(vacancy)
                ),
                ProcessResult.class
        );
    }

    @Override
    public String saveRaw(Preview preview, String content, String extension, String method) {
        return callString("save_raw", JsonSupport.GSON.toJson(preview), content, extension, method);
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
