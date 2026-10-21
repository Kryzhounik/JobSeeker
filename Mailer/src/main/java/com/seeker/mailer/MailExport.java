package com.seeker.mailer;

/** A complete server MIME stream saved locally without library serialization. */
public record MailExport(String folder, String id, String output, long byteCount) {}
