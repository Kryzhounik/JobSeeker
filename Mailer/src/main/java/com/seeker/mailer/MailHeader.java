package com.seeker.mailer;

/** Jakarta Mail's field value and RFC 2047 decoded value; folding may differ from the raw MIME stream. */
public record MailHeader(String name, String value, String decodedValue) {}
