package com.seeker.mailer;

import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageContentCliTest {
    private static final String ID = "v1." + "a".repeat(64) + ".SU5CT1g.1.2";
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
    private final PrintStream stdout = new PrintStream(output, true, StandardCharsets.UTF_8);
    private final PrintStream stderr = new PrintStream(errors, true, StandardCharsets.UTF_8);

    private int run(String[] args, MailerCli.MessageReader reader, MailerCli.MessageExporter exporter) {
        return MailerCli.run(args, stdout, stderr,
                () -> { fail("Must not list folders"); return null; },
                folder -> { fail("Must not list headers"); return null; }, reader, exporter,
                () -> { fail("Must not scan recommendations"); return null; });
    }

    @Test
    void rejectsMalformedSyntaxSelectorsAndPathsBeforeAllMailOperations() {
        for (String[] args : new String[][]{
                {"message"}, {"message", "--folder", "INBOX", "--id"},
                {"message", "--folder", "INBOX", "--id", "broken"},
                {"message", "--folder", "Archive", "--id", ID},
                {"message", "--folder", "INBOX", "--id", ID, "extra"},
                {"message", "--folder", "INBOX", "--wrong", ID},
                {"export", "--folder", "INBOX", "--id", ID},
                {"export", "--folder", "INBOX", "--id", ID, "--output", ""},
                {"export", "--folder", "INBOX", "--id", ID, "--output", "file.html"},
                {"export", "--folder", "INBOX", "--id", ID, "--output", "\0.eml"},
                {"export", "--folder", "INBOX", "--id", ID, "--output", "file.eml", "extra"}}) {
            assertEquals(2, run(args,
                    (folder, id) -> { fail("Must validate before reading"); return null; },
                    (folder, id, path) -> { fail("Must validate before exporting"); return null; }));
        }
        assertEquals("", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void readUsesOneSharedOperationAndKeepsHtmlAndTextSeparateInUtf8Json() {
        var calls = new AtomicInteger();
        assertEquals(0, run(new String[]{"message", "--folder", "INBOX", "--id", ID}, (folder, id) -> {
            calls.incrementAndGet();
            assertEquals("INBOX", folder);
            assertEquals(ID, id);
            return new MailMessageContent(folder, new MailMessageHeader(id, List.of(), null, null, null, false),
                    List.of(new MailHeader("Subject", "Тема", "Тема")), List.of("Привет"), List.of("<p>Привет</p>"));
        }, (folder, id, path) -> { fail("Must not export"); return null; }));
        assertEquals(1, calls.get());
        var json = JsonParser.parseString(output.toString(StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("Привет", json.getAsJsonArray("plainTextBodies").get(0).getAsString());
        assertEquals("<p>Привет</p>", json.getAsJsonArray("htmlBodies").get(0).getAsString());
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
    }

    @Test
    void exportDelegatesOnceWithExactPathAndPrintsExportMetadata() {
        var calls = new AtomicInteger();
        assertEquals(0, run(new String[]{"export", "--folder", "INBOX", "--id", ID, "--output", "sample.eml"},
                (folder, id) -> { fail("Export must not decode"); return null; }, (folder, id, path) -> {
                    calls.incrementAndGet();
                    assertEquals("INBOX", folder);
                    assertEquals(ID, id);
                    assertEquals(Path.of("sample.eml"), path);
                    return new MailExport(folder, id, path.toString(), 42);
                }));
        assertEquals(1, calls.get());
        var json = JsonParser.parseString(output.toString(StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(42, json.get("byteCount").getAsLong());
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
    }

    @Test
    void readAndExportFailuresUseCodeOneAndLeaveStdoutEmpty() {
        assertEquals(1, run(new String[]{"message", "--folder", "INBOX", "--id", ID},
                (folder, id) -> { throw new MailException("Cannot decode message", new Exception("private")); },
                (folder, id, path) -> null));
        assertEquals(1, run(new String[]{"export", "--folder", "INBOX", "--id", ID, "--output", "file.eml"},
                (folder, id) -> null,
                (folder, id, path) -> { throw new MailException("Cannot write export", new Exception("private")); }));
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertFalse(errors.toString(StandardCharsets.UTF_8).contains("private"));
    }
}
