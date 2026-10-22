package com.seeker.codex;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jpy.PyModule;
import org.jpy.PyObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/** Calls the metered Python proxy and translates its structured failures. */
public final class CodexClient {
    private static final Gson JSON = new Gson();

    private CodexClient() { }

    /** Calls an agent operation using an already initialized Python runtime. */
    public static String call(PyModule module, String operation, Object... arguments)
            throws UsageLimitExceededException {
        try (PyObject moduleName = module.getAttribute("__name__");
             PyModule adapter = PyModule.importModule("codex_proxy.bridge");
             PyObject response = adapter.callMethod("call", moduleName.getStringValue(),
                     operation, JSON.toJson(arguments))) {
            Response result = decode(response.getStringValue());
            if (result.error() != null) throw new IllegalStateException(result.error().message());
            return result.result();
        }
    }

    /** Runs metrics_proxy.py with its machine-readable response protocol. */
    public static String call(List<String> command, Path directory)
            throws IOException, InterruptedException, UsageLimitExceededException {
        List<String> arguments = new ArrayList<>(command);
        arguments.add("--json-result");
        Process process = new ProcessBuilder(arguments).directory(directory.toFile()).start();
        FutureTask<String> stderr = new FutureTask<>(() ->
                new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
        Thread.startVirtualThread(stderr);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        String diagnostics;
        try {
            String errorOutput = stderr.get().strip();
            diagnostics = errorOutput.isEmpty() ? "" : "\nstderr: " + errorOutput;
        } catch (ExecutionException error) {
            throw new IOException("Cannot read Codex Proxy stderr", error.getCause());
        }
        Response result;
        try {
            result = decode(output);
        } catch (JsonParseException error) {
            throw new IOException("Invalid Codex Proxy response (" + exitCode + "): "
                    + output.strip() + diagnostics, error);
        }
        if (exitCode != 0 || result.error() != null) {
            throw new IOException("Codex Proxy failed (" + exitCode + "): "
                    + (result.error() == null ? output.strip() : result.error().message()) + diagnostics);
        }
        return result.result();
    }

    private static Response decode(String output) throws UsageLimitExceededException {
        Response response = JSON.fromJson(output, Response.class);
        if (response == null) throw new JsonParseException("Empty Codex Proxy response");
        Failure error = response.error();
        if (error != null && "usage_limit".equals(error.code())) {
            Instant retryAt = null;
            if (error.retry_at() != null) {
                try {
                    retryAt = Instant.parse(error.retry_at());
                } catch (DateTimeParseException ignored) {
                    // The limit remains actionable even if the reset time is unavailable.
                }
            }
            throw new UsageLimitExceededException(error.message(), retryAt);
        }
        return response;
    }

    private record Response(String result, Failure error) { }
    private record Failure(String code, String message, String retry_at) { }
}
