package com.seeker.llm;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** A single context serving sequential JSON-line commands on standard input. */
public final class LlmCli {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static void main(String[] args) {
        try {
            if (args.length > 1) throw new IllegalArgumentException("Usage: java -jar LLM/llm.jar [project-root]");
            Path root = args.length == 1 ? Path.of(args[0]).toAbsolutePath().normalize() : findProjectRoot();
            var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            var output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
            run(root, input, output);
        } catch (Exception failure) {
            System.err.println("LLM CLI failed: " + failure.getMessage());
            System.exit(1);
        }
    }

    static void run(Path root, BufferedReader input, PrintWriter output) throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(Path.class, () -> root);
            context.register(LlmConfiguration.class);
            context.refresh();
            var service = context.getBean(LlmService.class);
            try {
                reply(output, Map.of("status", "ready", "pid", ProcessHandle.current().pid()));
                String line;
                while ((line = input.readLine()) != null) {
                    if (line.isBlank()) continue;
                    try {
                        Command command = JSON.readValue(line, Command.class);
                        if (command == null) throw new IllegalArgumentException("Expected a command object");
                        if ("stop".equals(command.command())) break;
                        if (!"generate".equals(command.command())) {
                            throw new IllegalArgumentException("Expected command: generate or stop");
                        }
                        reply(output, Map.of("status", "ok", "text", service.generate(command.prompt())));
                    } catch (RuntimeException failure) {
                        reply(output, Map.of("status", "error", "error", failure.getMessage() == null
                                ? failure.getClass().getSimpleName() : failure.getMessage()));
                    }
                }
            } finally {
                // Explicit close makes an unload failure visible; Spring also owns normal lifecycle cleanup.
                service.close();
            }
        }
        reply(output, Map.of("status", "stopped"));
    }

    private static void reply(PrintWriter output, Map<String, ?> response) {
        output.println(JSON.writeValueAsString(response));
        output.flush();
    }

    private static Path findProjectRoot() throws Exception {
        Path location = Path.of(LlmCli.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        for (Path path = Files.isDirectory(location) ? location : location.getParent(); path != null;
                path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("LLM/pom.xml"))) return path;
        }
        throw new IllegalStateException("Cannot locate project root; supply it as the optional argument");
    }

    record Command(String command, String prompt) {}
}
