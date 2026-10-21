package com.seeker.mailer;

import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MailerCliTest {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
    private final PrintStream stdout = new PrintStream(output, true, StandardCharsets.UTF_8);
    private final PrintStream stderr = new PrintStream(errors, true, StandardCharsets.UTF_8);

    @Test
    void validatesAllSyntaxBeforeCallingMail() {
        for (String[] args : new String[][]{{}, {"unknown"}, {"folders", "extra"}, {"Folders"}, {"messages"}}) {
            assertEquals(2, run(args, stdout, stderr, () -> {
                fail("Invalid syntax must not call mail");
                return null;
            }));
        }
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Usage:"));
    }

    @Test
    void delegatesOnceAndPrintsUtf8JsonWithHierarchyAndEscapedNames() {
        var calls = new AtomicInteger();
        String name = "Входящие/\"Пример\"\n";
        assertEquals(0, run(new String[]{"folders"}, stdout, stderr, () -> {
            calls.incrementAndGet();
            return List.of(new MailFolder(name, name, "Входящие", "/", true, false));
        }));
        assertEquals(1, calls.get());
        var json = JsonParser.parseString(output.toString(StandardCharsets.UTF_8)).getAsJsonArray();
        assertEquals(name, json.get(0).getAsJsonObject().get("fullName").getAsString());
        assertTrue(json.get(0).getAsJsonObject().get("holdsMessages").getAsBoolean());
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
    }

    @Test
    void emptyListingIsSuccessfulJsonArray() {
        assertEquals(0, run(new String[]{"folders"}, stdout, stderr, List::of));
        assertEquals("[]", output.toString(StandardCharsets.UTF_8).strip());
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
    }

    @Test
    void errorsDoNotCorruptStdoutOrPrintProviderSecrets() {
        assertEquals(1, run(new String[]{"folders"}, stdout, stderr, () -> {
            throw new MailException("IMAP authentication failed", new Exception("private provider response"));
        }));
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertEquals("IMAP authentication failed", errors.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void outputFailureReturnsAnError() {
        var broken = new PrintStream(new OutputStream() {
            @Override public void write(int value) throws java.io.IOException {
                throw new java.io.IOException("fixture output failure");
            }
        });
        assertEquals(1, run(new String[]{"folders"}, broken, stderr, List::of));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Cannot write"));
    }

    private int run(String[] args, PrintStream out, PrintStream err, MailerCli.FolderSource source) {
        return runExisting(args, out, err, source, folder -> {
            fail("Folder command must not enumerate messages");
            return null;
        });
    }

    @Test
    void messageSyntaxIsValidatedBeforeEitherOperation() {
        for (String[] args : new String[][]{{"messages"}, {"messages", "--folder"},
                {"messages", "--folder", " "}, {"messages", "--folder", "INBOX", "extra"},
                {"messages", "--wrong", "INBOX"}, {"messages", "--folder", "--folder"},
                {"messages", "--folder", "INBOX\n"}}) {
            assertEquals(2, runExisting(args, stdout, stderr, () -> {
                fail("Must validate before mail"); return null;
            }, folder -> { fail("Must validate before mail"); return null; }));
        }
    }

    @Test
    void messagesDelegateOnceAndSerializeReceiptInstantAndMissingHeaders() {
        var calls = new AtomicInteger();
        var receipt = java.time.Instant.parse("2026-10-21T10:00:00Z");
        assertEquals(0, runExisting(new String[]{"messages", "--folder", "INBOX"}, stdout, stderr,
                () -> { fail("Must not list folders"); return null; }, folder -> {
                    calls.incrementAndGet();
                    assertEquals("INBOX", folder);
                    return new MailMessageListing(folder, 1, List.of(new MailMessageHeader(
                            "selector", List.of("fixture@example.invalid"), null, receipt, null, false)));
                }));
        assertEquals(1, calls.get());
        var json = JsonParser.parseString(output.toString(StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, json.get("messageCount").getAsInt());
        var header = json.getAsJsonArray("messages").get(0).getAsJsonObject();
        assertEquals(receipt.toString(), header.get("receivedAt").getAsString());
        assertTrue(header.get("subject").isJsonNull());
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
    }

    @Test
    void messageFailureLeavesStdoutEmpty() {
        assertEquals(1, runExisting(new String[]{"messages", "--folder", "INBOX"}, stdout, stderr,
                List::of, folder -> { throw new MailException("Enumeration failed"); }));
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertEquals("Enumeration failed", errors.toString(StandardCharsets.UTF_8).strip());
    }

    private int runExisting(String[] args, PrintStream out, PrintStream err,
                            MailerCli.FolderSource folders, MailerCli.MessageSource messages) {
        return MailerCli.run(args, out, err, folders, messages,
                (folder, id) -> { fail("Must not read a body"); return null; },
                (folder, id, path) -> { fail("Must not export a message"); return null; },
                () -> { fail("Must not scan recommendations"); return null; });
    }
}
