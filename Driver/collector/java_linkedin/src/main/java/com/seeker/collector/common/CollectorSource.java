package com.seeker.collector.common;

import com.seeker.tools.api.ProgressSnapshot;
import java.io.IOException;
import java.util.List;

/** Retrieval boundary. Implementations receive shared processing operations at construction. */
public interface CollectorSource {
    String source();
    SourceResult collectBatch(VacancyProcessor processor, CollectionSink sink) throws IOException;
    SourceResult collectUrls(List<String> urls, VacancyProcessor processor, CollectionSink sink) throws IOException;
    ProgressSnapshot startingSnapshot(String action, Integer urlCount);
    ProgressSnapshot snapshot(String status, String message);

    /** Optional legacy page diagnostics; only LinkedIn populates these. */
    default List<PageReport> pages() { return List.of(); }
}
