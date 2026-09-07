package com.seeker.collector.linkedin.python;

import com.google.gson.reflect.TypeToken;
import com.seeker.collector.linkedin.collection.Preview;
import com.seeker.collector.linkedin.collection.PreviewDecision;
import com.seeker.collector.linkedin.collection.ProcessResult;
import com.seeker.collector.linkedin.config.ProjectPaths;
import com.seeker.collector.linkedin.support.JsonSupport;
import org.jpy.PyInputMode;
import org.jpy.PyLib;
import org.jpy.PyLibInitializer;
import org.jpy.PyModule;
import org.jpy.PyObject;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JpyPythonGateway implements PythonGateway {
    private static final Type STRING_LIST = new TypeToken<List<String>>() {
    }.getType();

    private final PyModule bridge;
    private boolean closed;

    public JpyPythonGateway(ProjectPaths paths, PythonRuntime runtime) {
        Path pythonLibrary = requiredFile(runtime.pythonLibrary(), "CPython library");
        Path jpyLibrary = requiredFile(runtime.jpyLibrary(), "jpy library");
        Path jdlLibrary = requiredFile(runtime.jdlLibrary(), "jdl library");

        PyLibInitializer.initPyLib(
                pythonLibrary.toString(),
                jpyLibrary.toString(),
                jdlLibrary.toString()
        );
        if (runtime.pythonExecutable() != null
                && !PyLib.setProgramName(absolute(runtime.pythonExecutable()).toString())) {
            throw new IllegalStateException(
                    "jpy rejected Python executable: " + runtime.pythonExecutable()
            );
        }
        if (runtime.pythonHome() != null
                && !PyLib.setPythonHome(absolute(runtime.pythonHome()).toString())) {
            throw new IllegalStateException(
                    "jpy rejected Python home: " + runtime.pythonHome()
            );
        }

        PyLib.startPython(
                paths.driverRoot().toString(),
                paths.collectorRoot().toString()
        );
        try (PyObject ignored = PyObject.executeCode(
                "import sys\nsys.stdout = sys.stderr",
                PyInputMode.SCRIPT
        )) {
            // Keep stdout reserved for the Java command's final JSON result.
        }
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
    public ProcessResult processHtml(Preview preview, String html) {
        return JsonSupport.GSON.fromJson(
                callString(
                        "process_html",
                        JsonSupport.GSON.toJson(preview),
                        html
                ),
                ProcessResult.class
        );
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
            PyObject.cleanup();
            PyLib.stopPython();
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

    private static Path requiredFile(Path value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(
                    label + " is required; check Driver/collector/java_linkedin/runtime.properties"
            );
        }
        Path resolved = absolute(value);
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException(label + " not found: " + resolved);
        }
        return resolved;
    }

    private static Path absolute(Path value) {
        return value.toAbsolutePath().normalize();
    }
}
