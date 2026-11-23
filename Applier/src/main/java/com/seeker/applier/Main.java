package com.seeker.applier;

import com.seeker.codex.UsageLimitExceededException;

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
        int exitCode = execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int execute(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            printHelp();
            return 0;
        }
        try {
            Path projectRoot = Path.of("").toAbsolutePath().normalize();
            if (!Files.isDirectory(projectRoot.resolve("Applier"))
                    || !Files.isDirectory(projectRoot.resolve("Driver"))) {
                throw new IllegalStateException("Run Applier from the Seeker project root.");
            }
            String result = new ApplierCli(new ApplierApi(projectRoot)).handle(args);
            System.out.write(result.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.write('\n');
            return 0;
        } catch (IllegalArgumentException invalid) {
            System.err.println(invalid.getMessage());
            return 2;
        } catch (UsageLimitExceededException limit) {
            System.err.println("Codex usage limit: " + limit.getMessage());
            if (limit.getRetryAt() != null) System.err.println("Retry at: " + limit.getRetryAt());
            return 3;
        } catch (Exception error) {
            error.printStackTrace(System.err);
            return 1;
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
