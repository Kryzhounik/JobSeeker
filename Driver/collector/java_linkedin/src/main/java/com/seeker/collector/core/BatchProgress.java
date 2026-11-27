package com.seeker.collector.core;

import com.seeker.tools.api.ImmutableProgressSnapshot;
import com.seeker.tools.api.ProgressSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Adds whole-batch counters while retaining the active source's display fields. */
final class BatchProgress {
    private final Consumer<ProgressSnapshot> listener;
    private final Map<String, Integer> limits;
    private final Map<String, CollectionAccumulator> sinks;
    private String stage = "Collection";

    BatchProgress(Consumer<ProgressSnapshot> listener, Map<String, Integer> limits, Map<String, CollectionAccumulator> sinks) {
        this.listener = listener;
        this.limits = limits;
        this.sinks = sinks;
    }

    void emit(String source, ProgressSnapshot snapshot) {
        if (limits.size() == 1) {
            listener.accept(new ImmutableProgressSnapshot(snapshot.runId(), stage, snapshot.action(),
                    snapshot.progress(), snapshot.fields(), snapshot.details()));
            return;
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("Source", source);
        fields.put("Accepted total", Integer.toString(sinks.values().stream().mapToInt(CollectionAccumulator::acceptedCount).sum()));
        limits.forEach((name, limit) -> fields.put(name + " accepted / limit",
                sinks.get(name).acceptedCount() + (limit > 0 ? " / " + limit : " / unlimited")));
        fields.putAll(snapshot.fields());
        listener.accept(new ImmutableProgressSnapshot(snapshot.runId(), stage, snapshot.action(),
                "Collection " + fields.get("Accepted total") + " (" + source + ")", fields, snapshot.details()));
    }

    void stage(String stage) {
        this.stage = stage;
    }

    void email(String runId, com.seeker.mailer.Mailer.RecommendationStats stats) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("Checked emails", Integer.toString(stats.checkedEmails()));
        fields.put("Filtered emails", Integer.toString(stats.filteredEmails()));
        fields.put("Unique vacancy URLs", Integer.toString(stats.uniqueUrls()));
        listener.accept(new ImmutableProgressSnapshot(runId, "Email recommendations", "Reading emails",
                "Email recommendations", fields, ""));
    }
}
