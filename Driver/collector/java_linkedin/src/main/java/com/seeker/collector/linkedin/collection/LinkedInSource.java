package com.seeker.collector.linkedin.collection;

import com.seeker.collector.common.*;
import com.seeker.collector.linkedin.browser.*;
import com.seeker.collector.linkedin.config.*;
import com.seeker.collector.linkedin.python.LinkedInGateway;
import com.seeker.easyreasoning.EasyReasoningApi;
import com.seeker.tools.api.ProgressSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/** Owns LinkedIn navigation, sessions, diagnostics, and description preparation. */
public final class LinkedInSource implements CollectorSource {
    private final ProjectPaths paths;
    private final CollectorConfig config;
    private final String runId;
    private final Consumer<ProgressSnapshot> progress;
    private LinkedInCollector collector;

    public LinkedInSource(ProjectPaths paths, CollectorConfig config, String runId,
                          Consumer<ProgressSnapshot> progress) {
        this.paths = paths;
        this.config = config;
        this.runId = runId;
        this.progress = progress;
    }

    @Override public String source() { return "linkedin"; }

    public static com.seeker.collector.linkedin.login.LoginResult login(ProjectPaths paths) throws IOException {
        CollectorConfig config = CollectorConfig.load(paths.configPath());
        BrowserSettings settings = new BrowserSettings(config.browserChannel(), false,
                config.browserLaunchTimeoutSeconds(), config.pageTimeoutSeconds());
        return new com.seeker.collector.linkedin.login.LinkedInLoginService(paths.profileDirectory(), settings).login();
    }

    @Override public SourceResult collectBatch(VacancyProcessor processor, CollectionSink sink) throws IOException {
        return collect(null, processor, sink);
    }

    @Override public SourceResult collectUrls(List<String> urls, VacancyProcessor processor, CollectionSink sink) throws IOException {
        return collect(urls, processor, sink);
    }

    private SourceResult collect(List<String> urls, VacancyProcessor processor, CollectionSink sink) throws IOException {
        Integer count = urls == null ? null : urls.size();
        progress.accept(startingSnapshot("starting_browser", count));
        BrowserSettings settings = new BrowserSettings(config.browserChannel(), false,
                config.browserLaunchTimeoutSeconds(), config.pageTimeoutSeconds());
        // TODO: Check duplicate URLs before browser startup if session cost becomes a problem.
        try (EasyReasoningApi reasoning = new EasyReasoningApi(paths.driverRoot().getParent());
             LinkedInGateway python = new LinkedInGateway();
             PersistentLinkedInSession session = new PersistentLinkedInSession(paths.profileDirectory(), settings)) {
            LinkedInPageClient browser = new LinkedInPageClient(session, config);
            progress.accept(startingSnapshot("checking_session", count));
            browser.preflight();
            collector = new LinkedInCollector(browser, python, config, runId, progress, processor, sink,
                    new LinkedInVacancyPreparer(python, reasoning));
            return urls == null ? collector.runBatch() : collector.runFromUrls(urls);
        }
    }

    @Override public ProgressSnapshot startingSnapshot(String action, Integer urlCount) {
        return urlCount == null ? CollectionProgress.starting(runId, action, config.limit())
                : CollectionProgress.startingUrls(runId, action, urlCount);
    }

    @Override public ProgressSnapshot snapshot(String status, String message) {
        return collector.snapshot(status, message);
    }

    @Override public List<PageReport> pages() { return collector.pages(); }
}
