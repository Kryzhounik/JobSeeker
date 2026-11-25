package com.seeker.tools.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.seeker.codex.UsageLimitExceededException;
import com.seeker.tools.api.ProgressSnapshot;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Consumer;
import picocli.CommandLine;

/** Shared picocli console runner, independent of module commands and DTOs. */
public final class CliRunner {
    private static final Gson JSON = new GsonBuilder()
            .serializeNulls()
            .disableHtmlEscaping()
            .registerTypeAdapter(Instant.class,
                    (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString()))
            .create();

    private CliRunner() {}

    public static int run(Object commands, String... args) {
        var stdout = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        var stderr = new PrintWriter(System.err, true, StandardCharsets.UTF_8);
        return run(commands, stdout, stderr, args);
    }

    /** Long-lived console session. The caller owns the adapter and its API. */
    public static int runSession(Object commands) throws IOException {
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        var stdout = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        var stderr = new PrintWriter(System.err, true, StandardCharsets.UTF_8);
        return runSession(commands, input, stdout, stderr);
    }

    /** Reads text commands without closing the caller's streams or API. */
    public static int runSession(Object commands, BufferedReader input, PrintWriter stdout,
                                 PrintWriter stderr) throws IOException {
        var sessionInput = new CliSessionInput(input);
        int exitCode = 0;
        stderr.println("Ready. Enter a command or stop.");
        stderr.flush();
        while (true) {
            String[] arguments;
            try {
                arguments = sessionInput.readCommand();
            } catch (IllegalArgumentException error) {
                stderr.println(error.getMessage());
                stderr.flush();
                exitCode = 2;
                continue;
            }
            if (arguments == null) return exitCode;
            int commandCode = run(commands, stdout, stderr, arguments);
            if (commandCode != 0) exitCode = commandCode;
        }
    }

    /** Uses the same output format as command results, flushing each update immediately. */
    private static Consumer<ProgressSnapshot> progress(PrintWriter stdout) {
        return progress -> printOutput(progress, stdout);
    }

    /** Reusable JVM entry; the caller owns the supplied writers. */
    public static int run(Object commands, PrintWriter stdout, PrintWriter stderr, String... args) {
        var parser = new CommandLine(commands)
                .setOut(stdout)
                .setErr(stderr)
                .setExpandAtFiles(false)
                .setExecutionExceptionHandler(CliRunner::handleError)
                .setExecutionStrategy(parsed -> executeAndPrint(parsed, stdout));

        // picocli handles unknown/missing arguments and usage (exit code 2).
        // Successful commands return 0. Only main calls System.exit().
        return parser.execute(args);
    }

    private static int handleError(Exception error, CommandLine command, CommandLine.ParseResult parseResult) {
        if (error instanceof UsageLimitExceededException limit) {
            command.getErr().println("Codex usage limit: " + limit.getMessage());
            if (limit.getRetryAt() != null)
                command.getErr().println("Retry at: " + limit.getRetryAt());
            command.getErr().flush();
            return 3;
        }
        command.getErr().println(error.getMessage());
        command.getErr().flush();
        if (error instanceof CommandLine.ParameterException) {
            return command.getCommandSpec().exitCodeOnInvalidInput();
        }
        return command.getCommandSpec().exitCodeOnExecutionException();
    }

    private static int executeAndPrint(CommandLine.ParseResult parseResult, PrintWriter stdout) {
        var selected = parseResult;
        while (selected.hasSubcommand()) {
            selected = selected.subcommand();
        }
        var command = selected.commandSpec().commandLine();
        var callback = selected.commandSpec().findOption("--_cb");
        if (callback != null) {
            callback.setValue(progress(stdout));
        }

        // Supply the callback after parsing, then invoke the annotated method.
        int exitCode = new CommandLine.RunLast().execute(parseResult);
        Object result = command.getExecutionResult();

        // Only the console boundary formats the result; the API returns DTOs.
        // Help is printed by picocli and has no method result to print here.
        if (result != null) {
            printOutput(result, stdout);
        }
        return exitCode;
    }

    private static void printOutput(Object value, PrintWriter stdout) {
        stdout.println(value instanceof String text ? text : JSON.toJson(value));
        if (stdout.checkError()) {
            throw new IllegalStateException("Cannot write CLI output to stdout");
        }
    }
}
