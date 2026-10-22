package com.seeker.collector.linkedin.python;

import com.seeker.collector.linkedin.collection.PageReport;
import com.seeker.collector.linkedin.collection.Preview;
import com.seeker.collector.linkedin.collection.PreviewDecision;
import com.seeker.collector.linkedin.collection.ProcessResult;
import com.seeker.collector.linkedin.config.CollectorConfig;

import java.util.List;

public interface PythonGateway extends AutoCloseable {
    void startRun(String runId, CollectorConfig config);

    void logPage(String runId, PageReport page);

    void finishRun(
            String runId,
            String status,
            String stopReason,
            int acceptedCount,
            String message
    );

    List<String> priorityCompanyIds();

    PreviewDecision decidePreview(Preview preview);

    ProcessResult processHtml(Preview preview, String html, boolean easyApply);

    void logOutcome(Preview preview, String status, String reason);

    @Override
    void close();
}
