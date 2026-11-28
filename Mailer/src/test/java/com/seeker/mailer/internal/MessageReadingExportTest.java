package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.angus.mail.util.ReadableMime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class MessageReadingExportTest {
    @TempDir Path root;
    private String id;

    private ReadingStore setup(byte[] raw) throws Exception {
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG);
        id = new MessageSelector(MessageSelector.accountScope(MailSettings.load(root)), "INBOX", 11, 22).encode();
        return new ReadingStore(new RawMessage(raw));
    }

    private byte[] fixture(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/mail/" + name + ".eml")) {
            return java.util.Objects.requireNonNull(stream).readAllBytes();
        }
    }

    private ImapMailAccess access(ReadingStore store) { return new ImapMailAccess(root, session -> store); }

    @ParameterizedTest
    @ValueSource(strings = {"text/html", "text/plain"})
    void readsNamedInlineBodyInRelatedMultipartButExcludesAttachments(String contentType) throws Exception {
        String body = contentType.equals("text/html") ? "<p>Inline body</p>" : "Inline body";
        byte[] raw = ("MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=related\r\n\r\n"
                + "--related\r\nContent-Type: " + contentType + "; charset=UTF-8\r\n"
                + "Content-Disposition: inline; filename=body.html\r\n\r\n" + body + "\r\n"
                + "--related\r\nContent-Type: " + contentType + "; charset=UTF-8\r\n"
                + "Content-Disposition: attachment; filename=attachment.html\r\n\r\nExcluded attachment\r\n"
                + "--related\r\nContent-Type: text/plain; name=notes.txt\r\n\r\nExcluded named file\r\n"
                + "--related\r\nContent-Type: image/png\r\nContent-Disposition: inline; filename=image.png\r\n"
                + "Content-Transfer-Encoding: base64\r\n\r\nAAECAw==\r\n--related--\r\n")
                .getBytes(StandardCharsets.UTF_8);
        var store = setup(raw);
        var message = access(store).readMessage("INBOX", id);
        assertEquals(List.of(body), (contentType.equals("text/html") ? message.htmlBodies() : message.plainTextBodies())
                .stream().map(String::strip).toList());
        assertEquals(List.of(), contentType.equals("text/html") ? message.plainTextBodies() : message.htmlBodies());
        assertFalse(store.message.isSet(Flags.Flag.SEEN));
        assertClosed(store);
    }

    @ParameterizedTest
    @ValueSource(strings = {"synthetic-plain", "synthetic-html", "synthetic-multipart", "synthetic-latin", "synthetic-unsupported"})
    void readsGenericFixturesWithoutRecommendationClassification(String fixture) throws Exception {
        var store = setup(fixture(fixture));
        var message = access(store).readMessage("INBOX", id);
        assertEquals("INBOX", message.folder());
        assertEquals(id, message.summary().id());
        assertFalse(message.headers().isEmpty());
        if (fixture.equals("synthetic-html") || fixture.equals("synthetic-unsupported")) {
            assertEquals(1, message.htmlBodies().size());
            assertEquals(List.of(), message.plainTextBodies());
            assertTrue(message.htmlBodies().get(0).contains("<"));
        } else if (fixture.equals("synthetic-multipart")) {
            assertEquals(1, message.plainTextBodies().size());
            assertEquals(1, message.htmlBodies().size());
            assertTrue(message.plainTextBodies().get(0).contains("https://www.linkedin.com/jobs/view/12345/"));
            assertFalse(message.plainTextBodies().toString().contains("99999"));
            assertFalse(message.plainTextBodies().toString().contains("88888"));
        } else {
            assertEquals(1, message.plainTextBodies().size());
            assertEquals(List.of(), message.htmlBodies());
        }
        if (fixture.equals("synthetic-latin")) {
            assertEquals("Café", message.summary().subject());
            assertTrue(message.plainTextBodies().get(0).contains("Café and piñata"));
            assertTrue(message.headers().stream().anyMatch(header -> header.name().equalsIgnoreCase("Subject")
                    && header.decodedValue().equals("Café") && !header.value().equals("Café")));
        }
        assertFalse(store.message.isSet(Flags.Flag.SEEN));
        assertClosed(store);
    }

    @Test
    void missingBodySucceedsWithoutInventingHtmlOrText() throws Exception {
        var store = setup("From: fixture@example.invalid\r\nContent-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        var message = access(store).readMessage("INBOX", id);
        assertEquals(List.of(), message.plainTextBodies());
        assertEquals(List.of(), message.htmlBodies());
    }

    @Test
    void rawExportPreservesExactOctetsRepeatedHeadersFoldingBoundariesEncodingAndAttachments() throws Exception {
        byte[] raw = ("Received: first\r\nReceived: second\r\nX-Folded: first\r\n\tsecond\r\n"
                + "Content-Type: multipart/mixed; boundary=original-boundary\r\nMIME-Version: 1.0\r\n\r\n"
                + "preamble\r\n--original-boundary\r\nContent-Type: text/plain; charset=ISO-8859-1\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n\r\nCaf=E9\r\n"
                + "--original-boundary\r\nContent-Type: application/octet-stream\r\n"
                + "Content-Disposition: attachment; filename=payload.bin\r\nContent-Transfer-Encoding: base64\r\n\r\n"
                + "AAECA//+\r\n--original-boundary--\r\nepilogue").getBytes(StandardCharsets.US_ASCII);
        var store = setup(raw);
        var output = root.resolve("sample.eml");
        var exported = access(store).exportMessage("INBOX", id, output);
        assertEquals(raw.length, exported.byteCount());
        assertEquals(output.toAbsolutePath().toString(), exported.output());
        assertArrayEquals(raw, Files.readAllBytes(output));
        try (var input = Files.newInputStream(output)) {
            var parsed = new MimeMessage(Session.getInstance(new java.util.Properties()), input);
            assertEquals(2, parsed.getHeader("Received").length);
            var multi = (Multipart) parsed.getContent();
            assertEquals(2, multi.getCount());
            assertEquals("Café", ((String) multi.getBodyPart(0).getContent()).strip());
            assertArrayEquals(new byte[]{0, 1, 2, 3, (byte) 255, (byte) 254}, multi.getBodyPart(1).getInputStream().readAllBytes());
        }
        assertTrue(store.message.rawClosed);
        assertFalse(store.message.isSet(Flags.Flag.SEEN));
        assertClosed(store);
    }

    @Test
    void rawExportStillWorksWhenBodyDecodingFails() throws Exception {
        byte[] raw = "Subject: Private\r\nContent-Type: text/plain; charset=unknown-fixture-charset\r\n\r\nbody".getBytes(StandardCharsets.US_ASCII);
        var broken = setup(raw);
        assertThrows(MailException.class, () -> access(broken).readMessage("INBOX", id));
        assertClosed(broken);
        var export = setup(raw);
        var target = root.resolve("undecodable.eml");
        access(export).exportMessage("INBOX", id, target);
        assertArrayEquals(raw, Files.readAllBytes(target));
        assertClosed(export);
    }

    @Test
    void exportNeverOverwritesAndChecksOutputBeforeConnecting() throws Exception {
        var store = setup(fixture("synthetic-plain"));
        var output = root.resolve("existing.eml");
        Files.writeString(output, "preserve");
        assertThrows(MailException.class, () -> access(store).exportMessage("INBOX", id, output));
        assertEquals("preserve", Files.readString(output));
        assertFalse(store.connected);
        assertThrows(MailException.class, () -> access(store).exportMessage("INBOX", id, root.resolve("absent/sample.eml")));
        assertFalse(store.connected);
        assertThrows(MailException.class, () -> access(store).exportMessage("INBOX", id, root.resolve("wrong.txt")));
    }

    @Test
    void failedTransferLeavesNoPublishedOrTemporaryExport() throws Exception {
        var store = setup(fixture("synthetic-multipart"));
        store.message.failStream = true;
        var target = root.resolve("partial.eml");
        assertThrows(MailException.class, () -> access(store).exportMessage("INBOX", id, target));
        assertFalse(Files.exists(target));
        try (var files = Files.list(root)) { assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".mailer-"))); }
        assertClosed(store);
        assertTrue(store.message.rawClosed);
    }

    @Test
    void storeCloseFailureDoesNotPublishAnExport() throws Exception {
        var store = setup(fixture("synthetic-plain"));
        store.closeFailure = new MessagingException("fixture close failed");
        var target = root.resolve("close-failed.eml");
        var error = assertThrows(MailException.class, () -> access(store).exportMessage("INBOX", id, target));
        assertSame(store.closeFailure, error.getCause());
        assertFalse(Files.exists(target));
    }

    @Test
    void wrongScopeStaleAndMissingSelectorsCannotReadOrExportOtherMail() throws Exception {
        var wrongFolder = setup(fixture("synthetic-plain"));
        assertThrows(MailException.class, () -> access(wrongFolder).readMessage("Archive", id));
        assertFalse(wrongFolder.connected);
        var stale = setup(fixture("synthetic-plain"));
        stale.folder.validity = 12;
        assertThrows(MailException.class, () -> access(stale).readMessage("INBOX", id));
        assertClosed(stale);
        var missing = setup(fixture("synthetic-plain"));
        missing.folder.missing = true;
        assertThrows(MailException.class, () -> access(missing).exportMessage("INBOX", id, root.resolve("missing.eml")));
        assertFalse(Files.exists(root.resolve("missing.eml")));
        assertClosed(missing);
    }

    private void assertClosed(ReadingStore store) {
        assertTrue(store.closed);
        assertTrue(store.folder.closed);
        assertFalse(store.folder.open);
    }

    private static final class RawMessage extends MimeMessage implements ReadableMime {
        final byte[] raw;
        boolean rawClosed, failStream;
        RawMessage(byte[] raw) throws MessagingException {
            super(Session.getInstance(new java.util.Properties()), new ByteArrayInputStream(raw));
            this.raw = raw;
        }
        @Override public InputStream getMimeStream() {
            return new ByteArrayInputStream(raw) {
                @Override public synchronized int read(byte[] bytes, int offset, int length) {
                    if (failStream) { throw new java.io.UncheckedIOException(new IOException("fixture raw failure")); }
                    return super.read(bytes, offset, length);
                }
                @Override public long transferTo(OutputStream out) throws IOException {
                    if (failStream) { out.write(raw, 0, 3); throw new IOException("fixture raw failure"); }
                    return super.transferTo(out);
                }
                @Override public void close() { rawClosed = true; }
            };
        }
        @Override public void writeTo(OutputStream output) { throw new AssertionError("Must not reserialize export"); }
        @Override public void setFlags(Flags flags, boolean value) { throw new AssertionError("Must not change flags"); }
    }

    private static final class ReadingStore extends ImapMailAccessTest.StubStore {
        final RawMessage message;
        final ReadingFolder folder = new ReadingFolder(this);
        boolean connected;
        ReadingStore(RawMessage message) { this.message = message; }
        @Override protected boolean protocolConnect(String host, int port, String user, String password) {
            connected = true;
            return true;
        }
        @Override public Folder getFolder(String name) { assertEquals("INBOX", name); return folder; }
    }

    private static final class ReadingFolder extends ImapMailAccessTest.StubFolder implements UIDFolder {
        final ReadingStore owner;
        boolean open, closed, missing;
        long validity = 11;
        ReadingFolder(ReadingStore owner) { super(owner, "INBOX", null, HOLDS_MESSAGES, '/'); this.owner = owner; }
        @Override public boolean exists() { return true; }
        @Override public void open(int mode) { assertEquals(READ_ONLY, mode); this.mode = mode; open = true; }
        @Override public boolean isOpen() { return open; }
        @Override public void close(boolean expunge) { assertFalse(expunge); open = false; closed = true; }
        @Override public long getUIDValidity() { return validity; }
        @Override public long getUIDNext() { return 23; }
        @Override public long getUID(Message message) { assertSame(owner.message, message); return 22; }
        @Override public Message getMessageByUID(long uid) { return uid == 22 && !missing ? owner.message : null; }
        @Override public Message[] getMessagesByUID(long start, long end) { throw new AssertionError("Only selected message"); }
        @Override public Message[] getMessagesByUID(long[] uids) { throw new AssertionError("Only selected message"); }
    }
}
