package com.seeker.mailer.internal;

import com.google.gson.JsonParser;
import com.seeker.mailer.MailException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MailLogTest {
    @TempDir Path root;

    @Test
    void appendsUtf8RecordsWithoutTurningSubjectNewlinesIntoForgedEvents() throws Exception {
        var log = new MailLog(root);
        assertFalse(Files.exists(root.resolve("Data")));
        log.write("HEADERS_READ", "id", "Тема\nFORGED", false, "Body not read");
        log.write("READ_COMPLETE", "id", "Тема", true, "Plain-text bodies: 1");
        var lines = Files.readAllLines(root.resolve("Data/mailer/mailer.log"));
        assertEquals(2, lines.size());
        var first = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        assertEquals("Тема\nFORGED", first.get("subject").getAsString());
        assertFalse(first.get("seen").getAsBoolean());
        assertEquals("READ_COMPLETE", JsonParser.parseString(lines.get(1)).getAsJsonObject().get("event").getAsString());
    }

    @Test
    void interpreterLogsFilteredExtractedAndFailedMessagesWithIdAndSubjectButNotBodies() throws Exception {
        var interpreter = new MessageInterpreter(root);
        interpreter.interpret(input("From: fixture@example.invalid\nSubject: Other mail\n\nprivate-body-token"), "other-id", null);
        interpreter.interpret(input("From: jobs@linkedin.com\nSubject: Jobs for you\n\n"
                + "https://www.linkedin.com/jobs/view/12345/"), "jobs-id", null);
        assertThrows(MailException.class, () -> interpreter.interpret(input(
                "From: jobs@linkedin.com\nSubject: Jobs for you\n\nunknown recommendation"), "failed-id", null));
        var entries = Files.readAllLines(root.resolve("Data/mailer/mailer.log")).stream()
                .map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
        assertTrue(entries.stream().anyMatch(entry -> entry.get("event").getAsString().equals("FILTERED")
                && entry.get("id").getAsString().equals("other-id") && entry.get("subject").getAsString().equals("Other mail")));
        assertTrue(entries.stream().anyMatch(entry -> entry.get("event").getAsString().equals("EXTRACTED")
                && entry.get("detail").getAsString().contains("https://www.linkedin.com/jobs/view/12345/")));
        assertTrue(entries.stream().anyMatch(entry -> entry.get("event").getAsString().equals("PARSE_ERROR")
                && entry.get("id").getAsString().equals("failed-id") && entry.get("subject").getAsString().equals("Jobs for you")
                && entry.get("detail").getAsString().contains("Unsupported LinkedIn recommendation format")));
        assertFalse(Files.readString(root.resolve("Data/mailer/mailer.log")).contains("private-body-token"));
    }

    @Test
    void failuresKeepTheirCauseWhenLogCannotBeWrittenAndDoNotExposeProviderResponses() throws Exception {
        var log = new MailLog(root);
        var original = new MailException("IMAP authentication failed", new IOException("password-or-provider-secret"));
        log.failure("ERROR", "id", "subject", null, original);
        String content = Files.readString(root.resolve("Data/mailer/mailer.log"));
        assertTrue(content.contains("IMAP authentication failed"));
        assertTrue(content.contains("IOException"));
        assertFalse(content.contains("password-or-provider-secret"));
        Path blockedRoot = root.resolve("blocked");
        Files.createDirectories(blockedRoot.resolve("Data/mailer/mailer.log"));
        new MailLog(blockedRoot).failure("ERROR", "id", "subject", null, original);
        assertEquals(1, original.getSuppressed().length);
        assertEquals("password-or-provider-secret", original.getCause().getMessage());
    }

    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
