package com.seeker.easyreasoning;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

/** Reusable plain-text CLI handler; the caller owns the API and its lifecycle. */
public final class EasyReasoningCli {

    public static int run(EasyReasoningApi api, BufferedReader input, PrintWriter output, PrintWriter errors)
            throws IOException {
        boolean hadErrors = false;
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
                String response = "generate".equals(command) ? api.generate(prompt)
                        : api.cleanVacancy(prompt);
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

}
