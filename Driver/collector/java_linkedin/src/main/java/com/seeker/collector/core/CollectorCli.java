package com.seeker.collector.core;

import com.seeker.tools.cli.CliRunner;
import java.io.IOException;
import java.nio.file.Path;

public final class CollectorCli {
    private CollectorCli() {}

    public static void main(String[] args) throws IOException {
        System.exit(execute(args));
    }

    /** One-shot console startup, also used by the Driver router. */
    public static int execute(String[] args) throws IOException {
        Path projectRoot = Path.of(".");
        var api = new CollectorApi(projectRoot);
        var commands = new CollectorAdapter(api);
        return CliRunner.run(commands, args);
    }
}
