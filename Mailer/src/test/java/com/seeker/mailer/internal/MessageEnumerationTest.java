package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import com.seeker.mailer.MailMessageListing;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class MessageEnumerationTest {
    @TempDir Path root;

    private MessagesStore store(int count) throws Exception {
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG);
        var store = new MessagesStore();
        for (int i = 0; i < count; i++) {
            store.folder.messages.add(new HeaderMessage(i + 100L, i + 1, i % 2 == 0));
        }
        return store;
    }

    private MailMessageListing list(MessagesStore store) throws MailException {
        return new ImapMailAccess(root, session -> store).listMessages("INBOX");
    }

    @Test
    void completeMixedMailboxSpansBatchesWithoutBodiesFiltersOrFlagChanges() throws Exception {
        var store = store(451);
        MailMessageListing listing = list(store);
        assertEquals(451, listing.messageCount());
        assertEquals(451, listing.messages().size());
        assertEquals(List.of(200, 200, 51), store.folder.batches);
        assertEquals(451, listing.messages().stream().map(header -> header.id()).distinct().count());
        assertEquals(226, listing.messages().stream().filter(header -> header.seen()).count());
        assertEquals("Fixture <fixture@example.invalid>", listing.messages().get(0).senders().get(0));
        assertEquals("Тема", listing.messages().get(0).subject());
        assertEquals(Instant.parse("2026-10-21T10:00:00Z"), listing.messages().get(0).receivedAt());
        assertEquals("<fixture-100@example.invalid>", listing.messages().get(0).messageId());
        for (int i = 0; i < store.folder.messages.size(); i++) {
            assertEquals(i % 2 == 0, store.folder.messages.get(i).isSet(Flags.Flag.SEEN));
        }
        assertClosed(store);
        assertThrows(UnsupportedOperationException.class, () -> listing.messages().clear());
    }

    @Test
    void emptyFolderIsSuccessfulAndStillClosedWithoutExpunge() throws Exception {
        var store = store(0);
        assertEquals(List.of(), list(store).messages());
        assertEquals(List.of(), store.folder.batches);
        assertClosed(store);
    }

    @Test
    void unavailableHeadersRemainNullAndMissingSenderIsAnEmptyList() throws Exception {
        var store = store(1);
        HeaderMessage message = store.folder.messages.get(0);
        message.removeHeader("From");
        message.removeHeader("Subject");
        message.removeHeader("Message-ID");
        message.received = null;
        var header = list(store).messages().get(0);
        assertEquals(List.of(), header.senders());
        assertNull(header.subject());
        assertNull(header.messageId());
        assertNull(header.receivedAt());
    }

    @Test
    void selectorsSurviveReconnectAndSequenceRenumbering() throws Exception {
        var first = store(2);
        var initial = list(first);
        var second = store(2);
        second.folder.messages.get(0).renumber(500);
        second.folder.messages.get(1).renumber(999);
        var repeated = list(second);
        assertEquals(initial.messages().stream().map(header -> header.id()).toList(),
                repeated.messages().stream().map(header -> header.id()).toList());
        MessageSelector selector = MessageSelector.decode(initial.messages().get(0).id());
        assertSame(second.folder.messages.get(0), selector.resolve(selector.account(), "INBOX", second.folder));
    }

    @Test
    void staleMissingAndWrongScopeSelectorsNeverSelectAnotherMessage() throws Exception {
        var store = store(2);
        MessageSelector selector = MessageSelector.decode(list(store).messages().get(0).id());
        store.folder.validity++;
        assertThrows(MailException.class, () -> selector.resolve(selector.account(), "INBOX", store.folder));
        store.folder.validity--;
        store.folder.messages.remove(0);
        store.folder.messages.get(0).renumber(1);
        assertThrows(MailException.class, () -> selector.resolve(selector.account(), "INBOX", store.folder));
        assertThrows(MailException.class, () -> selector.resolve("b".repeat(64), "INBOX", store.folder));
        assertThrows(MailException.class, () -> selector.resolve(selector.account(), "Archive", store.folder));
    }

    @Test
    void passwordRotationDoesNotChangeAccountScopeButAnotherAccountDoes() throws Exception {
        store(0);
        String scope = MessageSelector.accountScope(MailSettings.load(root));
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG.replace("abcd efgh ijkl mnop", "ponm lkji hgfe dcba"));
        assertEquals(scope, MessageSelector.accountScope(MailSettings.load(root)));
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG.replace("fixture@", "other@"));
        assertNotEquals(scope, MessageSelector.accountScope(MailSettings.load(root)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "v2.bad.folder.1.2", "v1.bad.folder.1.2",
            "v1.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.SU5CT1g.0.2",
            "v1.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.SU5CT1g.1.4294967296"})
    void rejectsMalformedOrOutOfRangeSelectors(String id) {
        assertThrows(MailException.class, () -> MessageSelector.decode(id));
    }

    @Test
    void folderNameRoundTripsWithoutNormalizingUnicodeOrHierarchy() throws Exception {
        var selector = new MessageSelector("a".repeat(64), "Папка/Новая. почта", 12, 987654321L);
        assertEquals(selector, MessageSelector.decode(selector.encode()));
    }

    @Test
    void laterBatchFailureDoesNotReturnPartialResultAndClosesEverything() throws Exception {
        var store = store(201);
        store.folder.failBatch = 2;
        var error = assertThrows(MailException.class, () -> list(store));
        assertInstanceOf(MessagingException.class, error.getCause());
        assertClosed(store);
    }

    @Test
    void countChangesAreErrorsRatherThanTruncatedSuccess() throws Exception {
        var store = store(2);
        store.folder.changeCount = true;
        assertThrows(MailException.class, () -> list(store));
        assertClosed(store);
    }

    @Test
    void duplicateOrMissingUidsAreErrors() throws Exception {
        var duplicate = store(2);
        duplicate.folder.messages.get(1).uid = duplicate.folder.messages.get(0).uid;
        assertThrows(MailException.class, () -> list(duplicate));
        assertClosed(duplicate);
        var missing = store(1);
        missing.folder.messages.get(0).uid = -1;
        assertThrows(MailException.class, () -> list(missing));
        assertClosed(missing);
    }

    @Test
    void missingFolderAndNonMessageContainerAreVisibleFailures() throws Exception {
        var absent = store(1);
        absent.folder.exists = false;
        assertThrows(MailException.class, () -> list(absent));
        assertTrue(absent.closed);
        assertFalse(absent.folder.opened);
        var container = store(1);
        container.folder.messageFolder = false;
        assertThrows(MailException.class, () -> list(container));
        assertFalse(container.folder.opened);
        assertTrue(container.closed);
    }

    @Test
    void rejectsBlankFolderBeforeSettingsOrConnection() {
        var access = new ImapMailAccess(root, session -> { fail("Must not connect"); return null; });
        assertThrows(IllegalArgumentException.class, () -> access.listMessages(" "));
        assertThrows(IllegalArgumentException.class, () -> access.listMessages(null));
    }

    private void assertClosed(MessagesStore store) {
        assertTrue(store.closed);
        assertTrue(store.folder.closed);
        assertFalse(store.folder.opened);
        assertFalse(store.folder.expunge);
    }

    private static final class MessagesStore extends ImapMailAccessTest.StubStore {
        final MessagesFolder folder = new MessagesFolder(this);
        @Override public Folder getFolder(String name) {
            assertEquals("INBOX", name);
            return folder;
        }
    }

    private static final class MessagesFolder extends ImapMailAccessTest.StubFolder implements UIDFolder {
        final List<HeaderMessage> messages = new ArrayList<>();
        final List<Integer> batches = new ArrayList<>();
        long validity = 12345;
        boolean opened, closed, expunge, changeCount;
        boolean exists = true, messageFolder = true;
        int countCalls, failBatch;
        MessagesFolder(Store store) { super(store, "INBOX", null, HOLDS_MESSAGES, '/'); }
        @Override public boolean exists() { return exists; }
        @Override public int getType() { return messageFolder ? HOLDS_MESSAGES : HOLDS_FOLDERS; }
        @Override public void open(int mode) {
            assertEquals(READ_ONLY, mode);
            this.mode = mode;
            opened = true;
        }
        @Override public boolean isOpen() { return opened; }
        @Override public void close(boolean expunge) {
            this.expunge = expunge;
            assertFalse(expunge);
            closed = true;
            opened = false;
        }
        @Override public int getMessageCount() {
            return messages.size() + (changeCount && ++countCalls > 1 ? 1 : 0);
        }
        @Override public void fetch(Message[] batch, FetchProfile profile) throws MessagingException {
            assertTrue(profile.contains(FetchProfile.Item.ENVELOPE));
            assertTrue(profile.contains(FetchProfile.Item.FLAGS));
            assertTrue(profile.contains(UIDFolder.FetchProfileItem.UID));
            assertArrayEquals(new String[]{"Message-ID"}, profile.getHeaderNames());
            assertEquals(3, profile.getItems().length);
            batches.add(batch.length);
            if (batches.size() == failBatch) { throw new MessagingException("fixture FETCH failed"); }
        }
        @Override public long getUIDValidity() { return validity; }
        @Override public long getUIDNext() { return 1000; }
        @Override public long getUID(Message message) { return ((HeaderMessage) message).uid; }
        @Override public Message getMessageByUID(long uid) {
            return messages.stream().filter(message -> message.uid == uid).findFirst().orElse(null);
        }
        @Override public Message[] getMessagesByUID(long start, long end) {
            assertEquals(1, start);
            assertEquals(LASTUID, end);
            return messages.toArray(Message[]::new);
        }
        @Override public Message[] getMessagesByUID(long[] uids) {
            return Arrays.stream(uids).mapToObj(this::getMessageByUID).toArray(Message[]::new);
        }
    }

    private static final class HeaderMessage extends MimeMessage {
        long uid;
        Date received = Date.from(Instant.parse("2026-10-21T10:00:00Z"));
        HeaderMessage(long uid, int number, boolean seen) throws Exception {
            super(Session.getInstance(new java.util.Properties()));
            this.uid = uid;
            renumber(number);
            setFrom(new InternetAddress("fixture@example.invalid", "Fixture", "UTF-8"));
            setSubject("Тема", "UTF-8");
            setHeader("Message-ID", "<fixture-" + uid + "@example.invalid>");
            super.setFlags(new Flags(Flags.Flag.SEEN), seen);
        }
        void renumber(int number) { setMessageNumber(number); }
        @Override public Date getReceivedDate() { return received; }
        @Override public Object getContent() { throw new AssertionError("Must not fetch bodies"); }
        @Override public java.io.InputStream getInputStream() { throw new AssertionError("Must not fetch bodies"); }
        @Override public void setFlags(Flags flags, boolean value) { throw new AssertionError("Must not modify flags"); }
        @Override public java.util.Enumeration<jakarta.mail.Header> getAllHeaders() {
            throw new AssertionError("Must not fetch all headers");
        }
    }
}
