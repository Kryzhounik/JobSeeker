package com.seeker.collector.linkedin.collection;

import com.seeker.tools.api.ProgressSnapshot;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Complete collection snapshot, including display-ready counters and labels. */
public record CollectionProgress(String runId, String stage, String action, String progress,
                                 Map<String, String> fields, String details) implements ProgressSnapshot {
    public CollectionProgress {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public CollectionProgress(String runId, String action, String location, String search,
                              int page, int cards, int accepted, int limit,
                              Map<String, Integer> outcomes, String message) {
        this(runId, "Collection", actionName(action),
                "Collection " + accepted + (limit > 0 ? " / " + limit : ""),
                collectionFields(location, search, page, cards, accepted, limit, outcomes),
                switch (message == null ? "" : message) {
                    case "limit_reached" -> "Accepted-vacancy limit reached.";
                    case "plan_exhausted" -> "Search plan exhausted.";
                    default -> message == null ? "" : message;
                });
    }

    static CollectionProgress starting(String runId, String action, int limit) {
        return new CollectionProgress(runId, action, "", "", 0, 0, 0, limit, Map.of(), "");
    }

    private static Map<String, String> collectionFields(String location, String search,
            int page, int cards, int accepted, int limit, Map<String, Integer> outcomes) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Location", location == null ? "" : location);
        fields.put("Search", switch (search == null ? "" : search) {
            case "account_remote" -> "AccountRemote";
            case "priority" -> "Priority companies";
            case "general" -> "General";
            case "seed" -> "Session seed";
            default -> search == null ? "" : search;
        });
        fields.put("Page", page > 0 ? Integer.toString(page) : "");
        fields.put("Cards on page", Integer.toString(cards));
        int processed = outcomes.values().stream().mapToInt(Integer::intValue).sum()
                - outcomes.getOrDefault("not_processed_due_to_limit", 0);
        fields.put("Processed", Integer.toString(processed));
        fields.put("Accepted / limit", accepted + (limit > 0 ? " / " + limit : ""));
        fields.put("Skipped by preview", Integer.toString(outcomes.getOrDefault("preview_filtered", 0)));
        fields.put("Rejected by age", Integer.toString(outcomes.getOrDefault("age_filtered", 0)));
        fields.put("Rejected by content", Integer.toString(outcomes.getOrDefault("content_filtered", 0)));
        fields.put("Open failures", Integer.toString(outcomes.getOrDefault("open_failed", 0)));
        fields.put("Incomplete data", Integer.toString(outcomes.getOrDefault("incomplete_raw", 0)));
        fields.put("Skipped at limit", Integer.toString(outcomes.getOrDefault("not_processed_due_to_limit", 0)));
        return fields;
    }

    private static String actionName(String action) {
        return switch (action) {
            case "starting" -> "Starting collection";
            case "starting_browser" -> "Starting browser";
            case "checking_session" -> "Checking LinkedIn session";
            case "opening_seed" -> "Preparing search session";
            case "opening_search" -> "Opening search";
            case "reading_cards" -> "Reading search results";
            case "filtering_preview" -> "Filtering preview";
            case "opening_job" -> "Loading vacancy";
            case "filtering_content" -> "Saving and filtering content";
            case "processing" -> "Processing vacancies";
            case "complete" -> "Collection complete";
            case "login_required" -> "LinkedIn login required";
            case "blocked" -> "Collection stopped";
            default -> action;
        };
    }
}
