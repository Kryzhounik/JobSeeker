package com.seeker.collector.justjoin;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.seeker.collector.common.*;
import com.seeker.collector.core.CollectionAccumulator;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class JustJoinContractTest {
    @Test void actualOfferFieldsKeepNativeLanguageAndTechnologyLevelsThroughTheContract() throws Exception {
        JsonObject offer;
        try (var input = new InputStreamReader(getClass().getResourceAsStream("/justjoin/offer.json"), StandardCharsets.UTF_8)) {
            offer = JsonSupport.GSON.fromJson(input, JsonObject.class);
        }
        Map<String, Object> fields = JsonSupport.GSON.fromJson(offer,
                new TypeToken<Map<String, Object>>(){}.getType());
        fields.remove("body");
        Preview preview = new Preview("justjoin", offer.get("slug").getAsString(), offer.get("url").getAsString(),
                offer.get("title").getAsString(), offer.get("companyName").getAsString(),
                offer.get("city").getAsString(), offer.get("workplaceType").getAsString(), "", "fixture", 0, 1);
        PreparedVacancy prepared = new PreparedVacancy(preview, "Source-prepared Java vacancy description", null, fields);
        PreparedVacancy restored = JsonSupport.GSON.fromJson(JsonSupport.GSON.toJson(prepared), PreparedVacancy.class);
        assertEquals("justjoin", restored.preview().source());
        assertEquals("Source-prepared Java vacancy description", restored.description());
        assertEquals("en", ((Map<?, ?>)((List<?>)restored.fields().get("languages")).getFirst()).get("code"));
        assertEquals("B2", ((Map<?, ?>)((List<?>)restored.fields().get("languages")).getFirst()).get("level"));
        Map<?, ?> skill = (Map<?, ?>)((List<?>)restored.fields().get("requiredSkills")).getFirst();
        assertEquals("Java", skill.get("name"));
        assertEquals(5, ((Number)skill.get("level")).intValue());
        assertTrue(restored.fields().containsKey("niceToHaveSkills"));
        assertTrue(restored.fields().containsKey("employmentTypes"));
        assertTrue(restored.fields().containsKey("locations"));

        var sink = new CollectionAccumulator();
        sink.record(new ProcessResult("justjoin", preview.jobId(), preview.title(), "raw_saved", "", restored.fields()));
        var report = sink.report("justjoin", "run", new SourceResult("complete", "done", ""), List.of());
        assertEquals(restored.fields(), report.scope().getFirst().fields());
        ScopeItem output = JsonSupport.GSON.fromJson(JsonSupport.GSON.toJson(report.scope().getFirst()), ScopeItem.class);
        assertEquals(restored.fields(), output.fields());
        assertEquals(preview.jobId(), report.scope().getFirst().jobId());
    }
}
