package com.seeker.mailer;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Header-only view. Null header values mean unavailable; id is an opaque selector. */
public record MailMessageHeader(String id, List<String> senders, String subject,
                                Instant receivedAt, String messageId, boolean seen) {
    public MailMessageHeader {
        Objects.requireNonNull(id, "id");
        senders = List.copyOf(senders);
    }
}
