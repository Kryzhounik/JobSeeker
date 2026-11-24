package com.seeker.mailer;

import com.seeker.mailer.internal.MailValidation;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/** Command declarations and delegation; the runner owns parsing and console output. */
@Command(name = "mailer", description = "Read email and extract vacancy recommendations.",
        mixinStandardHelpOptions = true)
public final class MailerAdapter {
    private final Mailer mailer;
    @Spec
    private CommandSpec spec;

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
        validateFolder(folder);
        return mailer.listMessages(folder);
    }

    @Command(name = "message", description = "Read a message, including text and HTML bodies.", mixinStandardHelpOptions = true)
    public MailMessageContent message(
            @Option(names = "--folder", required = true, paramLabel = "<folder>") String folder,
            @Option(names = "--id", required = true, paramLabel = "<id>") String id
    ) throws MailException {
        validateSelection(folder, id);
        return mailer.readMessage(folder, id);
    }

    @Command(name = "export", description = "Export a complete MIME message as .eml.", mixinStandardHelpOptions = true)
    public MailExport export(
            @Option(names = "--folder", required = true, paramLabel = "<folder>") String folder,
            @Option(names = "--id", required = true, paramLabel = "<id>") String id,
            @Option(names = "--output", required = true, paramLabel = "<path.eml>") Path output
    ) throws MailException {
        validateSelection(folder, id);
        if (output.toString().isBlank() || output.getFileName() == null
                || !output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".eml")) {
            throw new ParameterException(spec.commandLine(), "Invalid export path: expected a .eml file");
        }
        return mailer.exportMessage(folder, id, output);
    }

    @Command(name = "recommendation-urls", description = "Extract new URLs and advance Mailer's checkpoint.", mixinStandardHelpOptions = true)
    public List<String> recommendationUrls() throws MailException {
        return mailer.getRecommendationUrls();
    }

    private void validateFolder(String folder) {
        try {
            MailValidation.folder(folder);
        } catch (MailException error) {
            throw new ParameterException(spec.commandLine(), error.getMessage());
        }
    }

    private void validateSelection(String folder, String id) {
        try {
            MailValidation.selection(folder, id);
        } catch (MailException error) {
            throw new ParameterException(spec.commandLine(), error.getMessage());
        }
    }
}
