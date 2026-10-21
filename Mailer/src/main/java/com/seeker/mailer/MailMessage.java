package com.seeker.mailer;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Decoded message. receivedAt is null when the source supplies no receipt time. */
public record MailMessage(String identifier, String sender, String subject,
                          Instant receivedAt, String text, MailMessageType type,
                          List<String> vacancyUrls) {
    public MailMessage {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(type, "type");
        vacancyUrls = List.copyOf(vacancyUrls);
    }
}
