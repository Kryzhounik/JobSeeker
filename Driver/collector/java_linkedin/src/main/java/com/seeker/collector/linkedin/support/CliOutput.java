package com.seeker.collector.linkedin.support;

import com.seeker.tools.api.ProgressSnapshot;
import com.seeker.collector.linkedin.config.ProjectPaths;
import com.seeker.collector.linkedin.python.PythonRuntime;
import com.seeker.python.JpyRuntime;
import org.jpy.PyModule;
import org.jpy.PyObject;

import java.io.IOException;
import java.nio.file.Path;

/** Output policy for command-line entry points, never installed by library calls. */
public final class CliOutput implements AutoCloseable {
    private final PyModule sys;
    private final PyObject originalStdout;

    private CliOutput(PyModule sys, PyObject originalStdout) {
        this.sys = sys;
        this.originalStdout = originalStdout;
    }

    public static CliOutput redirectPython(Path projectRoot) throws IOException {
        ProjectPaths paths = ProjectPaths.fromProjectRoot(projectRoot);
        PythonRuntime runtime = PythonRuntime.load(paths.collectorRoot().resolve("java_linkedin/runtime.properties"));
        JpyRuntime.ensureStarted(runtime.pythonLibrary(), runtime.jpyLibrary(), runtime.jdlLibrary(),
                runtime.pythonHome(), runtime.pythonExecutable(), projectRoot,
                paths.driverRoot(), paths.collectorRoot());
        PyModule sys = PyModule.importModule("sys");
        PyObject original = sys.getAttribute("stdout");
        try (PyObject stderr = sys.getAttribute("stderr")) {
            sys.setAttribute("stdout", stderr);
        } catch (RuntimeException | Error error) {
            original.close();
            sys.close();
            throw error;
        }
        return new CliOutput(sys, original);
    }

    public static void printProgress(ProgressSnapshot progress) {
        System.err.println("COLLECTOR_PROGRESS " + JsonSupport.GSON.toJson(progress));
    }

    @Override
    public void close() {
        try {
            sys.setAttribute("stdout", originalStdout);
        } finally {
            originalStdout.close();
            sys.close();
        }
    }
}
