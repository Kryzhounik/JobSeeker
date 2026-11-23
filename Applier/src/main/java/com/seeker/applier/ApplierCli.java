package com.seeker.applier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Path;
import java.io.IOException;
import com.seeker.codex.UsageLimitExceededException;
/** Sole parser of module commands; never prints responses. */
public final class ApplierCli {
    private final ApplierApi api;
    public ApplierCli(ApplierApi api) { this.api = api; }
    public String handle(String[] args) throws IOException, InterruptedException, UsageLimitExceededException {
        if (args.length > 0 && "cover-letter".equals(args[0])) {
            if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("Expected: cover-letter <jobs.id> [output.txt]");
            long id = Long.parseLong(args[1]);
            return args.length == 2 ? api.coverLetter(id) : api.coverLetter(id, Path.of(args[2])).toString();
        }
        if (args.length != 1) throw new IllegalArgumentException("Expected a vacancy URL or cover-letter command");
        URI url = URI.create(args[0]);
        if (!("http".equalsIgnoreCase(url.getScheme()) || "https".equalsIgnoreCase(url.getScheme())) || url.getHost() == null)
            throw new IllegalArgumentException("Expected an absolute HTTP(S) vacancy URL");
        return new ObjectMapper().writeValueAsString(api.prepare(args[0]));
    }
}
