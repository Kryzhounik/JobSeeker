package com.seeker.collector.linkedin.python;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record PythonRuntime(
        Path pythonLibrary,
        Path jpyLibrary,
        Path jdlLibrary,
        Path pythonHome,
        Path pythonExecutable
) {
    public static PythonRuntime load(Path configPath) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return new PythonRuntime(
                path(properties, "python.library"),
                path(properties, "jpy.library"),
                path(properties, "jdl.library"),
                path(properties, "python.home"),
                path(properties, "python.executable")
        );
    }

    private static Path path(Properties properties, String key) {
        String value = properties.getProperty(key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing runtime setting: " + key);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }
}
