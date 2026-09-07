package com.seeker.collector.linkedin.collection;

import com.seeker.collector.linkedin.config.CollectorConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchPlanTest {
    @Test
    void buildsBrowserOnlyPlanWithRemoteSeedAndPriorityCountryPasses() {
        CollectorConfig config = config(List.of(
                "AccountRemote",
                "Moldova:106178099",
                "Ukraine:102264497"
        ));

        List<SearchPlan.Target> plan = SearchPlan.build(
                config,
                null,
                List.of("101", "202")
        );

        assertEquals(
                List.of(
                        SearchPlan.Kind.SEED,
                        SearchPlan.Kind.ACCOUNT_REMOTE,
                        SearchPlan.Kind.PRIORITY,
                        SearchPlan.Kind.GENERAL,
                        SearchPlan.Kind.PRIORITY,
                        SearchPlan.Kind.GENERAL
                ),
                plan.stream().map(SearchPlan.Target::kind).toList()
        );
        assertEquals("Moldova", plan.get(0).expectedLocation());
        assertEquals("Moldova", plan.get(1).expectedLocation());

        Map<String, String> accountRemote = query(plan.get(1).baseUrl());
        assertFalse(accountRemote.containsKey("location"));
        assertFalse(accountRemote.containsKey("geoId"));
        assertEquals("2", accountRemote.get("f_WT"));

        Map<String, String> priority = query(plan.get(2).baseUrl());
        assertEquals("Moldova", priority.get("location"));
        assertEquals("106178099", priority.get("geoId"));
        assertEquals("101,202", priority.get("f_C"));
        assertEquals("2,3,1", priority.get("f_WT"));

        assertFalse(query(plan.get(3).baseUrl()).containsKey("f_C"));
        assertTrue(plan.stream().allMatch(
                target -> target.baseUrl().startsWith("https://www.linkedin.com/jobs/search/")
        ));
        assertTrue(plan.stream().noneMatch(
                target -> target.baseUrl().contains("jobs-guest")
        ));
    }

    @Test
    void locationOverrideDoesNotRunOtherConfiguredLocations() {
        CollectorConfig config = config(List.of(
                "AccountRemote",
                "Moldova:106178099",
                "Ukraine:102264497"
        ));

        List<SearchPlan.Target> plan = SearchPlan.build(
                config,
                "Ukraine:102264497",
                List.of("101")
        );

        assertEquals(2, plan.size());
        assertEquals(List.of("Ukraine:priority", "Ukraine"),
                plan.stream().map(SearchPlan.Target::label).toList());
    }

    @Test
    void accountRemoteAlwaysPrecedesConfiguredCountries() {
        CollectorConfig config = config(List.of(
                "Moldova:106178099",
                "AccountRemote",
                "Ukraine:102264497"
        ));

        List<SearchPlan.Target> plan = SearchPlan.build(config, null, List.of());

        assertEquals(
                List.of(
                        SearchPlan.Kind.SEED,
                        SearchPlan.Kind.ACCOUNT_REMOTE,
                        SearchPlan.Kind.GENERAL,
                        SearchPlan.Kind.GENERAL
                ),
                plan.stream().map(SearchPlan.Target::kind).toList()
        );
        assertEquals("Moldova", plan.get(0).expectedLocation());
        assertEquals("Moldova", plan.get(1).expectedLocation());
    }

    private CollectorConfig config(List<String> locations) {
        return new CollectorConfig(
                "Java",
                locations,
                List.of("associate", "mid_senior"),
                List.of("remote"),
                List.of("remote", "hybrid", "office"),
                List.of("full_time", "part_time", "contract"),
                "week",
                "newest",
                100,
                0,
                "chrome",
                30,
                20
        );
    }

    private Map<String, String> query(String url) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
            );
        }
        return result;
    }
}
