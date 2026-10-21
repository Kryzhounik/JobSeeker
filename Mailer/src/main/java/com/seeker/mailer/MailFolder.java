package com.seeker.mailer;

import java.util.Objects;

/**
 * Server folder metadata; names are preserved verbatim. A null parentFullName
 * denotes no parent; an empty parent name denotes the server's default root.
 * A null separator denotes a flat namespace (IMAP NIL delimiter).
 */
public record MailFolder(String name, String fullName, String parentFullName,
                         String separator, boolean holdsMessages, boolean holdsFolders) {
    public MailFolder {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(fullName, "fullName");
    }
}
