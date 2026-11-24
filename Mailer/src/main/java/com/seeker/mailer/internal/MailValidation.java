package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Argument checks for mail access. */
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

    public static void exportPath(Path output) throws MailException {
        Objects.requireNonNull(output, "output");
        if (output.toString().isBlank() || output.getFileName() == null
                || !output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".eml")) {
            throw new MailException("Export output must have an .eml extension");
        }
    }
}
