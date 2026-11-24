package com.seeker.chatevaluator;

import com.seeker.tools.cli.CliRunner;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

/** Standalone console entry; API and adapter are created once per process. */
public final class ChatEvaluatorCli {
    private ChatEvaluatorCli() {}

    public static void main(String[] args) {
        var api = new ChatEvaluatorApi(Path.of(System.getProperty("seeker.projectRoot", ".")));
        var commands = new ChatEvaluatorAdapter(api);
        // Keep the shared API/adapter/CliRunner pattern while preserving
        // the simple CLI input: one URL, without an extra command name.
        System.exit(CliRunner.run(commands,
                Stream.concat(Stream.of("evaluate"), Arrays.stream(args)).toArray(String[]::new)));
    }
}
