package com.seeker.mailer;

import java.util.List;

/** Generic decoded message; alternatives remain separate and HTML is returned as source. */
public record MailMessageContent(String folder, MailMessageHeader summary, List<MailHeader> headers,
                                 List<String> plainTextBodies, List<String> htmlBodies) {
    public MailMessageContent {
        headers = List.copyOf(headers);
        plainTextBodies = List.copyOf(plainTextBodies);
        htmlBodies = List.copyOf(htmlBodies);
    }
}
