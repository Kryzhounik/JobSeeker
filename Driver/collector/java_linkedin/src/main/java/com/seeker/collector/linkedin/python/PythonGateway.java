package com.seeker.collector.linkedin.python;

import com.seeker.collector.linkedin.collection.Preview;
import com.seeker.collector.linkedin.collection.PreviewDecision;
import com.seeker.collector.linkedin.collection.ProcessResult;

import java.util.List;

public interface PythonGateway extends AutoCloseable {
    List<String> priorityCompanyIds();

    PreviewDecision decidePreview(Preview preview);

    ProcessResult processHtml(Preview preview, String html);

    void logOutcome(Preview preview, String status, String reason);

    @Override
    void close();
}
