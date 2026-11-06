package com.seeker.guifx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class JobRows {
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
