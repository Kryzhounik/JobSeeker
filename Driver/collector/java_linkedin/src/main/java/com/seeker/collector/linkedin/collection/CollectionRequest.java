package com.seeker.collector.linkedin.collection;

import com.seeker.collector.linkedin.browser.BrowserSettings;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.linkedin.config.ProjectPaths;
import com.seeker.collector.linkedin.python.PythonRuntime;

public record CollectionRequest(
        Mode mode,
        ProjectPaths paths,
        CollectorConfig collectorConfig,
        BrowserSettings browserSettings,
        PythonRuntime pythonRuntime,
        String runId,
        String location,
        String url
) {
    public enum Mode {
        BATCH,
        FROM_URL
    }
}
