package com.seeker.guifx;

import com.seeker.python.PythonRuntime;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class TestDatabase {
    static Path create(Path directory) throws Exception {
        Path root = ClientData.findProjectRoot();
        Path database = directory.resolve("jobs.sqlite");
        Path python = PythonRuntime.load(root.resolve("Tools/runtime.properties")).pythonExecutable();
        Process process = new ProcessBuilder(python.toString(),
                root.resolve("GUI/JavaFX/src/test/python/create_database.py").toString(),
                root.toString(), database.toString()).redirectErrorStream(true)
                .redirectOutput(directory.resolve("database-setup.log").toFile()).start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Database fixture setup timed out");
        }
        assertEquals(0, process.exitValue(), () -> {
            try { return java.nio.file.Files.readString(directory.resolve("database-setup.log")); }
            catch (Exception error) { return error.toString(); }
        });
        return database;
    }
}
