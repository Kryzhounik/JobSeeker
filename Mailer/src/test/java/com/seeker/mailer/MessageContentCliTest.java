package com.seeker.mailer;

import com.google.gson.JsonParser;
import com.seeker.tools.cli.CliRunner;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageContentCliTest {
    private static final String ID = "v1." + "a".repeat(64) + ".SU5CT1g.1.2";
    private final Mailer api = mock(Mailer.class);
    private final StringWriter output = new StringWriter();
    private final StringWriter errors = new StringWriter();

    private int run(String... args) {
        return CliRunner.run(new MailerAdapter(api), new PrintWriter(output, true), new PrintWriter(errors, true), args);
    }

    @Test
    void rejectsMalformedSyntaxAndUnparseablePathsBeforeApiCall() {
        for (String[] args : new String[][]{
                {"message"}, {"message", "--folder", "INBOX", "--id"},
                {"message", "--folder", "INBOX", "--id", ID, "extra"},
                {"message", "--folder", "INBOX", "--wrong", ID},
                {"export", "--folder", "INBOX", "--id", ID},
                {"export", "--folder", "INBOX", "--id", ID, "--output", "\0.eml"},
                {"export", "--folder", "INBOX", "--id", ID, "--output", "file.eml", "extra"}}) {
            assertEquals(2, run(args));
        }
        verifyNoInteractions(api);
        assertEquals("", output.toString());
    }

    @Test
    void inputValuesReachApiAndItsValidationErrorsReturnOne() throws MailException {
        when(api.readMessage("INBOX", "broken")).thenThrow(new MailException("Invalid message selector"));
        when(api.readMessage("Archive", ID)).thenThrow(new MailException("Message selector belongs to another folder"));
        when(api.exportMessage("INBOX", ID, Path.of(""))).thenThrow(new MailException("Invalid export path"));
        when(api.exportMessage("INBOX", ID, Path.of("file.html"))).thenThrow(new MailException("Invalid export path"));

        assertEquals(1, run("message", "--folder", "INBOX", "--id", "broken"));
        assertEquals(1, run("message", "--folder", "Archive", "--id", ID));
        assertEquals(1, run("export", "--folder", "INBOX", "--id", ID, "--output", ""));
        assertEquals(1, run("export", "--folder", "INBOX", "--id", ID, "--output", "file.html"));

        verify(api).readMessage("INBOX", "broken");
        verify(api).readMessage("Archive", ID);
        verify(api).exportMessage("INBOX", ID, Path.of(""));
        verify(api).exportMessage("INBOX", ID, Path.of("file.html"));
        verifyNoMoreInteractions(api);
        assertEquals("", output.toString());
    }

    @Test
    void readKeepsBodiesSeparateAndAllowsReorderedOptions() throws MailException {
        when(api.readMessage("INBOX", ID)).thenReturn(new MailMessageContent("INBOX",
                new MailMessageHeader(ID, List.of(), null, null, null, false),
                List.of(new MailHeader("Subject", "Тема", "Тема")), List.of("Привет"), List.of("<p>Привет</p>")));
        assertEquals(0, run("message", "--id", ID, "--folder", "INBOX"));
        var json = JsonParser.parseString(output.toString()).getAsJsonObject();
        assertEquals("Привет", json.getAsJsonArray("plainTextBodies").get(0).getAsString());
        assertEquals("<p>Привет</p>", json.getAsJsonArray("htmlBodies").get(0).getAsString());
        verify(api).readMessage("INBOX", ID);
        verifyNoMoreInteractions(api);
        assertEquals("", errors.toString());
    }

    @Test
    void exportPassesTypedPathAndReturnsMetadata() throws MailException {
        when(api.exportMessage("INBOX", ID, Path.of("sample.eml")))
                .thenReturn(new MailExport("INBOX", ID, "sample.eml", 42));
        assertEquals(0, run("export", "--folder", "INBOX", "--id", ID, "--output", "sample.eml"));
        assertEquals(42, JsonParser.parseString(output.toString()).getAsJsonObject().get("byteCount").getAsLong());
        verify(api).exportMessage("INBOX", ID, Path.of("sample.eml"));
        verifyNoMoreInteractions(api);
    }

    @Test
    void readAndExportFailuresReturnOneWithoutPartialOutput() throws MailException {
        when(api.readMessage("INBOX", ID)).thenThrow(new MailException("Cannot decode message", new Exception("private")));
        when(api.exportMessage("INBOX", ID, Path.of("file.eml"))).thenThrow(new MailException("Cannot write export"));
        assertEquals(1, run("message", "--folder", "INBOX", "--id", ID));
        assertEquals(1, run("export", "--folder", "INBOX", "--id", ID, "--output", "file.eml"));
        assertEquals("", output.toString());
        assertFalse(errors.toString().contains("private"));
    }
}
