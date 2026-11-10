package com.seeker.guifx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class JobRows {
    static void sort(javafx.collections.ObservableList<JobRecord> rows, Comparator<JobRecord> comparator) {
        List<JobRecord> ordered = sorted(rows, comparator);
        java.util.Map<JobRecord, Integer> positions = new java.util.HashMap<>();
        for (int i = 0; i < ordered.size(); i++) positions.put(ordered.get(i), i);
        // A permutation preserves JavaFX selection; replacing all rows during sort does not.
        javafx.collections.FXCollections.sort(rows, Comparator.comparingInt(positions::get));
    }
    static List<JobRecord> sorted(List<JobRecord> rows, Comparator<JobRecord> comparator) {
        List<JobRecord> jobs = new ArrayList<>(rows.stream()
                .filter(row -> row.contextForJobId() == null).toList());
        if (comparator != null) jobs.sort(comparator);
        List<JobRecord> displayed = new ArrayList<>();
        for (JobRecord job : jobs) {
            if (job.duplicateParent() != null) displayed.add(job.duplicateParent().asParentFor(job.jobId()));
            displayed.add(job);
        }
        return displayed;
    }
}
