package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;

/** Pure argument checks shared by mail access and the command adapter. */
public final class MailValidation {
    private MailValidation() {}

    public static void folder(String folder) throws MailException {
        if (folder == null || folder.isBlank() || folder.indexOf('\r') >= 0
                || folder.indexOf('\n') >= 0 || folder.indexOf('\0') >= 0) {
            throw new MailException("Invalid folder name");
        }
    }

    public static void selection(String folder, String id) throws MailException {
        folder(folder);
        if (!MessageSelector.decode(id).folder().equals(folder)) {
            throw new MailException("Message selector belongs to another folder");
        }
    }
}
