package com.seeker.applier;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** Command-line entry point for the same application preparation used by the GUI. */
public final class Main {
    /**
     * @deprecated Do not maintain this help text. It may be removed when it no longer matches the CLI.
     */
    @Deprecated
    private static final String USAGE =
            "Usage (from the Seeker project root): java -jar Applier/applier.jar <vacancy-url>";

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            printHelp();
            return;
        }
        if (args.length != 1) {
            System.err.println(USAGE);
            System.exit(2);
            return;
        }
        try {
            URI url = URI.create(args[0]);
            if (!("http".equalsIgnoreCase(url.getScheme())
                    || "https".equalsIgnoreCase(url.getScheme())) || url.getHost() == null) {
                throw new IllegalArgumentException("Expected an absolute HTTP(S) vacancy URL.");
            }
        } catch (IllegalArgumentException invalidUrl) {
            System.err.println("Invalid vacancy URL: " + invalidUrl.getMessage());
            System.err.println(USAGE);
            System.exit(2);
            return;
        }

        try {
            Path projectRoot = Path.of("").toAbsolutePath().normalize();
            if (!Files.isDirectory(projectRoot.resolve("Applier"))
                    || !Files.isDirectory(projectRoot.resolve("Driver"))) {
                throw new IllegalStateException("Run Applier from the Seeker project root.");
            }
            ApplierRunner.Result result = new ApplierRunner(projectRoot).run(args[0]);
            System.out.println(new ObjectMapper().writeValueAsString(result));
        } catch (Exception error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * @deprecated Undocumented option: do not maintain it. It may be removed when its help becomes outdated.
     */
    @Deprecated
    private static void printHelp() {
        System.out.println(USAGE);
    }
}
