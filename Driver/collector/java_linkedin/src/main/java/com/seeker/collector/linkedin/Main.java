package com.seeker.collector.linkedin;

import com.seeker.collector.linkedin.cli.CliController;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = new CliController().execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
