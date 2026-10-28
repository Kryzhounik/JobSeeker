package com.seeker.orchestrator;

import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.ScopeItem;
import com.seeker.tools.api.ProgressSnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Complete immutable workflow snapshot, emitted on the caller's thread. */
public record WorkflowProgress(String runId, String stage, String action, String progress,
                               Map<String, String> fields, String details) implements ProgressSnapshot {
    public WorkflowProgress {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /** Statistics and collection adaptation belong to one invocation, never to a service. */
    static final class Run {
        private final Consumer<ProgressSnapshot> listener;
        private final Map<String, String> fields = new LinkedHashMap<>();
        private Map<String, String> collectionFields;
        private String collectionProgress = "Collection";
        private String runId;
        private String stage = "Collection";
        private String action = "Starting";
        private String details = "";
        private boolean collected;
        private int savedCount;

        Run(Consumer<ProgressSnapshot> listener) {
            this.listener = Objects.requireNonNull(listener);
            collectionFields = new CollectionProgress(null, "starting", "", "", 0, 0, 0, 0,
                    Map.of(), "").fields();
            for (String label : new String[]{"Scope", "Relevant", "Rejected by relevance", "Already saved", "Saved"}) {
                count(label, 0);
            }
            for (String label : new String[]{"Relevance filter", "Job facts", "Candidate fit"}) {
                fields.put(label, "0 / 0");
            }
            fields.put("Current vacancy", "");
        }

        void collection(ProgressSnapshot update) {
            collected = true;
            identify(update.runId());
            stage = update.stage();
            action = update.action();
            details = update.details();
            collectionFields = update.fields();
            collectionProgress = update.progress();
            emit();
        }

        void collectionResult(CollectionReport report) {
            if (!collected) {
                collection(new CollectionProgress(report.runId(), report.status(), "", "", 0, 0,
                        report.acceptedCount(), 0, report.outcomes(), report.message()));
            }
        }

        void identify(String value) {
            runId = value;
        }

        void count(String label, int value) {
            fields.put(label, Integer.toString(value));
        }

        void total(String stage, int total) {
            fields.put(stage, "0 / " + total);
        }

        void preparing(String stage, String action) {
            this.stage = stage;
            this.action = action;
            details = "";
            fields.put("Current vacancy", "");
            emit();
        }

        void work(String stage, String action, int completed, int total, ScopeItem job) {
            this.stage = stage;
            this.action = action;
            fields.put(stage, completed + " / " + total);
            fields.put("Current vacancy", job == null ? "" : job.jobId());
            details = job == null ? "" : job.title();
            emit();
        }

        void saved(int confirmed) {
            savedCount += confirmed;
            count("Saved", savedCount);
        }

        int savedCount() {
            return savedCount;
        }

        void complete(String message) {
            action = "Complete";
            details = message;
            fields.put("Current vacancy", "");
            emit();
        }

        private void emit() {
            String progress = stage.equals("Collection") ? collectionProgress : stage + " " + fields.get(stage);
            if (stage.equals("Collection") && !action.equals("Complete")) {
                listener.accept(new WorkflowProgress(runId, stage, action, progress, collectionFields, details));
                return;
            }
            String[] labels = action.equals("Complete")
                    ? new String[]{"Scope", "Relevant", "Rejected by relevance",
                            "Job facts", "Candidate fit", "Already saved", "Saved"}
                    : switch (stage) {
                        case "Relevance filter" -> new String[]{"Scope", "Relevance filter",
                                "Relevant", "Rejected by relevance"};
                        case "Job facts" -> new String[]{"Job facts", "Current vacancy"};
                        case "Candidate fit" -> new String[]{"Candidate fit", "Current vacancy"};
                        default -> throw new IllegalStateException("Unknown workflow stage: " + stage);
                    };
            Map<String, String> currentFields = new LinkedHashMap<>();
            for (String label : labels) currentFields.put(label, fields.get(label));
            listener.accept(new WorkflowProgress(runId, stage, action, progress, currentFields, details));
        }
    }
}
