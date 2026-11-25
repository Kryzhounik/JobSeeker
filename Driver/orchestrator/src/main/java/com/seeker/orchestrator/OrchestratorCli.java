package com.seeker.orchestrator;

import com.seeker.tools.cli.CliRunner;
import java.io.IOException;
import java.nio.file.Path;

/** One-shot console startup, also used by the Driver router. */
public final class OrchestratorCli {
    private OrchestratorCli() {}

    public static void main(String[] args) throws IOException {
        System.exit(execute(args));
    }

    public static int execute(String[] args) throws IOException {
        var api = new OrchestratorApi(Path.of("."));
        var commands = new OrchestratorAdapter(api);
        return CliRunner.run(commands, args);
    }
}
