package com.seeker.mailer;

import java.io.IOException;

/** An operational or interpretation failure; never an empty successful scan. */
public class MailException extends IOException {
    public MailException(String message) {
        super(message);
    }

    public MailException(String message, Throwable cause) {
        super(message, cause);
    }
}
