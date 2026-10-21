package com.seeker.mailer;

import java.time.Instant;

/**
 * Mail search criteria. A null sender omits the sender restriction; a null
 * receivedAfter omits the date restriction. receivedAfter is exclusive.
 * The folder belongs to local configuration, not this query.
 */
public record MailQuery(String sender, Instant receivedAfter) {
    public MailQuery {
        if (sender != null) {
            sender = sender.strip();
            if (sender.isEmpty()) {
                throw new IllegalArgumentException("Sender must be nonblank or null");
            }
        }
    }
}
