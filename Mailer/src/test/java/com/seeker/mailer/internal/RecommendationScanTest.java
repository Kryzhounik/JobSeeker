package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RecommendationScanTest {
    @TempDir Path root;
    @BeforeEach void configure() throws Exception {
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG + """
                recommendations.folder=INBOX
                recommendations.originalRecipient=original@example.invalid
                recommendations.startAfterUid=425
                """);
    }
    private Path checkpoint() { return root.resolve("Data/mailer/recommendations.properties"); }
    private RecommendationSettings settings() throws Exception { return RecommendationSettings.load(root); }
    private String account() throws Exception { return MessageSelector.accountScope(MailSettings.load(root)); }
    private ImapMailAccess access(ScanStore store) { return new ImapMailAccess(root, session -> store); }
    private void seed(long uid) throws Exception { new RecommendationProgress(root).save(account(), settings(), 1, uid); }
    private long saved() throws Exception { return new RecommendationProgress(root).load(account(), settings(), 1); }

    @Test
    void completePassReturnsOrderedDistinctUrlsAndSkipsThemAfterReconstruction() throws Exception {
        var store = new ScanStore();
        var beforeRange = store.add(425, "recommendation-439");
        var first = store.add(426, "recommendation-439");
        first.seen();
        var facets = store.add(427, "turnitin-437");
        store.add(428, "luxoft-438");
        store.add(429, "intellias-432");
        var excluded = store.add(430, "luxoft-438");
        excluded.setHeader("X-LinkedIn-Template", "email_application_confirmation_with_nba_01");
        excluded.failContent = true;
        var expected = new LinkedHashSet<String>();
        for (String name : List.of("recommendation-439", "turnitin-437", "luxoft-438", "intellias-432")) expected.addAll(RecommendationParserTest.expected(name));
        assertEquals(List.copyOf(expected), access(store).getRecommendationUrls());
        assertEquals(430, saved());
        assertEquals(0, beforeRange.bodyReads);
        assertEquals(0, excluded.bodyReads);
        assertTrue(first.isSet(Flags.Flag.SEEN));
        assertFalse(facets.isSet(Flags.Flag.SEEN));
        assertTrue(store.closed && store.folder.closed);
        int oldReads = first.bodyReads;
        assertEquals(List.of(), access(store).getRecommendationUrls());
        assertEquals(oldReads, first.bodyReads);
        assertEquals(1, store.folder.rangeCalls); // Empty UID range is never sent on second connection.
    }

    @Test
    void failedMiddleMessageStopsPassKeepsOldCheckpointAndCanBeRetried() throws Exception {
        seed(425);
        byte[] old = Files.readAllBytes(checkpoint());
        var store = new ScanStore();
        var good = store.add(426, "luxoft-438");
        var broken = store.add(427, "luxoft-438");
        broken.failContent = true;
        var later = store.add(428, "intellias-432");
        assertThrows(MailException.class, () -> access(store).getRecommendationUrls());
        assertArrayEquals(old, Files.readAllBytes(checkpoint()));
        assertEquals(1, good.bodyReads);
        assertEquals(0, later.bodyReads);
        String log = Files.readString(root.resolve("Data/mailer/mailer.log"));
        assertTrue(log.contains("RECOMMENDATIONS_ERROR"));
        assertTrue(log.contains("Sanitized recommendation fixture"));
        assertTrue(store.closed && store.folder.closed);
        broken.failContent = false;
        assertFalse(access(store).getRecommendationUrls().isEmpty());
        assertEquals(2, good.bodyReads); // Successful earlier work is deliberately repeated.
        assertEquals(428, saved());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sender", "recipient", "forwardedTo", "forwardedFor", "template", "confirmation"})
    void rejectsNonmatchingHeadersWithoutReadingBodyButAdvancesSuccessfulPass(String mismatch) throws Exception {
        var store = new ScanStore();
        var message = store.add(426, "luxoft-438");
        switch (mismatch) {
            case "sender" -> message.setHeader("From", "LinkedIn <jobs-noreply@linkedin.com>");
            case "recipient" -> message.setHeader("To", "somebody@example.invalid");
            case "forwardedTo" -> message.removeHeader("X-Forwarded-To");
            case "forwardedFor" -> message.setHeader("X-Forwarded-For", "not-original@example.invalid fixture@example.invalid");
            case "template" -> message.setHeader("X-LinkedIn-Template", "unknown");
            case "confirmation" -> message.setHeader("X-LinkedIn-Template", "email_application_confirmation_with_nba_01");
        }
        message.failContent = true;
        assertEquals(List.of(), access(store).getRecommendationUrls());
        assertEquals(0, message.bodyReads);
        assertEquals(426, saved());
    }

    @Test
    void emptyMailboxAndUidGapsDoNotReprocessOldMessages() throws Exception {
        var empty = new ScanStore();
        assertEquals(List.of(), access(empty).getRecommendationUrls());
        assertEquals(425, saved());
        assertEquals(0, empty.folder.rangeCalls);
        var later = empty.add(500, "luxoft-438");
        assertEquals(1, access(empty).getRecommendationUrls().size());
        assertEquals(500, saved());
        assertEquals(1, later.bodyReads);
    }

    @Test
    void folderOrStoreCleanupFailureKeepsPreviousCheckpoint() throws Exception {
        seed(425);
        byte[] old = Files.readAllBytes(checkpoint());
        var store = new ScanStore();
        store.add(426, "luxoft-438");
        store.closeFailure = new MessagingException("fixture close failure");
        assertThrows(MailException.class, () -> access(store).getRecommendationUrls());
        assertArrayEquals(old, Files.readAllBytes(checkpoint()));
    }

    @Test
    void changedUidValidityOrCorruptStateCannotSilentlyResetProgress() throws Exception {
        seed(425);
        byte[] old = Files.readAllBytes(checkpoint());
        var store = new ScanStore();
        store.folder.validity = 2;
        store.add(426, "luxoft-438");
        assertThrows(MailException.class, () -> access(store).getRecommendationUrls());
        assertArrayEquals(old, Files.readAllBytes(checkpoint()));
        Files.writeString(checkpoint(), "malformed checkpoint");
        store.folder.validity = 1;
        assertThrows(MailException.class, () -> access(store).getRecommendationUrls());
        assertEquals("malformed checkpoint", Files.readString(checkpoint()));
    }

    @Test
    void retrievalFailureAndMissingInitialRangeAreVisibleWithoutCommitting() throws Exception {
        var store = new ScanStore();
        store.add(426, "luxoft-438");
        store.folder.failRetrieval = true;
        assertThrows(MailException.class, () -> access(store).getRecommendationUrls());
        assertFalse(Files.exists(checkpoint()));
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG + "recommendations.folder=INBOX\nrecommendations.originalRecipient=original@example.invalid\n");
        store.folder.failRetrieval = false;
        assertThrows(MailException.class, () -> access(store).getRecommendationUrls());
        assertFalse(Files.exists(checkpoint()));
    }

    @Test
    void ordinaryListingAndReadingDoNotConsumeRecommendations() throws Exception {
        var store = new ScanStore();
        store.add(426, "luxoft-438");
        var lowLevel = access(store);
        var listing = lowLevel.listMessages("INBOX");
        lowLevel.readMessage("INBOX", listing.messages().get(0).id());
        assertFalse(Files.exists(checkpoint()));
        assertEquals(1, lowLevel.getRecommendationUrls().size());
        assertEquals(426, saved());
    }

    private static final class ScanStore extends ImapMailAccessTest.StubStore {
        final ScanFolder folder = new ScanFolder(this);
        ScanMessage add(long uid, String fixture) throws Exception {
            try (var input = RecommendationScanTest.class.getResourceAsStream("/mail/recommendations/" + fixture + ".eml")) {
                var message = new ScanMessage(uid, input);
                folder.messages.add(message);
                return message;
            }
        }
        @Override public Folder getFolder(String name) { assertEquals("INBOX", name); return folder; }
    }

    private static final class ScanFolder extends ImapMailAccessTest.StubFolder implements UIDFolder {
        final List<ScanMessage> messages = new ArrayList<>();
        boolean open, closed, failRetrieval;
        int rangeCalls;
        long validity = 1;
        ScanFolder(Store store) { super(store, "INBOX", null, HOLDS_MESSAGES, '/'); }
        @Override public boolean exists() { return true; }
        @Override public void open(int mode) { assertEquals(READ_ONLY, mode); this.mode = mode; open = true; }
        @Override public boolean isOpen() { return open; }
        @Override public void close(boolean expunge) { assertFalse(expunge); open = false; closed = true; }
        @Override public long getUIDValidity() { return validity; }
        @Override public long getUIDNext() { return messages.stream().mapToLong(message -> message.uid).max().orElse(0) + 1; }
        @Override public long getUID(Message message) { return ((ScanMessage) message).uid; }
        @Override public Message getMessageByUID(long uid) { return messages.stream().filter(message -> message.uid == uid).findFirst().orElse(null); }
        @Override public Message[] getMessagesByUID(long first, long last) throws MessagingException {
            if (failRetrieval) throw new MessagingException("fixture retrieval failed");
            assertTrue(last == LASTUID || first <= last, "No reversed IMAP ranges");
            rangeCalls++;
            return messages.stream().filter(message -> message.uid >= first && (last == LASTUID || message.uid <= last)).toArray(Message[]::new);
        }
        @Override public Message[] getMessagesByUID(long[] ids) { return Arrays.stream(ids).mapToObj(this::getMessageByUID).toArray(Message[]::new); }
        @Override public int getMessageCount() { return messages.size(); }
        @Override public void fetch(Message[] batch, FetchProfile profile) { /* Header data already loaded by fixture. */ }
    }

    private static final class ScanMessage extends MimeMessage {
        final long uid;
        int bodyReads;
        boolean failContent;
        ScanMessage(long uid, java.io.InputStream input) throws MessagingException {
            super(Session.getInstance(new Properties()), input);
            this.uid = uid;
        }
        void seen() { flags.add(Flags.Flag.SEEN); }
        @Override public Object getContent() throws MessagingException, IOException {
            bodyReads++;
            if (failContent) throw new IOException("fixture parse failure");
            return super.getContent();
        }
        @Override public void setFlags(Flags flags, boolean value) { throw new AssertionError("Must not change server flags"); }
    }
}
