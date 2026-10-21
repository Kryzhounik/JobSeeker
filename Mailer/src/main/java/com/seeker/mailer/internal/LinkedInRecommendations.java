package com.seeker.mailer.internal;

import com.seeker.mailer.MailException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Provisional rules exercised by synthetic fixtures; real sender fixtures are still required. */
final class LinkedInRecommendations {
    private static final Pattern JOB_PATH = Pattern.compile("^/(?:comm/)?jobs/view/(?:[^/]*-)?([0-9]+)/?$");
    private static final Pattern RECOMMENDATION = Pattern.compile(
            "(?i)\\b(?:recommended jobs?|jobs? (?:recommended )?for you|job recommendations?|job alert)\\b");
    private static final Set<String> DESTINATION_KEYS = Set.of("url", "dest", "destination", "redirect", "redirect_uri");

    private LinkedInRecommendations() {}

    static boolean recognizes(String sender, String subject, String text) {
        int at = sender.lastIndexOf('@');
        return at >= 0 && isLinkedIn(sender.substring(at + 1))
                && RECOMMENDATION.matcher(subject + "\n" + text).find();
    }

    static List<String> extract(List<String> links) throws MailException {
        var urls = new LinkedHashSet<String>();
        for (String link : links) {
            String canonical = canonical(link, 0);
            if (canonical != null) {
                urls.add(canonical);
            }
        }
        if (urls.isEmpty()) {
            throw new MailException("Unsupported LinkedIn recommendation format: no interpretable vacancy links");
        }
        return List.copyOf(urls);
    }

    private static boolean isLinkedIn(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("linkedin.com") || lower.endsWith(".linkedin.com");
    }

    private static String canonical(String link, int depth) {
        if (depth > 4) {
            return null;
        }
        try {
            URI uri = URI.create(link);
            if (uri.getHost() == null || !isLinkedIn(uri.getHost()) || uri.getUserInfo() != null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                return null;
            }
            var matcher = JOB_PATH.matcher(uri.getPath());
            if (matcher.matches()) {
                return "https://www.linkedin.com/jobs/view/" + matcher.group(1) + "/";
            }
            // Only unwrap explicit redirect endpoints, never arbitrary footer or search parameters.
            if (!("/redir/redirect".equals(uri.getPath()) || "/safety/go".equals(uri.getPath()))
                    || uri.getRawQuery() == null) {
                return null;
            }
            for (String parameter : uri.getRawQuery().split("&")) {
                String[] pair = parameter.split("=", 2);
                if (pair.length == 2 && DESTINATION_KEYS.contains(decode(pair[0]))) {
                    String result = canonical(decode(pair[1]), depth + 1);
                    if (result != null) {
                        return result;
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Unrelated or malformed links are not vacancy URLs. The caller rejects an unsupported message.
        }
        return null;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    static String normalize(String link) {
        return canonical(link, 0);
    }
}
