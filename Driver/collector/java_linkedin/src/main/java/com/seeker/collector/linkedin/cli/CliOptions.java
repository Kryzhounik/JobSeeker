package com.seeker.collector.linkedin.cli;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

record CliOptions(
        Command command,
        String runId,
        Integer limit,
        String location,
        String url
) {
    private static final DateTimeFormatter RUN_ID_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final Set<String> OPTIONS = Set.of(
            "run-id",
            "limit",
            "location",
            "url"
    );

    enum Command {
        LOGIN,
        BATCH,
        FROM_URL
    }

    static CliOptions parse(String[] args) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            throw new HelpRequested();
        }
        Command command = switch (args[0].toLowerCase()) {
            case "login" -> Command.LOGIN;
            case "batch" -> Command.BATCH;
            case "from-url" -> Command.FROM_URL;
            default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
        };

        Map<String, String> values = new HashMap<>();
        for (int index = 1; index < args.length; index++) {
            String token = args[index];
            if (!token.startsWith("--")) {
                throw new IllegalArgumentException("Expected --option, got: " + token);
            }
            String name = token.substring(2);
            if (!OPTIONS.contains(name)) {
                throw new IllegalArgumentException("Unknown option: --" + name);
            }
            if (++index >= args.length) {
                throw new IllegalArgumentException("Missing value for --" + name);
            }
            values.put(name, args[index]);
        }

        String url = values.get("url");
        if (command == Command.FROM_URL && (url == null || url.isBlank())) {
            throw new IllegalArgumentException("from-url requires --url");
        }
        return new CliOptions(
                command,
                values.getOrDefault(
                        "run-id",
                        RUN_ID_TIME.format(Instant.now()) + "-" + commandName(command) + "-linkedin"
                ),
                integer(values.get("limit")),
                values.get("location"),
                url
        );
    }

    static String usage() {
        return """
                Seeker Java LinkedIn collector

                Commands:
                  login     Open the persistent browser profile and wait for LinkedIn login.
                  batch     Collect a configured LinkedIn batch and print its JSON result.
                  from-url  Collect one LinkedIn vacancy; requires --url <url>.

                Options:
                  --run-id <id>              Workflow run id.
                  --limit <count>            Override the configured batch limit.
                  --location <Name[:geoId]>  Collect one location.
                  --url <url>                Vacancy URL for from-url.
                """;
    }

    private static String commandName(Command command) {
        return command == Command.FROM_URL ? "from-url" : command.name().toLowerCase();
    }

    private static Integer integer(String value) {
        return value == null ? null : Integer.valueOf(value);
    }

    static final class HelpRequested extends RuntimeException {
    }
}
