package com.seeker.mailer.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.seeker.mailer.MailException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** A local UTF-8 append-only log. One JSON object per line; no console handler. */
final class MailLog {
    private static final Gson JSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final Object WRITE_LOCK = new Object();
    private final Path file;

    MailLog(Path projectRoot) {
        file = projectRoot.resolve("Data/mailer/mailer.log");
    }

    void write(String event, String id, String subject, Boolean seen, String detail) throws MailException {
        String line = JSON.toJson(new Entry(Instant.now().toString(), event, id, subject, seen, detail)) + "\n";
        synchronized (WRITE_LOCK) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new MailException("Cannot append Data/mailer/mailer.log", e);
            }
        }
    }

    void failure(String event, String id, String subject, Boolean seen, MailException error) {
        // MailException messages at these boundaries are controlled by Mailer.
        // Keep private provider response text and full message bodies out of the log.
        String detail = error.getMessage();
        if (error.getCause() != null) {
            detail += " (cause: " + error.getCause().getClass().getSimpleName() + ")";
        }
        try {
            write(event, id, subject, seen, detail);
        } catch (MailException loggingFailure) {
            error.addSuppressed(loggingFailure);
        }
    }

    private record Entry(String time, String event, String id, String subject, Boolean seen, String detail) {}
}
