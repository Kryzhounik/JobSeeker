package com.seeker.mailer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.seeker.mailer.internal.ImapMailAccess;
import java.time.Instant;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/** Minimal CLI over the same typed Java API; run from the repository root. */
public final class MailerCli {
    private MailerCli() {}

    @FunctionalInterface
    interface FolderSource {
        List<MailFolder> listFolders() throws MailException;
    }

    @FunctionalInterface
    interface MessageSource {
        MailMessageListing listMessages(String folder) throws MailException;
    }

    @FunctionalInterface
    interface MessageReader {
        MailMessageContent read(String folder, String id) throws MailException;
    }

    @FunctionalInterface
    interface MessageExporter {
        MailExport export(String folder, String id, Path output) throws MailException;
    }

    public static void main(String[] args) {
        var stdout = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        var mailer = new Mailer(Path.of(""));
        int code = run(args, stdout, stderr, mailer::listFolders, mailer::listMessages,
                mailer::readMessage, mailer::exportMessage);
        System.exit(code);
    }

    static int run(String[] args, PrintStream stdout, PrintStream stderr, FolderSource source, MessageSource messages,
                   MessageReader reader, MessageExporter exporter) {
        boolean foldersCommand = args.length == 1 && args[0].equals("folders");
        boolean messagesCommand = args.length == 3 && args[0].equals("messages") && args[1].equals("--folder")
                && !args[2].isBlank() && !args[2].startsWith("--")
                && args[2].indexOf('\r') < 0 && args[2].indexOf('\n') < 0 && args[2].indexOf('\0') < 0;
        boolean readCommand = args.length == 5 && args[0].equals("message") && args[1].equals("--folder")
                && args[3].equals("--id");
        boolean exportCommand = args.length == 7 && args[0].equals("export") && args[1].equals("--folder")
                && args[3].equals("--id") && args[5].equals("--output");
        Path output = null;
        try {
            if (readCommand || exportCommand) {
                ImapMailAccess.validateSelection(args[2], args[4]);
            }
            if (exportCommand) {
                output = Path.of(args[6]);
                if (args[6].isBlank() || output.getFileName() == null
                        || !output.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".eml")) {
                    throw new IllegalArgumentException();
                }
            }
        } catch (MailException | IllegalArgumentException e) {
            stderr.println("Invalid folder, message selector or export path");
            return 2;
        }
        if (!foldersCommand && !messagesCommand && !readCommand && !exportCommand) {
            stderr.println("Usage: java -jar Mailer/mailer.jar folders | messages --folder <folder> | "
                    + "message --folder <folder> --id <id> | export --folder <folder> --id <id> --output <path.eml>");
            return 2;
        }
        try {
            Object result;
            if (foldersCommand) {
                result = source.listFolders();
            } else if (messagesCommand) {
                result = messages.listMessages(args[2]);
            } else if (readCommand) {
                result = reader.read(args[2], args[4]);
            } else {
                result = exporter.export(args[2], args[4], output);
            }
            String json = new GsonBuilder().serializeNulls().disableHtmlEscaping()
                    .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString()))
                    .create().toJson(result);
            stdout.println(json);
            if (stdout.checkError()) {
                stderr.println("Cannot write mail JSON to stdout");
                return 1;
            }
            return 0;
        } catch (MailException e) {
            // Provider exceptions may contain private server responses. Keep their
            // causes on the Java API exception, not in routine CLI output.
            stderr.println(e.getMessage());
            return 1;
        }
    }
}
