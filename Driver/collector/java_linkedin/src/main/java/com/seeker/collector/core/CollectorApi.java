package com.seeker.collector.core;

import com.seeker.collector.linkedin.collection.LinkedInSource;
import com.seeker.collector.common.CollectionReport;
import com.seeker.tools.api.ProgressSnapshot;

import com.seeker.collector.common.ProjectPaths;
import com.seeker.collector.linkedin.login.LoginResult;
import com.seeker.python.PythonRuntime;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/** Entry points for login and collection. */
public final class CollectorApi {
    private static final DateTimeFormatter RUN_ID_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private final ProjectPaths paths;
    private final CollectionService collectionService;

    public CollectorApi(Path projectRoot) throws IOException {
        this.paths = ProjectPaths.fromProjectRoot(projectRoot);
        PythonRuntime pythonRuntime = PythonRuntime.load(
                paths.driverRoot().getParent().resolve("Tools/runtime.properties")
        );
        this.collectionService = new CollectionService(
                paths,
                pythonRuntime
        );
    }

    public LoginResult login() throws IOException {
        return LinkedInSource.login(paths);
    }

    public CollectionReport collectBatch() throws IOException {
        return collectBatch(ignored -> { });
    }

    public CollectionReport collectBatch(Consumer<ProgressSnapshot> progress) throws IOException {
        return collectBatch(null, progress);
    }

    public CollectionReport collectBatch(String source, Consumer<ProgressSnapshot> progress) throws IOException {
        return collectionService.collectBatch(RUN_ID_TIME.format(Instant.now()) + "-batch", source, progress);
    }

    public CollectionReport collectUrl(String runId, String url) throws IOException {
        return collectUrl(runId, url, ignored -> { });
    }

    public CollectionReport collectUrl(String runId, String url, Consumer<ProgressSnapshot> progress)
            throws IOException {
        return collectionService.collectFromUrl(runId, url, progress);
    }

    public CollectionReport collectUrl(String url) throws IOException {
        return collectUrls(List.of(url));
    }

    public CollectionReport collectUrl(String url, Consumer<ProgressSnapshot> progress) throws IOException {
        return collectUrls(List.of(url), progress);
    }

    public CollectionReport collectUrls(List<String> urls) throws IOException {
        return collectUrls(urls, "");
    }

    public CollectionReport collectUrls(List<String> urls, Consumer<ProgressSnapshot> progress) throws IOException {
        return collectUrls(urls, "", progress);
    }

    public CollectionReport collectUrls(List<String> urls, String runPrefix) throws IOException {
        return collectUrls(urls, runPrefix, ignored -> { });
    }

    public CollectionReport collectUrls(List<String> urls, String runPrefix,
                                       Consumer<ProgressSnapshot> progress) throws IOException {
        return collectionService.collectFromUrls(
                (runPrefix.isBlank() ? "" : runPrefix.strip() + "-")
                        + RUN_ID_TIME.format(Instant.now())
                        + (urls.size() == 1 ? "-url-linkedin" : "-urls-linkedin"),
                urls, progress
        );
    }
}
