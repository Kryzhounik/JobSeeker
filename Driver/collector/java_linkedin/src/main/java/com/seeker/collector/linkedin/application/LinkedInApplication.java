package com.seeker.collector.linkedin.application;

import com.seeker.collector.core.CollectorApi;
import com.seeker.collector.common.CollectionReport;
import com.seeker.collector.linkedin.login.LoginResult;
import com.seeker.tools.api.ProgressSnapshot;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Compatibility entry point; new callers use CollectorApi. */
public final class LinkedInApplication {
    private final CollectorApi api;
    public LinkedInApplication(Path root) throws IOException { api = new CollectorApi(root); }
    public LoginResult login() throws IOException { return api.login(); }
    public CollectionReport collectBatch() throws IOException { return api.collectBatch("linkedin", ignored -> {}); }
    public CollectionReport collectBatch(Consumer<ProgressSnapshot> progress) throws IOException { return api.collectBatch("linkedin", progress); }
    public CollectionReport collectUrl(String runId, String url) throws IOException { return api.collectUrl(runId, url); }
    public CollectionReport collectUrl(String runId, String url, Consumer<ProgressSnapshot> progress) throws IOException { return api.collectUrl(runId, url, progress); }
    public CollectionReport collectUrl(String url) throws IOException { return api.collectUrl(url); }
    public CollectionReport collectUrl(String url, Consumer<ProgressSnapshot> progress) throws IOException { return api.collectUrl(url, progress); }
    public CollectionReport collectUrls(List<String> urls) throws IOException { return api.collectUrls(urls); }
    public CollectionReport collectUrls(List<String> urls, Consumer<ProgressSnapshot> progress) throws IOException { return api.collectUrls(urls, progress); }
    public CollectionReport collectUrls(List<String> urls, String prefix) throws IOException { return api.collectUrls(urls, prefix); }
    public CollectionReport collectUrls(List<String> urls, String prefix, Consumer<ProgressSnapshot> progress) throws IOException { return api.collectUrls(urls, prefix, progress); }
}
