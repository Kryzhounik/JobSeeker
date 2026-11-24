package com.seeker.tools.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.seeker.codex.UsageLimitExceededException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
        // picocli invokes the selected annotated method with parsed arguments.
        // That method calls the API and returns its ordinary Java result.
        int exitCode = new CommandLine.RunLast().execute(parseResult);

        var selected = parseResult;
        while (selected.hasSubcommand()) {
            selected = selected.subcommand();
        }
        var command = selected.commandSpec().commandLine();
        Object result = command.getExecutionResult();

        // Only the console boundary formats the result; the API returns DTOs.
        // Help is printed by picocli and has no method result to print here.
        if (result != null) {
            stdout.println(result instanceof String text ? text : JSON.toJson(result));
            if (stdout.checkError()) {
                throw new CommandLine.ExecutionException(command, "Cannot write CLI result to stdout");
            }
        }
        return exitCode;
    }
}
