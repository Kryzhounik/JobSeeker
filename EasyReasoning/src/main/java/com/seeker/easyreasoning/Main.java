package com.seeker.easyreasoning;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Standalone entry point owning the API for one long-lived CLI session. */
public final class Main {
    public static void main(String[] args) {
        try {
            if (args.length > 1) throw new IllegalArgumentException("Usage: java -jar EasyReasoning/easy-reasoning.jar [project-root]");
            Path root = args.length == 1 ? Path.of(args[0]).toAbsolutePath().normalize() : findProjectRoot();
            var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            var output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
            var errors = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);
            int exitCode = run(root, input, output, errors);
            if (exitCode != 0) System.exit(exitCode);
        } catch (Exception failure) {
            System.err.println("EasyReasoning CLI failed: " + failure.getMessage());
            System.exit(1);
        }
    }

    static int run(Path root, BufferedReader input, PrintWriter output, PrintWriter errors) throws Exception {
        int exitCode;
        try (var api = new EasyReasoningApi(root)) {
            exitCode = EasyReasoningCli.run(api, input, output, errors);
        }
        errors.println("Stopped.");
        errors.flush();
        return exitCode;
    }

    private static Path findProjectRoot() throws Exception {
        Path location = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        for (Path path = Files.isDirectory(location) ? location : location.getParent(); path != null;
                path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("EasyReasoning/pom.xml"))) return path;
        }
        throw new IllegalStateException("Cannot locate project root; supply it as the optional argument");
    }
}
