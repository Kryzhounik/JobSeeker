package com.seeker.mailer;

import java.util.List;
import java.util.Objects;

/** Complete folder snapshot; concurrent count changes are reported as failures. */
public record MailMessageListing(String folder, int messageCount, List<MailMessageHeader> messages) {
    public MailMessageListing {
        Objects.requireNonNull(folder, "folder");
        messages = List.copyOf(messages);
        if (messageCount != messages.size()) {
            throw new IllegalArgumentException("Message count must match complete listing");
        }
    }
}
