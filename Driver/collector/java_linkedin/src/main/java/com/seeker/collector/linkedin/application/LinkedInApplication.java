package com.seeker.collector.linkedin.application;

import com.seeker.collector.linkedin.browser.BrowserSettings;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.CollectionProgress;
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

    private final LinkedInLoginService loginService;
    private final LinkedInCollectionService collectionService;

    public LinkedInApplication(Path projectRoot) throws IOException {
        this(projectRoot, ignored -> { });
    }

    public LinkedInApplication(Path projectRoot, Consumer<CollectionProgress> progress) throws IOException {
        ProjectPaths paths = ProjectPaths.fromProjectRoot(projectRoot);
        CollectorConfig config = CollectorConfig.load(paths.configPath());
        BrowserSettings browserSettings = new BrowserSettings(
                config.browserChannel(),
                false,
                config.browserLaunchTimeoutSeconds(),
                config.pageTimeoutSeconds()
        );
        PythonRuntime pythonRuntime = PythonRuntime.load(
                paths.collectorRoot().resolve("java_linkedin/runtime.properties")
        );
        this.loginService = new LinkedInLoginService(
                paths.profileDirectory(),
                browserSettings
        );
        this.collectionService = new LinkedInCollectionService(
                paths,
                config,
                browserSettings,
                pythonRuntime,
                progress
        );
    }

    public LoginResult login() throws IOException {
        return loginService.login();
    }

    public CollectionReport collectBatch() throws IOException {
        return collectionService.collectBatch(
                RUN_ID_TIME.format(Instant.now()) + "-batch-linkedin"
        );
    }

    public CollectionReport collectUrl(String runId, String url) throws IOException {
        return collectionService.collectFromUrl(runId, url);
    }

    public CollectionReport collectUrl(String url) throws IOException {
        return collectUrls(List.of(url));
    }

    public CollectionReport collectUrls(List<String> urls) throws IOException {
        return collectUrls(urls, "");
    }

    public CollectionReport collectUrls(List<String> urls, String runPrefix) throws IOException {
        return collectionService.collectFromUrls(
                (runPrefix.isBlank() ? "" : runPrefix.strip() + "-")
                        + RUN_ID_TIME.format(Instant.now())
                        + (urls.size() == 1 ? "-url-linkedin" : "-urls-linkedin"),
                urls
        );
    }
}
