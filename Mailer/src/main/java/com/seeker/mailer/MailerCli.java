package com.seeker.mailer;

import com.seeker.tools.cli.CliRunner;
import java.nio.file.Path;

/** Standalone console entry; the API and adapter are created once per process. */
public final class MailerCli {
    private MailerCli() {}

    public static void main(String[] args) {
        var api = new Mailer(Path.of("."));
        var commands = new MailerAdapter(api);
        System.exit(CliRunner.run(commands, args));
    }
}
