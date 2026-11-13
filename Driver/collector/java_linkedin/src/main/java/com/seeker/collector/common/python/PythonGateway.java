package com.seeker.collector.common.python;

import com.seeker.collector.common.Preview;
import com.seeker.collector.common.PreviewDecision;
import com.seeker.collector.common.ProcessResult;
import com.seeker.collector.common.PreparedVacancy;

public interface PythonGateway extends AutoCloseable {
    void startRun(String runId, Object config);

    void finishRun(
            String runId,
            String status,
            String stopReason,
            int acceptedCount,
            String message
    );

    PreviewDecision decidePreview(Preview preview);

    ProcessResult processVacancy(PreparedVacancy vacancy);

    String saveRaw(Preview preview, String content, String extension, String method);

    void logOutcome(Preview preview, String status, String reason);

    @Override
    void close();
}
