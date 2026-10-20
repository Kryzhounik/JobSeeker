package com.seeker.guifx;

import java.util.List;
import java.util.Objects;

record JobFilters(List<String> statuses, boolean showZero, boolean showUnavailable,
                  String idQuery, String addedFrom, List<String> reasons) {
    JobFilters {
        statuses = List.copyOf(statuses);
        reasons = List.copyOf(reasons);
        Objects.requireNonNull(idQuery);
        Objects.requireNonNull(addedFrom);
    }
}
