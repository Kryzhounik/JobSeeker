package com.seeker.easyreasoning.llm;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Owns only the local server process started by this module. */
final class OllamaRuntime implements AutoCloseable {
    private final URI endpoint;
    private final Path projectRoot;
    private Process process;
    private boolean ready;

    OllamaRuntime(String baseUrl, Path projectRoot) {
        this.endpoint = URI.create(baseUrl);
        this.projectRoot = projectRoot;
    }

    void ensureRunning() {
        String host = endpoint.getHost();
        if (ready || !("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "[::1]".equals(host) || "::1".equals(host))) return;
        if (isReady()) {
            ready = true;
            return;
        }
        Path executable = projectRoot.resolve(".tools/ollama/ollama.exe").toAbsolutePath();
        if (!Files.isRegularFile(executable)) {
            throw new IllegalStateException("Ollama is unavailable at " + endpoint
                    + "; local executable not found: " + executable);
        }
        try {
            Path log = projectRoot.resolve("Data/easy-reasoning/ollama.log").toAbsolutePath();
            Files.createDirectories(log.getParent());
            ProcessBuilder builder = new ProcessBuilder(executable.toString(), "serve");
            builder.environment().put("OLLAMA_HOST", endpoint.getAuthority());
            process = builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (process.isAlive() && System.nanoTime() < deadline) {
                if (isReady()) {
                    ready = true;
                    return;
                }
                Thread.sleep(100);
            }
            throw new IllegalStateException("Ollama did not start at " + endpoint + "; see " + log);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot start Ollama at " + endpoint, error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted starting Ollama at " + endpoint, error);
        } finally {
            if (!ready) close();
        }
    }

    private boolean isReady() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) endpoint.resolve("/api/version").toURL().openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            return connection.getResponseCode() == 200;
        } catch (IOException error) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Override
    public void close() {
        ready = false;
        if (process == null) return;
        Process owned = process;
        process = null;
        var children = owned.descendants().toList();
        owned.destroy();
        children.forEach(ProcessHandle::destroy);
        try {
            if (!owned.waitFor(5, TimeUnit.SECONDS)) owned.destroyForcibly();
        } catch (InterruptedException error) {
            owned.destroyForcibly();
            Thread.currentThread().interrupt();
        } finally {
            children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        }
    }
}
