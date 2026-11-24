package com.seeker.mailer;

import com.google.gson.JsonParser;
import com.seeker.tools.cli.CliRunner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailerCliTest {
    private final Mailer api = mock(Mailer.class);
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
    private final PrintWriter stdout = new PrintWriter(output, true, StandardCharsets.UTF_8);
    private final PrintWriter stderr = new PrintWriter(errors, true, StandardCharsets.UTF_8);

    private int run(String... args) {
        return CliRunner.run(new MailerAdapter(api), stdout, stderr, args);
    }

    @Test
    void validatesSyntaxBeforeCallingMail() {
        for (String[] args : new String[][]{{}, {"unknown"}, {"folders", "extra"}, {"Folders"},
                {"messages"}, {"messages", "--folder"},
                {"messages", "--folder", "INBOX", "extra"}, {"messages", "--wrong", "INBOX"},
                {"messages", "--folder", "--folder"}}) {
            assertEquals(2, run(args));
        }
        verifyNoInteractions(api);
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Usage:"));
    }

    @Test
    void delegatesOnceAndPrintsUtf8Json() throws MailException {
        String name = "Входящие/\"Пример\"\n";
        when(api.listFolders()).thenReturn(List.of(new MailFolder(name, name, "Входящие", "/", true, false)));
        assertEquals(0, run("folders"));
        var json = JsonParser.parseString(output.toString(StandardCharsets.UTF_8)).getAsJsonArray();
        assertEquals(name, json.get(0).getAsJsonObject().get("fullName").getAsString());
        assertTrue(json.get(0).getAsJsonObject().get("holdsMessages").getAsBoolean());
        verify(api).listFolders();
        verifyNoMoreInteractions(api);
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
    }

    @Test
    void emptyListingIsSuccessfulJsonArray() throws MailException {
        when(api.listFolders()).thenReturn(List.of());
        assertEquals(0, run("folders"));
        assertEquals("[]", output.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void failuresLeaveStdoutEmptyAndDoNotPrintProviderCauses() throws MailException {
        when(api.listFolders()).thenThrow(new MailException("IMAP authentication failed", new Exception("private")));
        assertEquals(1, run("folders"));
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertEquals("IMAP authentication failed", errors.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void outputFailureReturnsAnError() throws MailException {
        when(api.listFolders()).thenReturn(List.of());
        var broken = new PrintWriter(new OutputStream() {
            @Override public void write(int value) throws IOException {
                throw new IOException("fixture output failure");
            }
        }, true, StandardCharsets.UTF_8);
        assertEquals(1, CliRunner.run(new MailerAdapter(api), broken, stderr, "folders"));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Cannot write"));
    }

    @Test
    void messagesSerializeReceiptInstantAndMissingHeaders() throws MailException {
        var receipt = Instant.parse("2026-10-21T10:00:00Z");
        when(api.listMessages("INBOX")).thenReturn(new MailMessageListing("INBOX", 1,
                List.of(new MailMessageHeader("selector", List.of("fixture@example.invalid"), null, receipt, null, false))));
        assertEquals(0, run("messages", "--folder", "INBOX"));
        var json = JsonParser.parseString(output.toString(StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, json.get("messageCount").getAsInt());
        var header = json.getAsJsonArray("messages").get(0).getAsJsonObject();
        assertEquals(receipt.toString(), header.get("receivedAt").getAsString());
        assertTrue(header.get("subject").isJsonNull());
        verify(api).listMessages("INBOX");
        verifyNoMoreInteractions(api);
    }

    @Test
    void messageListingFailureLeavesStdoutEmpty() throws MailException {
        when(api.listMessages("INBOX")).thenThrow(new MailException("Enumeration failed"));
        assertEquals(1, run("messages", "--folder", "INBOX"));
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertEquals("Enumeration failed", errors.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void folderValidationBelongsToApiAndReturnsOne() throws MailException {
        for (String folder : List.of(" ", "INBOX\n")) {
            when(api.listMessages(folder)).thenThrow(new MailException("Invalid folder name"));
            assertEquals(1, run("messages", "--folder", folder));
            verify(api).listMessages(folder);
        }
        verifyNoMoreInteractions(api);
        assertEquals("", output.toString(StandardCharsets.UTF_8));
    }
}
