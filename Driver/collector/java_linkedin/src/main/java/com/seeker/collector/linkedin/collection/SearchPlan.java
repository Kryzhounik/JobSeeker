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
    static final String SEARCH_URL = "https://www.linkedin.com/jobs/search-results/";
    private static final Map<String, String> EXPERIENCE = Map.of(
            "entry_level", "276001",
            "manager", "278001",
            "senior", "277001"
    );
    private static final Map<String, String> WORKPLACE = Map.of(
            "office", "296001",
            "remote", "272001",
            "hybrid", "1621001"
    );
    private static final Map<String, String> JOB_TYPES = Map.of(
            "full_time", "272015",
            "part_time", "273001",
            "contract", "274001"
    );
    private static final Map<String, String> DATE_POSTED = Map.of(
            "day", "r86400",
            "week", "r604800",
            "month", "r2592000"
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
            List<String> priorityCompanyIds
    ) {
        if (!config.sort().isBlank()) {
            System.err.println("LinkedIn AI search has no sort control; ignoring sort=" + config.sort());
        }
        List<String> configured = config.locations();
        String anchor = configured.stream()
                .filter(item -> !isLocationless(item))
                .findFirst()
                .orElse("");
        List<String> locations = new ArrayList<>();
        // AccountRemote owns the first pass even if somebody later moves it
        // in the properties file. Preserve configured order within the
        // locationless and real-country groups.
        configured.stream().filter(SearchPlan::isLocationless).forEach(locations::add);
        configured.stream().filter(item -> !isLocationless(item)).forEach(locations::add);
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
                        Kind.SEED,
                        searchUrl(config, anchor, true, List.of())
                ));
                targets.add(new Target(
                        locationName(rawLocation),
                        Kind.ACCOUNT_REMOTE,
                        searchUrl(config, rawLocation, true, List.of())
                ));
                continue;
            }

            String name = locationName(rawLocation);
            if (!priorityCompanyIds.isEmpty()) {
                targets.add(new Target(
                        name + ":priority",
                        Kind.PRIORITY,
                        searchUrl(config, rawLocation, false, priorityCompanyIds)
                ));
            }
            targets.add(new Target(
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
        if (!locationless) {
            if (!geoId.isBlank()) {
                parameters.put("geoId", geoId);
            } else {
                parameters.put("location", location);
            }
        }
        List<String> filters = new ArrayList<>();
        addFilter(filters, "225001", remoteBroad ? config.remoteBroadWorkplace() : config.workplace(), WORKPLACE);
        addFilter(filters, "227001", config.experience(), EXPERIENCE);
        addFilter(filters, "226001", config.jobTypes(), JOB_TYPES);
        parameters.put("f_SAL", String.join("$", filters));
        parameters.put("f_TPR", mapped(List.of(config.datePosted()), DATE_POSTED));
        if (!priorityCompanyIds.isEmpty()) {
            parameters.put("f_C", String.join(",", priorityCompanyIds));
        }

        String query = parameters.entrySet().stream()
                .filter(entry -> !entry.getValue().isBlank())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return SEARCH_URL + "?" + query;
    }

    private static void addFilter(List<String> filters, String id, List<String> values, Map<String, String> mapping) {
        String value = mapped(values, mapping);
        if (!value.isBlank()) filters.add("f_SA_id_" + id + ":" + value);
    }

    private static String mapped(List<String> rawValues, Map<String, String> mapping) {
        return rawValues.stream()
                .filter(item -> !item.isBlank())
                .map(item -> mapping.getOrDefault(normalize(item), item))
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
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
