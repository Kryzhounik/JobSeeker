package com.seeker.applier;

import com.seeker.tools.cli.CliRunner;
import java.nio.file.Path;

/** Standalone console entry; API and adapter are created once per process. */
public final class ApplierCli {
    private ApplierCli() {}

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    public static int execute(String[] args) {
        var api = new ApplierApi(Path.of(System.getProperty("seeker.projectRoot", ".")));
        return CliRunner.run(new ApplierAdapter(api), ApplierAdapter.arguments(args));
    }
}
