package com.seeker.collector.linkedin.collection;

import com.seeker.collector.linkedin.config.CollectorConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class SearchPlan {
    static final String SEARCH_URL = "https://www.linkedin.com/jobs/search/";
    private static final Map<String, String> EXPERIENCE = Map.of(
            "associate", "3",
            "mid_senior", "4"
    );
    private static final Map<String, String> WORKPLACE = Map.of(
            "office", "1",
            "remote", "2",
            "hybrid", "3"
    );
    private static final Map<String, String> JOB_TYPES = Map.of(
            "full_time", "F",
            "part_time", "P",
            "contract", "C"
    );
    private static final Map<String, String> DATE_POSTED = Map.of(
            "day", "r86400",
            "week", "r604800",
            "month", "r2592000"
    );
    private static final Map<String, String> SORT = Map.of(
            "newest", "DD",
            "relevant", "R"
    );
    private static final Set<String> LOCATIONLESS = Set.of(
            "accountremote", "account_remote", "remote", "worldwide",
            "global", "anywhere"
    );

    enum Kind {
        SEED,
        ACCOUNT_REMOTE,
        PRIORITY,
        GENERAL
    }

    record Target(
            String label,
            String rawLocation,
            String expectedLocation,
            Kind kind,
            String baseUrl
    ) {
        String pageUrl(int start) {
            return baseUrl + (baseUrl.contains("?") ? "&" : "?") + "start=" + start;
        }

        boolean collectsJobs() {
            return kind != Kind.SEED;
        }
    }

    static List<Target> build(
            CollectorConfig config,
            String locationOverride,
            List<String> priorityCompanyIds
    ) {
        List<String> configured = config.locations();
        String anchor = configured.stream()
                .filter(item -> !isLocationless(item))
                .findFirst()
                .orElse("");
        List<String> locations = new ArrayList<>();
        if (locationOverride != null) {
            locations.add(locationOverride);
        } else {
            // AccountRemote owns the first pass even if somebody later moves it
            // in the properties file. Preserve configured order within the
            // locationless and real-country groups.
            configured.stream().filter(SearchPlan::isLocationless).forEach(locations::add);
            configured.stream().filter(item -> !isLocationless(item)).forEach(locations::add);
        }
        List<Target> targets = new ArrayList<>();

        for (String rawLocation : locations) {
            if (isLocationless(rawLocation)) {
                if (anchor.isBlank()) {
                    throw new IllegalArgumentException(
                            "AccountRemote requires at least one real country as its seed"
                    );
                }
                String anchorName = locationName(anchor);
                targets.add(new Target(
                        "seed:" + anchorName,
                        anchor,
                        anchorName,
                        Kind.SEED,
                        searchUrl(config, anchor, true, List.of())
                ));
                targets.add(new Target(
                        locationName(rawLocation),
                        rawLocation,
                        anchorName,
                        Kind.ACCOUNT_REMOTE,
                        searchUrl(config, rawLocation, true, List.of())
                ));
                continue;
            }

            String name = locationName(rawLocation);
            if (!priorityCompanyIds.isEmpty()) {
                targets.add(new Target(
                        name + ":priority",
                        rawLocation,
                        name,
                        Kind.PRIORITY,
                        searchUrl(config, rawLocation, false, priorityCompanyIds)
                ));
            }
            targets.add(new Target(
                    name,
                    rawLocation,
                    name,
                    Kind.GENERAL,
                    searchUrl(config, rawLocation, false, List.of())
            ));
        }
        return targets;
    }

    static boolean isLocationless(String rawLocation) {
        return LOCATIONLESS.contains(locationKey(rawLocation));
    }

    static String locationName(String rawLocation) {
        String name = rawLocation.split(":", 2)[0].trim();
        return name.isBlank() ? "all" : name;
    }

    private static String locationKey(String rawLocation) {
        return locationName(rawLocation)
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_');
    }

    private static String searchUrl(
            CollectorConfig config,
            String rawLocation,
            boolean remoteBroad,
            List<String> priorityCompanyIds
    ) {
        String[] locationParts = rawLocation.split(":", 2);
        String location = locationParts[0].trim();
        String geoId = locationParts.length > 1 ? locationParts[1].trim() : "";
        boolean locationless = isLocationless(rawLocation);

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("keywords", config.keywords());
        parameters.put("origin", "JOB_SEARCH_PAGE_SEARCH_BUTTON");
        parameters.put("refresh", "true");
        if (!locationless) {
            parameters.put("location", location);
            if (!geoId.isBlank()) {
                parameters.put("geoId", geoId);
            }
        }
        putMapped(parameters, "f_E", config.experience(), EXPERIENCE);
        putMapped(
                parameters,
                "f_WT",
                remoteBroad ? config.remoteBroadWorkplace() : config.workplace(),
                WORKPLACE
        );
        putMapped(parameters, "f_JT", config.jobTypes(), JOB_TYPES);
        putMapped(parameters, "f_TPR", List.of(config.datePosted()), DATE_POSTED);
        putMapped(parameters, "sortBy", List.of(config.sort()), SORT);
        if (!priorityCompanyIds.isEmpty()) {
            parameters.put("f_C", String.join(",", priorityCompanyIds));
        }

        String query = parameters.entrySet().stream()
                .filter(entry -> !entry.getValue().isBlank())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return SEARCH_URL + "?" + query;
    }

    private static void putMapped(
            Map<String, String> target,
            String key,
            List<String> rawValues,
            Map<String, String> mapping
    ) {
        String value = rawValues.stream()
                .filter(item -> !item.isBlank())
                .map(item -> mapping.getOrDefault(normalize(item), item))
                .collect(Collectors.joining(","));
        if (!value.isBlank()) {
            target.put(key, value);
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private SearchPlan() {
    }
}
