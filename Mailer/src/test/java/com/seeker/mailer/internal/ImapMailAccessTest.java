package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import com.seeker.mailer.MailFolder;
import jakarta.mail.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ImapMailAccessTest {
    @TempDir Path root;

    @Test
    void listsEveryFolderWithVerbatimHierarchyAndCapabilitiesWithoutOpeningAnything() throws Exception {
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG);
        var store = new StubStore();
        var parent = new StubFolder(store, "[Gmail]", store.root, Folder.HOLDS_FOLDERS, '/');
        var nested = new StubFolder(store, "[Gmail]/Вся почта", parent, Folder.HOLDS_MESSAGES, '/');
        var inbox = new StubFolder(store, "INBOX", store.root, 3, '/');
        var flat = new StubFolder(store, "Flat", null, Folder.HOLDS_MESSAGES, '\0');
        store.root.children = new Folder[]{nested, inbox, parent, flat};
        var calls = new AtomicInteger();
        var access = new ImapMailAccess(root, session -> {
            calls.incrementAndGet();
            assertFalse(session.getDebug());
            assertEquals("true", session.getProperty("mail.imaps.ssl.checkserveridentity"));
            return store;
        });
        List<MailFolder> folders = access.listFolders();
        assertEquals(1, calls.get());
        assertEquals(List.of(
                new MailFolder("Flat", "Flat", null, null, true, false),
                new MailFolder("INBOX", "INBOX", "", "/", true, true),
                new MailFolder("[Gmail]", "[Gmail]", "", "/", false, true),
                new MailFolder("Вся почта", "[Gmail]/Вся почта", "[Gmail]", "/", true, false)), folders);
        assertThrows(UnsupportedOperationException.class, () -> folders.clear());
        assertEquals("*", store.root.pattern);
        assertEquals("imap.gmail.com", store.host);
        assertEquals(993, store.port);
        assertEquals("fixture@example.invalid", store.username);
        assertEquals("abcdefghijklmnop", store.password);
        assertTrue(store.closed);
    }

    @Test
    void emptyFolderListingSucceedsAndConnectionIsClosed() throws Exception {
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG);
        var store = new StubStore();
        assertEquals(List.of(), new ImapMailAccess(root, session -> store).listFolders());
        assertTrue(store.closed);
    }

    @Test
    void authenticationFailureIsVisibleWithCauseAndClosesStore() throws Exception {
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG);
        var store = new StubStore();
        store.connectFailure = new AuthenticationFailedException("private provider response");
        var error = assertThrows(MailException.class, () -> new ImapMailAccess(root, session -> store).listFolders());
        assertSame(store.connectFailure, error.getCause());
        assertTrue(error.getMessage().contains("authentication failed"));
        assertFalse(error.getMessage().contains("private"));
        assertTrue(store.closed);
    }

    @Test
    void connectionFailureIsVisibleAndClosesStore() throws Exception {
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG);
        var store = new StubStore();
        store.connectFailure = new MessagingException("fixture timeout");
        var error = assertThrows(MailException.class, () -> new ImapMailAccess(root, session -> store).listFolders());
        assertSame(store.connectFailure, error.getCause());
        assertTrue(store.closed);
    }

    @Test
    void failedListingReturnsNoPartialSuccessAndClosesStore() throws Exception {
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG);
        var store = new StubStore();
        store.root.listFailure = new MessagingException("fixture LIST failed");
        var error = assertThrows(MailException.class, () -> new ImapMailAccess(root, session -> store).listFolders());
        assertSame(store.root.listFailure, error.getCause());
        assertTrue(store.closed);
    }

    @Test
    void metadataFailureAndCloseFailurePreservePrimaryAndSuppressedCauses() throws Exception {
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG);
        var store = new StubStore();
        var folder = new StubFolder(store, "INBOX", store.root, 1, '/');
        store.root.children = new Folder[]{folder};
        folder.metadataFailure = new MessagingException("fixture metadata failure");
        store.closeFailure = new MessagingException("fixture close failure");
        var error = assertThrows(MailException.class, () -> new ImapMailAccess(root, session -> store).listFolders());
        assertSame(folder.metadataFailure, error.getCause());
        assertArrayEquals(new Throwable[]{store.closeFailure}, error.getCause().getSuppressed());
        assertTrue(store.closed);
    }

    @Test
    void closeFailureCannotLookLikeSuccessfulEmptyList() throws Exception {
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG);
        var store = new StubStore();
        store.closeFailure = new MessagingException("fixture close failure");
        var error = assertThrows(MailException.class, () -> new ImapMailAccess(root, session -> store).listFolders());
        assertSame(store.closeFailure, error.getCause());
    }

    @Test
    void storeCreationFailureIsAnOperationalError() throws Exception {
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG);
        var cause = new NoSuchProviderException("fixture provider");
        var error = assertThrows(MailException.class, () -> new ImapMailAccess(root, session -> { throw cause; }).listFolders());
        assertSame(cause, error.getCause());
    }

    /** A replaceable transport; every unintended folder operation fails the test. */
    static class StubStore extends Store {
        final StubFolder root = new StubFolder(this, "", null, Folder.HOLDS_FOLDERS, '/');
        String host, username, password;
        int port;
        boolean closed;
        MessagingException connectFailure, closeFailure;

        StubStore() { super(Session.getInstance(new java.util.Properties()), null); }
        @Override protected boolean protocolConnect(String host, int port, String user, String password) throws MessagingException {
            this.host = host; this.port = port; this.username = user; this.password = password;
            if (connectFailure != null) { throw connectFailure; }
            return true;
        }
        @Override public void close() throws MessagingException {
            closed = true;
            setConnected(false);
            if (closeFailure != null) { throw closeFailure; }
        }
        @Override public Folder getDefaultFolder() { return root; }
        @Override public Folder getFolder(String name) { throw new AssertionError("Unexpected folder lookup"); }
        @Override public Folder getFolder(URLName name) { throw new AssertionError("Unexpected folder lookup"); }
    }

    static class StubFolder extends Folder {
        final String fullName;
        final Folder parent;
        final int type;
        final char separator;
        Folder[] children = new Folder[0];
        String pattern;
        MessagingException listFailure, metadataFailure;

        StubFolder(Store store, String fullName, Folder parent, int type, char separator) {
            super(store);
            this.fullName = fullName; this.parent = parent; this.type = type; this.separator = separator;
        }
        @Override public String getName() { return fullName.substring(fullName.lastIndexOf('/') + 1); }
        @Override public String getFullName() { return fullName; }
        @Override public Folder getParent() { return parent; }
        @Override public char getSeparator() { return separator; }
        @Override public int getType() throws MessagingException {
            if (metadataFailure != null) { throw metadataFailure; }
            return type;
        }
        @Override public Folder[] list(String pattern) throws MessagingException {
            this.pattern = pattern;
            if (listFailure != null) { throw listFailure; }
            return children;
        }
        @Override public boolean exists() { throw new AssertionError("Unexpected exists query"); }
        @Override public boolean create(int type) { throw new AssertionError("Must not create folders"); }
        @Override public boolean hasNewMessages() { throw new AssertionError("Must not inspect messages"); }
        @Override public Folder getFolder(String name) { throw new AssertionError("Unexpected folder lookup"); }
        @Override public boolean delete(boolean recurse) { throw new AssertionError("Must not delete folders"); }
        @Override public boolean renameTo(Folder target) { throw new AssertionError("Must not rename folders"); }
        @Override public void open(int mode) { throw new AssertionError("Must not open folders"); }
        @Override public void close(boolean expunge) { throw new AssertionError("No folders were opened"); }
        @Override public boolean isOpen() { return false; }
        @Override public Flags getPermanentFlags() { throw new AssertionError("Must not inspect flags"); }
        @Override public int getMessageCount() { throw new AssertionError("Must not fetch counts"); }
        @Override public Message getMessage(int number) { throw new AssertionError("Must not fetch messages"); }
        @Override public void appendMessages(Message[] messages) { throw new AssertionError("Must not append"); }
        @Override public Message[] expunge() { throw new AssertionError("Must not expunge"); }
    }
}
