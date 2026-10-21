package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.UIDFolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/** Stable identity, independent of mutable IMAP sequence numbers and Message-ID headers. */
record MessageSelector(String account, String folder, long validity, long uid) {
    MessageSelector {
        if (account == null || !account.matches("[a-f0-9]{64}") || folder == null || folder.isEmpty()
                || validity < 1 || validity > 0xffffffffL || uid < 1 || uid > 0xffffffffL) {
            throw new IllegalArgumentException("Invalid message selector");
        }
    }

    static String accountScope(MailSettings settings) {
        String scope = settings.host.toLowerCase(Locale.ROOT) + "\n" + settings.port + "\n" + settings.username;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    String encode() {
        return "v1." + account + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(folder.getBytes(StandardCharsets.UTF_8)) + "." + validity + "." + uid;
    }

    static MessageSelector decode(String id) throws MailException {
        try {
            String[] parts = id.split("\\.", -1);
            if (parts.length != 5 || !parts[0].equals("v1")) {
                throw new IllegalArgumentException();
            }
            return new MessageSelector(parts[1], new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8),
                    Long.parseLong(parts[3]), Long.parseLong(parts[4]));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new MailException("Invalid message selector", e);
        }
    }

    /** Resolve only within an already-open folder; never fall back to a sequence number. */
    Message resolve(String currentAccount, String currentFolder, UIDFolder folder) throws MailException, MessagingException {
        if (!account.equals(currentAccount) || !this.folder.equals(currentFolder)) {
            throw new MailException("Message selector belongs to another account or folder");
        }
        if (validity != folder.getUIDValidity()) {
            throw new MailException("Stale message selector: folder UIDVALIDITY changed");
        }
        Message message = folder.getMessageByUID(uid);
        if (message == null || message.isExpunged() || folder.getUID(message) != uid) {
            throw new MailException("Selected message no longer exists");
        }
        return message;
    }
}
