package com.seeker.mailer;

import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Command declarations and delegation; the runner owns parsing and console output. */
@Command(name = "mailer", description = "Read email and extract vacancy recommendations.",
        mixinStandardHelpOptions = true)
public final class MailerAdapter {
    private final Mailer mailer;

    public MailerAdapter(Mailer mailer) {
        this.mailer = mailer;
    }

    @Command(name = "folders", description = "List mailbox folders.", mixinStandardHelpOptions = true)
    public List<MailFolder> folders() throws MailException {
        return mailer.listFolders();
    }

    @Command(name = "messages", description = "List all message headers in a folder.", mixinStandardHelpOptions = true)
    public MailMessageListing messages(
            @Option(names = "--folder", required = true, paramLabel = "<folder>") String folder
    ) throws MailException {
        return mailer.listMessages(folder);
    }

    @Command(name = "message", description = "Read a message, including text and HTML bodies.", mixinStandardHelpOptions = true)
    public MailMessageContent message(
            @Option(names = "--folder", required = true, paramLabel = "<folder>") String folder,
            @Option(names = "--id", required = true, paramLabel = "<id>") String id
    ) throws MailException {
        return mailer.readMessage(folder, id);
    }

    @Command(name = "export", description = "Export a complete MIME message as .eml.", mixinStandardHelpOptions = true)
    public MailExport export(
            @Option(names = "--folder", required = true, paramLabel = "<folder>") String folder,
            @Option(names = "--id", required = true, paramLabel = "<id>") String id,
            @Option(names = "--output", required = true, paramLabel = "<path.eml>") Path output
    ) throws MailException {
        return mailer.exportMessage(folder, id, output);
    }

    @Command(name = "recommendation-urls", description = "Extract new URLs and advance Mailer's checkpoint.", mixinStandardHelpOptions = true)
    public List<String> recommendationUrls() throws MailException {
        return mailer.getRecommendationUrls();
    }
}
