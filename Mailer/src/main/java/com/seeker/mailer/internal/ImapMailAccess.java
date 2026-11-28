package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import com.seeker.logging.LogInput;
import com.seeker.logging.LogOutput;
import com.seeker.mailer.Mailer.RecommendationStats;
import java.util.function.Consumer;
import com.seeker.mailer.MailFolder;
import com.seeker.mailer.MailMessageHeader;
import com.seeker.mailer.MailMessageListing;
import com.seeker.mailer.MailMessageContent;
import com.seeker.mailer.MailHeader;
import com.seeker.mailer.MailExport;
import jakarta.mail.internet.MimeUtility;
import org.eclipse.angus.mail.util.ReadableMime;
import org.eclipse.angus.mail.imap.IMAPMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Message;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Arrays;
import java.util.HashSet;

/** Transport boundary. Folder inspection never opens a folder or fetches mail. */
public final class ImapMailAccess {
    private static final Object RECOMMENDATION_PASS_LOCK = new Object();
    @FunctionalInterface
    interface StoreFactory {
        Store create(Session session) throws MessagingException;
    }

    private final Path projectRoot;
    private final StoreFactory stores;

    public ImapMailAccess(Path projectRoot) {
        this(projectRoot, session -> session.getStore("imaps"));
    }

    ImapMailAccess(Path projectRoot, StoreFactory stores) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        this.stores = Objects.requireNonNull(stores, "stores");
    }

    @LogOutput(include = "size")
    public List<MailFolder> listFolders() throws MailException {
        return fetchFolders();
    }

    private List<MailFolder> fetchFolders() throws MailException {
        MailSettings settings = MailSettings.load(projectRoot);
        Session session = Session.getInstance(settings.sessionProperties());
        session.setDebug(false);
        try (Store store = stores.create(session)) {
            store.connect(settings.host, settings.port, settings.username, settings.appPassword);
            Folder root = store.getDefaultFolder();
            // LIST "" "*" includes unsubscribed folders and every hierarchy level.
            Folder[] folders = root.list("*");
            var result = new ArrayList<MailFolder>(folders.length);
            for (Folder folder : folders) {
                int type = folder.getType();
                Folder parent = folder.getParent();
                char separator = folder.getSeparator();
                result.add(new MailFolder(folder.getName(), folder.getFullName(),
                        parent == null ? null : parent.getFullName(),
                        separator == '\0' ? null : String.valueOf(separator),
                        (type & Folder.HOLDS_MESSAGES) != 0, (type & Folder.HOLDS_FOLDERS) != 0));
            }
            result.sort(Comparator.comparing(MailFolder::fullName));
            return List.copyOf(result);
        } catch (AuthenticationFailedException e) {
            throw new MailException("IMAP authentication failed; check imap.username and imap.appPassword", e);
        } catch (MessagingException e) {
            throw new MailException("IMAP connection or folder inspection failed", e);
        }
    }

    @LogOutput(include = {"folder", "messageCount"})
    public MailMessageListing listMessages(String folderName) throws MailException {
        if (folderName == null || folderName.isBlank() || folderName.indexOf('\r') >= 0 || folderName.indexOf('\n') >= 0
                || folderName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("An explicit nonblank folder name is required");
        }

        return fetchMessages(folderName);
    }

    private MailMessageListing fetchMessages(String folderName) throws MailException {
        MailSettings settings = MailSettings.load(projectRoot);
        Session session = Session.getInstance(settings.sessionProperties());
        session.setDebug(false);
        try (Store store = stores.create(session)) {
            store.connect(settings.host, settings.port, settings.username, settings.appPassword);
            Folder folder = store.getFolder(folderName);
            try (var handle = new FolderHandle(folder)) {
                if (!folder.exists() || (folder.getType() & Folder.HOLDS_MESSAGES) == 0) {
                    throw new MailException("Selected folder does not exist or cannot contain messages");
                }
                folder.open(Folder.READ_ONLY);
                if (folder.getMode() != Folder.READ_ONLY || !(folder instanceof UIDFolder uids)) {
                    throw new MailException("Read-only IMAP UID access is required");
                }
                long validity = uids.getUIDValidity();
                if (validity < 1 || validity > 0xffffffffL) {
                    throw new MailException("Server did not supply valid UIDVALIDITY");
                }
                int count = folder.getMessageCount();
                Message[] messages = uids.getMessagesByUID(1, UIDFolder.LASTUID);
                if (messages.length != count) {
                    throw new MailException("Folder changed during enumeration; repeat the listing");
                }
                var profile = new FetchProfile();
                profile.add(FetchProfile.Item.ENVELOPE);
                profile.add(FetchProfile.Item.FLAGS);
                profile.add(UIDFolder.FetchProfileItem.UID);
                profile.add("Message-ID");
                var headers = new ArrayList<MailMessageHeader>(count);
                var seenUids = new HashSet<Long>();
                String account = MessageSelector.accountScope(settings);
                // Batches limit each metadata request, never the total number of returned messages.
                for (int start = 0; start < messages.length; start += 200) {
                    Message[] batch = Arrays.copyOfRange(messages, start, Math.min(start + 200, messages.length));
                    for (Message message : batch) {
                        if (message == null || message.isExpunged()) {
                            throw new MailException("Message disappeared during enumeration; repeat the listing");
                        }
                    }
                    folder.fetch(batch, profile);
                    for (Message message : batch) {
                        long uid = uids.getUID(message);
                        if (message.isExpunged() || uid < 1 || uid > 0xffffffffL || !seenUids.add(uid)) {
                            throw new MailException("Missing or duplicate message UID during enumeration");
                        }
                        var senders = new ArrayList<String>();
                        Address[] from = message.getFrom();
                        if (from != null) {
                            for (Address address : from) {
                                senders.add(address instanceof InternetAddress internet ? internet.toUnicodeString() : address.toString());
                            }
                        }
                        var received = message.getReceivedDate();
                        String[] messageIds = message.getHeader("Message-ID");
                        String id = new MessageSelector(account, folder.getFullName(), validity, uid).encode();
                        var header = new MailMessageHeader(id, senders, message.getSubject(),
                                received == null ? null : received.toInstant(),
                                messageIds == null || messageIds.length == 0 ? null : messageIds[0],
                                message.isSet(Flags.Flag.SEEN));
                        headers.add(header);
                    }
                }
                if (folder.getMessageCount() != count || uids.getUIDValidity() != validity) {
                    throw new MailException("Folder changed during enumeration; repeat the listing");
                }
                headers.sort(Comparator.comparingLong(header -> {
                    // Selectors were constructed locally; UID is the final numeric component.
                    return Long.parseLong(header.id().substring(header.id().lastIndexOf('.') + 1));
                }));
                return new MailMessageListing(folder.getFullName(), count, headers);
            }
        } catch (AuthenticationFailedException e) {
            throw new MailException("IMAP authentication failed; check imap.username and imap.appPassword", e);
        } catch (MessagingException e) {
            throw new MailException("IMAP connection or message enumeration failed", e);
        }
    }

    private record FolderHandle(Folder folder) implements AutoCloseable {
        @Override public void close() throws MessagingException {
            if (folder.isOpen()) {
                folder.close(false);
            }
        }
    }

    @FunctionalInterface
    private interface MessageAction<T> {
        T apply(Message message, Folder folder) throws MessagingException, IOException;
    }

    private <T> T withMessage(String folderName, String id, MessageAction<T> action) throws MailException {
        try {
            MailValidation.selection(folderName, id);
            MessageSelector selector = MessageSelector.decode(id);
            MailSettings settings = MailSettings.load(projectRoot);
            String account = MessageSelector.accountScope(settings);
            if (!selector.account().equals(account)) {
                throw new MailException("Message selector belongs to another account");
            }
            Session session = Session.getInstance(settings.sessionProperties());
            session.setDebug(false);
            try (Store store = stores.create(session)) {
                store.connect(settings.host, settings.port, settings.username, settings.appPassword);
                Folder folder = store.getFolder(folderName);
                try (var handle = new FolderHandle(folder)) {
                    if (!folder.exists() || (folder.getType() & Folder.HOLDS_MESSAGES) == 0) {
                        throw new MailException("Selected folder does not exist or cannot contain messages");
                    }
                    folder.open(Folder.READ_ONLY);
                    if (folder.getMode() != Folder.READ_ONLY || !(folder instanceof UIDFolder uids)) {
                        throw new MailException("Read-only IMAP UID access is required");
                    }
                    Message message = selector.resolve(account, folder.getFullName(), uids);
                    if (message instanceof IMAPMessage imap) {
                        imap.setPeek(true);
                    }
                    T result = action.apply(message, folder);
                    if (message.isExpunged() || uids.getUIDValidity() != selector.validity()) {
                        throw new MailException("Selected message changed during access");
                    }
                    return result;
                }
            }
        } catch (MailException e) {
            throw e;
        } catch (AuthenticationFailedException e) {
            throw new MailException("IMAP authentication failed; check imap.username and imap.appPassword", e);
        } catch (MessagingException | IOException e) {
            throw new MailException("Cannot read or export selected message", e);
        }
    }

    @LogInput(include = {"folder", "id"})
    @LogOutput(include = {"#id", "#result.summary.subject", "#result.summary.seen"})
    public MailMessageContent readMessage(String folder, String id) throws MailException {
        return withMessage(folder, id, (message, actualFolder) -> {
            var headers = new ArrayList<MailHeader>();
            var all = message.getAllHeaders();
            while (all.hasMoreElements()) {
                var header = all.nextElement();
                headers.add(new MailHeader(header.getName(), header.getValue(), MimeUtility.decodeText(header.getValue())));
            }
            var plain = new ArrayList<String>();
            var html = new ArrayList<String>();
            for (var part : MimeBodyDecoder.decode(message)) {
                (part.html() ? html : plain).add(part.text());
            }
            var senders = new ArrayList<String>();
            var from = message.getFrom();
            if (from != null) {
                for (var address : from) {
                    senders.add(address instanceof InternetAddress internet ? internet.toUnicodeString() : address.toString());
                }
            }
            var receipt = message.getReceivedDate();
            var ids = message.getHeader("Message-ID");
            var summary = new MailMessageHeader(id, senders, message.getSubject(),
                    receipt == null ? null : receipt.toInstant(), ids == null || ids.length == 0 ? null : ids[0],
                    message.isSet(Flags.Flag.SEEN));
            return new MailMessageContent(actualFolder.getFullName(), summary, headers, plain, html);
        });
    }

    @LogOutput(include = {"id", "output", "byteCount"})
    public MailExport exportMessage(String folder, String id, Path output) throws MailException {
        return saveMessage(folder, id, output);
    }

    private MailExport saveMessage(String folder, String id, Path output) throws MailException {
        MailValidation.selection(folder, id);
        MailValidation.exportPath(output);
        Path target = output.toAbsolutePath().normalize();
        Path temporary = null;
        MailException failure = null;
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new MailException("Export output already exists; choose a new path");
            }
            temporary = Files.createTempFile(target.getParent(), ".mailer-", ".part");
            Path staged = temporary;
            long bytes = withMessage(folder, id, (message, actualFolder) -> {
                if (!(message instanceof ReadableMime raw)) {
                    throw new MailException("Mail provider does not supply an original MIME stream");
                }
                // Never use getInputStream (decoded body only) or writeTo (serialization).
                try (var input = raw.getMimeStream(); var destination = Files.newOutputStream(staged)) {
                    return input.transferTo(destination);
                }
            });
            // Publish only after streams, folder and store have all closed successfully.
            Files.move(temporary, target);
            temporary = null;
            return new MailExport(folder, id, target.toString(), bytes);
        } catch (MailException e) {
            failure = e;
            throw e;
        } catch (IOException e) {
            failure = new MailException("Cannot write message export file", e);
            throw failure;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException e) {
                    if (failure != null) {
                        failure.addSuppressed(e);
                    } else {
                        throw new MailException("Cannot remove incomplete export file", e);
                    }
                }
            }
        }
    }

    public List<String> getRecommendationUrls() throws MailException {
        return getRecommendationUrls(ignored -> { });
    }

    public List<String> getRecommendationUrls(
            Consumer<RecommendationStats> progress) throws MailException {
        synchronized (RECOMMENDATION_PASS_LOCK) {
            return scanRecommendations(Objects.requireNonNull(progress));
        }
    }

    @LogInput
    @LogOutput(include = "size", duration = true)
    private List<String> scanRecommendations(
            Consumer<RecommendationStats> progress) throws MailException {
        int checked = 0;
        int filtered = 0;
        progress.accept(new RecommendationStats(0, 0, 0));
        try {
            MailSettings mail = MailSettings.load(projectRoot);
            RecommendationSettings settings = RecommendationSettings.load(projectRoot);
            var checkpoint = new RecommendationProgress(projectRoot);
            String account = MessageSelector.accountScope(mail);
            var session = Session.getInstance(mail.sessionProperties());
            session.setDebug(false);
            var urls = new java.util.LinkedHashSet<String>();
            long validity;
            long lastUid;
            try (Store store = stores.create(session)) {
                store.connect(mail.host, mail.port, mail.username, mail.appPassword);
                Folder folder = store.getFolder(settings.folder());
                try (var handle = new FolderHandle(folder)) {
                    if (!folder.exists() || (folder.getType() & Folder.HOLDS_MESSAGES) == 0) {
                        throw new MailException("Recommendation folder does not exist or cannot contain messages");
                    }
                    folder.open(Folder.READ_ONLY);
                    if (folder.getMode() != Folder.READ_ONLY || !(folder instanceof UIDFolder uids)) {
                        throw new MailException("Read-only IMAP UID access is required");
                    }
                    validity = uids.getUIDValidity();
                    if (validity < 1 || validity > 0xffffffffL) throw new MailException("Invalid recommendation folder UIDVALIDITY");
                    lastUid = checkpoint.load(account, settings, validity);
                    long nextUid = uids.getUIDNext();
                    if (nextUid < 1 || nextUid > 0x100000000L) throw new MailException("Server did not supply valid UIDNEXT");
                    long throughUid = nextUid - 1;

                    var messages = messagesInRange(uids, lastUid, throughUid);
                    for (var entry : messages.entrySet()) {
                        Message message = entry.getValue();
                        List<String> extracted = extractRecommendation(message, settings, mail.username);
                        if (extracted == null) filtered++;
                        else urls.addAll(extracted);
                        if (message.isExpunged()) throw new MailException("Message disappeared during recommendation scan");
                        lastUid = entry.getKey();
                        progress.accept(new RecommendationStats(++checked, filtered, urls.size()));
                    }
                    if (uids.getUIDValidity() != validity) throw new MailException("UIDVALIDITY changed during recommendation pass");
                }
            }
            List<String> result = List.copyOf(urls);
            // This is the last fallible operation. No message checkpoints are written above.
            checkpoint.save(account, settings, validity, lastUid);
            return result;
        } catch (MailException e) {
            throw e;
        } catch (MessagingException | IOException | RuntimeException e) {
            throw new MailException("Recommendation pass failed; checkpoint unchanged", e);
        }
    }

    @LogInput(include = {"afterUid", "throughUid"})
    @LogOutput(include = "size")
    private java.util.SortedMap<Long, Message> messagesInRange(UIDFolder uids, long afterUid, long throughUid)
            throws MessagingException, MailException {
        var messages = new java.util.TreeMap<Long, Message>();
        // IMAP ranges can reverse their bounds; an empty range must not reach the server.
        if (throughUid <= afterUid) return messages;
        for (Message message : uids.getMessagesByUID(afterUid + 1, throughUid)) {
            if (message == null || message.isExpunged()) throw new MailException("Message disappeared during recommendation scan");
            long uid = uids.getUID(message);
            if (uid <= afterUid || uid > throughUid || messages.put(uid, message) != null) {
                throw new MailException("Missing, duplicate or out-of-range recommendation UID");
            }
        }
        return messages;
    }

    @LogOutput(include = "#result", condition = "#result != null")
    private List<String> extractRecommendation(Message message, RecommendationSettings settings, String username)
            throws MessagingException, IOException {
        if (RecommendationParser.rejection(message, settings, username) != null) return null;
        String template = RecommendationParser.header(message, "X-LinkedIn-Template");
        if (message instanceof IMAPMessage imap) imap.setPeek(true);
        return RecommendationParser.extract(message, template);
    }

}
