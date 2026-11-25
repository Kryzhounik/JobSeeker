package com.seeker.tools.cli;

import java.io.BufferedReader;
import java.io.IOException;

/** Session grammar: a command followed by one optional, possibly multiline text argument. */
final class CliSessionInput {
    private final BufferedReader input;

    CliSessionInput(BufferedReader input) {
        this.input = input;
    }

    String[] readCommand() throws IOException {
        String line;
        do {
            line = input.readLine();
            if (line == null) return null;
        } while (line.isBlank());
        if (line.strip().equals("stop")) return null;
        String commandLine = line.stripLeading();
        int separator = 0;
        while (separator < commandLine.length()
                && !Character.isWhitespace(commandLine.charAt(separator))) separator++;
        String command = commandLine.substring(0, separator);
        String argument = commandLine.substring(separator).stripLeading();
        if (argument.isEmpty()) return new String[] {command};
        return new String[] {command, "--", readText(argument)};
    }

    private String readText(String argument) throws IOException {
        if (!argument.startsWith("\"")) return argument;
        StringBuilder text = new StringBuilder();
        String part = argument.substring(1);
        while (!appendQuotedPart(part, text)) {
            part = input.readLine();
            if (part == null) throw new IllegalArgumentException("Unclosed quote");
            text.append('\n');
        }
        return text.toString();
    }

    private boolean appendQuotedPart(String part, StringBuilder text) {
        for (int index = 0; index < part.length(); index++) {
            char character = part.charAt(index);
            if (character == '\\' && index + 1 < part.length()
                    && (part.charAt(index + 1) == '"' || part.charAt(index + 1) == '\\')) {
                text.append(part.charAt(++index));
                continue;
            }
            if (character == '"') {
                if (!part.substring(index + 1).isBlank())
                    throw new IllegalArgumentException("Unexpected text after closing quote");
                return true;
            }
            text.append(character);
        }
        return false;
    }
}
