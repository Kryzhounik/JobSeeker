package com.seeker.easyreasoning;

import com.seeker.tools.cli.CliRunner;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/** Long-lived console entry; reusable sessions accept a caller-owned API. */
public final class EasyReasoningCli {

    @SuppressWarnings("PMD.SystemPrintln") // Session completion and CLI errors retain their console format.
    public static void main(String[] args) {
        try {
            if (args.length > 1) throw new IllegalArgumentException("Usage: java -jar EasyReasoning/easy-reasoning.jar [project-root]");
            Path root = args.length == 1 ? Path.of(args[0]).toAbsolutePath().normalize() : findProjectRoot();
            int exitCode;
            try (var api = new EasyReasoningApi(root)) {
                exitCode = CliRunner.runSession(new EasyReasoningAdapter(api));
            }
            System.err.println("Stopped.");
            if (exitCode != 0) System.exit(exitCode);
        } catch (Exception failure) {
            System.err.println("EasyReasoning CLI failed: " + failure.getMessage());
            System.exit(1);
        }
    }

    public static int run(EasyReasoningApi api, BufferedReader input, PrintWriter output, PrintWriter errors)
            throws IOException {
        return CliRunner.runSession(new EasyReasoningAdapter(api), input, output, errors);
    }

    private static Path findProjectRoot() throws Exception {
        Path location = Path.of(EasyReasoningCli.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        for (Path path = Files.isDirectory(location) ? location : location.getParent(); path != null;
                path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("EasyReasoning/pom.xml"))) return path;
        }
        throw new IllegalStateException("Cannot locate project root; supply it as the optional argument");
    }
}
