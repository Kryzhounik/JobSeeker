package com.seeker.collector.linkedin.application;

import com.seeker.collector.linkedin.browser.BrowserSettings;
import com.seeker.collector.linkedin.collection.CollectionReport;
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

/** Entry points for login and collection. */
public final class LinkedInApplication {
    private static final DateTimeFormatter RUN_ID_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private final LinkedInLoginService loginService;
    private final LinkedInCollectionService collectionService;

    public LinkedInApplication(Path projectRoot) throws IOException {
        ProjectPaths paths = ProjectPaths.fromProjectRoot(projectRoot);
        CollectorConfig config = CollectorConfig.load(paths.configPath());
        BrowserSettings browserSettings = new BrowserSettings(
                config.browserChannel(),
                false,
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
                pythonRuntime
        );
    }

    public LoginResult login() throws IOException {
        return loginService.login();
    }

    public CollectionReport batch() throws IOException {
        return collectionService.collectBatch(
                RUN_ID_TIME.format(Instant.now()) + "-batch-linkedin"
        );
    }

    public CollectionReport fromUrl(String runId, String url) throws IOException {
        return collectionService.collectFromUrl(runId, url);
    }
}
