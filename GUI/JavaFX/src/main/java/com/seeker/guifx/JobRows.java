package com.seeker.guifx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class JobRows {
    static void sort(javafx.collections.ObservableList<JobRecord> rows, Comparator<JobRecord> comparator) {
        List<JobRecord> ordered = sorted(rows, comparator);
        Map<JobRecord, Integer> positions = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) positions.put(ordered.get(i), i);
        // A permutation preserves JavaFX selection; replacing all rows during sort does not.
        javafx.collections.FXCollections.sort(rows, Comparator.comparingInt(positions::get));
    }

    static List<JobRecord> sorted(List<JobRecord> rows, Comparator<JobRecord> comparator) {
        Map<Integer, JobRecord> existing = new HashMap<>();
        for (JobRecord row : rows) existing.put(row.jobId(), row);
        Map<Integer, Group> groups = new LinkedHashMap<>();
        for (JobRecord job : rows) {
            if (job.contextForJobId() != null) continue;
            int parentId = job.duplicateParent() == null ? job.jobId() : job.duplicateParent().jobId();
            Group group = groups.computeIfAbsent(parentId, id -> {
                JobRecord parent = existing.get(id);
                // Reuse displayed parents so header sorting remains a pure permutation.
                if (parent == null) parent = job.duplicateParent().asParentFor(job.jobId());
                return new Group(parent, new ArrayList<>());
            });
            if (job.jobId() != parentId) group.children().add(job);
        }
        List<Group> ordered = new ArrayList<>(groups.values());
        if (comparator != null) ordered.sort(Comparator.comparing(Group::parent, comparator));
        List<JobRecord> displayed = new ArrayList<>();
        for (Group group : ordered) {
            displayed.add(group.parent());
            displayed.addAll(group.children());
        }
        return displayed;
    }

    static Set<Integer> parentIds(List<JobRecord> rows) {
        return rows.stream().filter(row -> row.duplicateParent() != null)
                .map(row -> row.duplicateParent().jobId()).collect(Collectors.toSet());
    }

    private record Group(JobRecord parent, List<JobRecord> children) { }
}
