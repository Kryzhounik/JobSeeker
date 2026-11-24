package com.seeker.applier;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** Command declarations and API delegation; CliRunner owns parsing and output. */
@Command(name = "applier", mixinStandardHelpOptions = true)
public final class ApplierAdapter {
    private final ApplierApi api;

    public ApplierAdapter(ApplierApi api) { this.api = api; }

    /** Preserve the original bare-URL syntax for standalone and Driver callers. */
    public static String[] arguments(String[] args) {
        if (args.length > 0 && (args[0].equals("prepare") || args[0].equals("cover-letter") || args[0].startsWith("-")))
            return args;
        return Stream.concat(Stream.of("prepare"), Arrays.stream(args)).toArray(String[]::new);
    }

    @Command(name = "prepare", mixinStandardHelpOptions = true)
    public ApplierRunner.Result prepare(@Parameters(index = "0", paramLabel = "URL") String url) throws Exception {
        return api.prepare(url);
    }

    @Command(name = "cover-letter", mixinStandardHelpOptions = true)
    public String coverLetter(
            @Parameters(index = "0", paramLabel = "jobs.id") long id,
            @Parameters(index = "1", arity = "0..1", paramLabel = "output.txt") Path output) throws Exception {
        return output == null ? api.coverLetter(id) : api.coverLetter(id, output).toString();
    }
}
