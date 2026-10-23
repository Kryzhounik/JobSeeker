package com.seeker.collector.linkedin.application;

import com.seeker.collector.linkedin.browser.BrowserSettings;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.tools.api.ProgressSnapshot;
import com.seeker.collector.linkedin.collection.LinkedInCollectionService;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.linkedin.config.ProjectPaths;
import com.seeker.collector.linkedin.login.LinkedInLoginService;
import com.seeker.collector.linkedin.login.LoginResult;
import com.seeker.collector.linkedin.python.PythonRuntime;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/** Entry points for login and collection. */
public final class LinkedInApplication {
    private static final DateTimeFormatter RUN_ID_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private final ProjectPaths paths;
    private final LinkedInCollectionService collectionService;

    public LinkedInApplication(Path projectRoot) throws IOException {
        this.paths = ProjectPaths.fromProjectRoot(projectRoot);
        PythonRuntime pythonRuntime = PythonRuntime.load(
                paths.collectorRoot().resolve("java_linkedin/runtime.properties")
        );
        this.collectionService = new LinkedInCollectionService(
                paths,
                pythonRuntime
        );
    }

    public LoginResult login() throws IOException {
        CollectorConfig config = CollectorConfig.load(paths.configPath());
        BrowserSettings browserSettings = new BrowserSettings(
                config.browserChannel(),
                false,
                config.browserLaunchTimeoutSeconds(),
                config.pageTimeoutSeconds()
        );
        return new LinkedInLoginService(paths.profileDirectory(), browserSettings).login();
    }

    public CollectionReport collectBatch() throws IOException {
        return collectBatch(ignored -> { });
    }

    public CollectionReport collectBatch(Consumer<ProgressSnapshot> progress) throws IOException {
        return collectionService.collectBatch(
                RUN_ID_TIME.format(Instant.now()) + "-batch-linkedin", progress
        );
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
