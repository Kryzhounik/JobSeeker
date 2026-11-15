package com.seeker.collector.common;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Complete user search preferences for one run. Technical settings are loaded separately. */
public record CollectionSettings(List<String> sources, LinkedIn linkedin, JustJoin justjoin) {
    public CollectionSettings {
        sources = List.copyOf(sources);
        validateSources(sources);
        if (sources.contains("linkedin")) Objects.requireNonNull(linkedin, "LinkedIn settings are required");
        if (sources.contains("justjoin")) Objects.requireNonNull(justjoin, "JustJoin settings are required");
    }

    public static void validateSources(List<String> sources) {
        if (sources.isEmpty() || new HashSet<>(sources).size() != sources.size()
                || sources.stream().anyMatch(source -> !List.of("linkedin", "justjoin").contains(source)))
            throw new IllegalArgumentException("Expected distinct sources: linkedin, justjoin");
    }

    /** One LinkedIn keyword/query, with its ordered locations and search filters. */
    public record LinkedIn(String keywords, List<String> locations, List<String> experience,
                           List<String> remoteBroadWorkplace, List<String> workplace, List<String> jobTypes,
                           String datePosted, String sort, int limit) {
        public LinkedIn {
            keywords = Objects.requireNonNull(keywords).trim();
            locations = List.copyOf(locations);
            experience = List.copyOf(experience);
            remoteBroadWorkplace = List.copyOf(remoteBroadWorkplace);
            workplace = List.copyOf(workplace);
            jobTypes = List.copyOf(jobTypes);
            datePosted = Objects.requireNonNull(datePosted).trim();
            sort = Objects.requireNonNull(sort).trim();
            if (limit <= 0) throw new IllegalArgumentException("LinkedIn collector limit must be positive");
        }
    }

    /** One JustJoinIT technology category. An empty publication window means no age filter. */
    public record JustJoin(String mainTech, String location, String publishedDays, List<String> languages,
                           List<String> experienceLevels, String orderBy, String sortBy, int limit) {
        public JustJoin {
            mainTech = Objects.requireNonNull(mainTech).trim();
            location = Objects.requireNonNull(location).trim();
            publishedDays = Objects.requireNonNull(publishedDays).trim();
            languages = List.copyOf(languages);
            experienceLevels = List.copyOf(experienceLevels);
            orderBy = Objects.requireNonNull(orderBy).trim();
            sortBy = Objects.requireNonNull(sortBy).trim();
            if (mainTech.isBlank() || limit < 0) throw new IllegalArgumentException("Invalid JustJoin search settings");
        }
    }
}
