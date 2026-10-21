package com.seeker.mailer;

import com.seeker.mailer.internal.ImapMailAccess;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** In-process mail API. Construction does not read settings or connect. */
public final class Mailer {
    private final ImapMailAccess mailAccess;

    public Mailer(Path projectRoot) {
        mailAccess = new ImapMailAccess(Objects.requireNonNull(projectRoot, "projectRoot"));
    }

    /** Lists all visible folders, including non-message hierarchy containers. */
    public List<MailFolder> listFolders() throws MailException {
        return mailAccess.listFolders();
    }

    /** Enumerates all headers in the explicitly selected folder, without fetching bodies. */
    public MailMessageListing listMessages(String folder) throws MailException {
        return mailAccess.listMessages(folder);
    }

    public MailMessageContent readMessage(String folder, String id) throws MailException {
        return mailAccess.readMessage(folder, id);
    }

    public MailExport exportMessage(String folder, String id, Path output) throws MailException {
        return mailAccess.exportMessage(folder, id, output);
    }
}
