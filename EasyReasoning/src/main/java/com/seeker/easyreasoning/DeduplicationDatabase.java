package com.seeker.easyreasoning;

import com.google.gson.Gson;
import com.seeker.python.JpyRuntime;
import com.seeker.python.PythonRuntime;
import org.jpy.PyModule;
import org.jpy.PyObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** Thin adapter to the shared Driver/db operations; no collector session. */
final class DeduplicationDatabase implements AutoCloseable {
    private final PyModule bridge;
    private final String database;

    DeduplicationDatabase(Path root) throws IOException {
        Path config = root.resolve("Driver/collector/java_linkedin/runtime.properties");
        var runtime = PythonRuntime.load(config);
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) { properties.load(reader); }
        database = root.resolve(properties.getProperty("database.path")).toAbsolutePath().normalize().toString();
        JpyRuntime.ensureStarted(runtime.pythonLibrary(), runtime.jpyLibrary(), runtime.jdlLibrary(),
                runtime.pythonHome(), runtime.pythonExecutable(), root, root.resolve("Driver"));
        bridge = PyModule.importModule("EasyReasoning.python_bridge");
    }

    List<VacancySnapshot> loadBatch(String batchId) {
        try (PyObject result = bridge.callMethod("load_batch", database, batchId)) {
            return List.of(new Gson().fromJson(result.getStringValue(), VacancySnapshot[].class));
        }
    }

    void saveLink(String batchId, long duplicate, long original) {
        try (PyObject ignored = bridge.callMethod("save_link", database, batchId, duplicate, original)) { }
    }

    @Override public void close() { bridge.close(); }
}
