package com.seeker.collector.support;

import com.seeker.python.JpyRuntime;
import com.seeker.python.PythonRuntime;
import org.jpy.PyModule;
import org.jpy.PyObject;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import java.nio.file.Path;

/** Test-only profile injection; the production gateway still initializes the real Python service. */
public final class CollectorProfileExtension implements BeforeEachCallback, AfterEachCallback {
    private PyObject profile;
    private String previousTrimming;

    @Override public void beforeEach(ExtensionContext context) throws Exception {
        previousTrimming = System.getProperty("vacancy.trimming.enabled");
        System.setProperty("vacancy.trimming.enabled", "false");
        Path root = Path.of(System.getProperty("seeker.project.root")).toAbsolutePath().normalize();
        var runtime = PythonRuntime.load(root.resolve("Tools/runtime.properties"));
        JpyRuntime.ensureStarted(runtime.pythonLibrary(), runtime.jpyLibrary(), runtime.jdlLibrary(),
                runtime.pythonHome(), runtime.pythonExecutable(), root, root.resolve("Driver"));
        try (var support = PyModule.importModule("Driver.test.filter_test_support")) {
            profile = support.callMethod("isolated_collector_session");
            try (var ignored = profile.callMethod("__enter__")) { }
        }
    }

    @Override public void afterEach(ExtensionContext context) {
        try {
            if (profile != null) {
                try (var ignored = profile.callMethod("__exit__", (Object) null, (Object) null, (Object) null)) { }
                finally { profile.close(); profile = null; }
            }
        } finally {
            if (previousTrimming == null) System.clearProperty("vacancy.trimming.enabled");
            else System.setProperty("vacancy.trimming.enabled", previousTrimming);
        }
    }
}
