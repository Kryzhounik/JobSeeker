package com.seeker.collector.justjoin;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.seeker.collector.common.*;
import com.seeker.tools.api.ImmutableProgressSnapshot;
import com.seeker.tools.api.ProgressSnapshot;
import org.jsoup.Jsoup;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

/** JustJoin HTTP traversal and preparation, using the common collection operations. */
public final class JustJoinSource implements CollectorSource {
    private final JustJoinConfig config;
    private final JustJoinHttpClient http;
    private final String runId;
    private final Consumer<ProgressSnapshot> progress;
    private CollectionSink sink;
    private int offset;
    private int index;

    public JustJoinSource(JustJoinConfig config, String runId, Consumer<ProgressSnapshot> progress) {
        this(config, new JustJoinHttpClient(config), runId, progress);
    }

    JustJoinSource(JustJoinConfig config, JustJoinHttpClient http, String runId, Consumer<ProgressSnapshot> progress) {
        this.config = config;
        this.http = http;
        this.runId = runId;
        this.progress = progress;
    }

    @Override public String source() { return "justjoin"; }

    @Override public SourceResult collectBatch(VacancyProcessor processor, CollectionSink sink) throws IOException {
        this.sink = sink;
        progress.accept(snapshot("Loading offer count", ""));
        int count = http.get("offers/count?" + config.query()).get("count").getAsInt();
        Set<String> seen = new HashSet<>();
        for (offset = 0; offset < count; offset += config.itemsCount()) {
            if (atLimit()) return complete("limit_reached");
            progress.accept(snapshot("Loading search results", ""));
            var offers = http.get("offers?" + config.query() + "&from=" + offset + "&itemsCount=" + config.itemsCount())
                    .getAsJsonArray("data");
            if (offers.isEmpty()) break;
            index = 0;
            for (var item : offers) {
                if (atLimit()) return complete("limit_reached");
                index++;
                var offer = item.getAsJsonObject();
                String slug = offer.get("slug").getAsString();
                if (seen.add(slug)) collect(preview(offer, slug), null, processor);
            }
        }
        return complete(atLimit() ? "limit_reached" : "plan_exhausted");
    }

    @Override public SourceResult collectUrls(List<String> urls, VacancyProcessor processor, CollectionSink sink) throws IOException {
        this.sink = sink;
        for (String url : urls) {
            index++;
            URI uri = URI.create(url);
            if (!"justjoin.it".equalsIgnoreCase(uri.getHost()) || !uri.getPath().startsWith("/job-offer/"))
                throw new IllegalArgumentException("Not a JustJoin offer URL: " + url);
            String slug = uri.getPath().substring("/job-offer/".length()).replaceAll("/$", "");
            progress.accept(snapshot("Loading vacancy", url));
            JsonObject full = http.get("offers/" + slug);
            collect(preview(full, slug), full, processor);
        }
        return complete("urls_exhausted");
    }

    private void collect(Preview preview, JsonObject full, VacancyProcessor processor) throws IOException {
        progress.accept(snapshot("Filtering preview", preview.sourceUrl()));
        PreviewDecision decision = processor.decidePreview(preview);
        if (!decision.shouldOpen()) {
            processor.logOutcome(preview, "preview_filtered", decision.reason());
            sink.record(new ProcessResult(source(), preview.jobId(), preview.title(), "preview_filtered", decision.reason()));
        } else {
            progress.accept(snapshot("Loading vacancy", preview.sourceUrl()));
            if (full == null) full = http.get("offers/" + preview.jobId());
            // Full offer metadata is authoritative; identity remains the requested publication slug.
            PreparedVacancy vacancy = prepare(full, preview.jobId());
            processor.saveRaw(vacancy.preview(), full.toString(), "json", "script");
            progress.accept(snapshot("Saving and filtering content", preview.sourceUrl()));
            sink.record(processor.process(vacancy));
        }
        progress.accept(snapshot("Processing vacancies", ""));
    }

    PreparedVacancy prepare(JsonObject offer, String slug) {
        var document = Jsoup.parseBodyFragment(offer.get("body").getAsString());
        document.select("script,style").remove();
        // Preserve paragraph/list boundaries and decode HTML entities without custom HTML parsing.
        document.select("p,div,li,h1,h2,h3,h4,h5,h6,tr").prepend("\n");
        String description = document.body().wholeText().strip();
        Map<String, Object> fields = JsonSupport.GSON.fromJson(offer,
                new TypeToken<Map<String, Object>>(){}.getType());
        fields.remove("body");
        return new PreparedVacancy(preview(offer, slug), description, null, fields);
    }

    private Preview preview(JsonObject offer, String slug) {
        return new Preview(source(), slug, "https://justjoin.it/job-offer/" + slug,
                text(offer, "title"), text(offer, "companyName"), text(offer, "city"),
                text(offer, "workplaceType"), "", "justjoin", offset, index);
    }

    private static String text(JsonObject offer, String key) {
        var value = offer.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private boolean atLimit() { return config.limit() > 0 && sink.acceptedCount() >= config.limit(); }
    private SourceResult complete(String reason) { return new SourceResult("complete", reason, ""); }

    @Override public ProgressSnapshot startingSnapshot(String action, Integer urlCount) { return snapshot(action, ""); }

    @Override public ProgressSnapshot snapshot(String status, String message) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("Source", source());
        fields.put("Offset", Integer.toString(offset));
        fields.put("Accepted", Integer.toString(sink == null ? 0 : sink.acceptedCount()));
        fields.put("Limit", Integer.toString(config.limit()));
        fields.put("Processed", Integer.toString(sink == null ? 0 : sink.outcomes().values().stream().mapToInt(Integer::intValue).sum()));
        return new ImmutableProgressSnapshot(runId, "Collection", status,
                "Collection " + fields.get("Accepted") + (config.limit() > 0 ? " / " + config.limit() : ""), fields, message);
    }
}
