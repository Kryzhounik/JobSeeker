package com.seeker.applier;
import com.seeker.applier.coverletter.CoverLetterWriter;
import java.nio.file.Path;
import java.io.IOException;
import java.net.URI;
import com.seeker.codex.UsageLimitExceededException;
/** Operations shared by console and other Java callers. */
public final class ApplierApi {
    private final ApplierRunner runner;
    private final CoverLetterWriter writer;
    public ApplierApi(Path root) {
        this(new ApplierRunner(root), new CoverLetterWriter(root));
    }
    ApplierApi(ApplierRunner runner, CoverLetterWriter writer) {
        this.runner = runner;
        this.writer = writer;
    }
    public ApplierRunner.Result prepare(String url) throws IOException, InterruptedException, UsageLimitExceededException {
        if (url == null) throw new IllegalArgumentException("Expected an absolute HTTP(S) vacancy URL");
        URI parsed = URI.create(url);
        if (!("http".equalsIgnoreCase(parsed.getScheme()) || "https".equalsIgnoreCase(parsed.getScheme()))
                || parsed.getHost() == null)
            throw new IllegalArgumentException("Expected an absolute HTTP(S) vacancy URL");
        return runner.run(url);
    }
    public String coverLetter(long id) throws IOException, InterruptedException, UsageLimitExceededException { return writer.generate(id); }
    public Path coverLetter(long id, Path output) throws IOException, InterruptedException, UsageLimitExceededException { return writer.generate(id, output); }
}
