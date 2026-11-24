package com.seeker.collector.core;

import com.seeker.collector.common.CollectionReport;
import com.seeker.collector.linkedin.login.LoginResult;
import com.seeker.tools.api.ProgressSnapshot;
import java.io.IOException;
import java.util.function.Consumer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Command declarations; the API owns collection and the runner owns output. */
@Command(name = "collector")
public final class CollectorAdapter {
    private final CollectorApi api;

    public CollectorAdapter(CollectorApi api) {
        this.api = api;
    }

    @Command(name = "login")
    public LoginResult login() throws IOException {
        return api.login();
    }

    @Command(name = "collect-batch")
    public CollectionReport collectBatch(
            @Option(names = "--_cb", hidden = true) Consumer<ProgressSnapshot> progress
    ) throws IOException {
        return api.collectBatch(progress);
    }
}
