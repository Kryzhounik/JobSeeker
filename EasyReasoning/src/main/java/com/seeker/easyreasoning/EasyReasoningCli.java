package com.seeker.easyreasoning;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** A single context serving plain-text commands on standard input. */
public final class EasyReasoningCli {

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
        boolean hadErrors = false;
        try (var application = new EasyReasoningApplication(root)) {
            errors.println("Ready. Enter generate \"text\", clean-vacancy \"text\" or stop.");
            errors.flush();
            String line;
            while ((line = input.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    String commandLine = line.stripLeading();
                    int separator = 0;
                    while (separator < commandLine.length()
                            && !Character.isWhitespace(commandLine.charAt(separator))) separator++;
                    String command = commandLine.substring(0, separator);
                    String argument = commandLine.substring(separator).stripLeading();
                    if ("stop".equals(command) && argument.isEmpty()) break;
                    if (!"generate".equals(command) && !"clean-vacancy".equals(command)) {
                        throw new IllegalArgumentException("Usage: generate \"text\", clean-vacancy \"text\" or stop");
                    }
                    String prompt = readPrompt(argument, input);
                    String response = "generate".equals(command) ? application.generate(prompt)
                            : application.cleanVacancy(prompt);
                    output.print(response);
                    if (!response.endsWith("\n")) output.println();
                    output.flush();
                } catch (RuntimeException failure) {
                    hadErrors = true;
                    errors.println("Error: " + (failure.getMessage() == null
                            ? failure.getClass().getSimpleName() : failure.getMessage()));
                    errors.flush();
                }
            }
        }
        errors.println("Stopped.");
        errors.flush();
        return hadErrors ? 1 : 0;
    }

    private static String readPrompt(String argument, BufferedReader input) throws java.io.IOException {
        if (!argument.startsWith("\"")) return argument;
        StringBuilder prompt = new StringBuilder();
        String part = argument.substring(1);
        while (true) {
            for (int index = 0; index < part.length(); index++) {
                char character = part.charAt(index);
                if (character == '\\' && index + 1 < part.length()
                        && (part.charAt(index + 1) == '"' || part.charAt(index + 1) == '\\')) {
                    prompt.append(part.charAt(++index));
                } else if (character == '"') {
                    if (!part.substring(index + 1).isBlank()) {
                        throw new IllegalArgumentException("Unexpected text after closing quote");
                    }
                    return prompt.toString();
                } else {
                    prompt.append(character);
                }
            }
            part = input.readLine();
            if (part == null) throw new IllegalArgumentException("Unclosed quote");
            prompt.append('\n');
        }
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
