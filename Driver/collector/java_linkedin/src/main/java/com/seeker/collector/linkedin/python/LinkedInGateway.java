package com.seeker.collector.linkedin.python;

import com.google.gson.reflect.TypeToken;
import com.seeker.collector.common.*;
import org.jpy.PyModule;
import org.jpy.PyObject;
import java.util.List;

/** Source-specific calls into the same initialized Python collector session. */
public final class LinkedInGateway implements AutoCloseable {
    private final PyModule bridge = PyModule.importModule("collector.java_linkedin.python_bridge");

    public List<String> priorityCompanyIds() {
        return JsonSupport.GSON.fromJson(call("priority_company_ids"), new TypeToken<List<String>>(){}.getType());
    }

    public void logPage(String runId, PageReport page) {
        try (PyObject ignored = bridge.callMethod("log_page", runId, JsonSupport.GSON.toJson(page))) { }
    }

    public Preparation prepare(Preview preview, String html, boolean easyApply) {
        return JsonSupport.GSON.fromJson(call("prepare_linkedin_html", JsonSupport.GSON.toJson(preview), html, easyApply),
                Preparation.class);
    }

    private String call(String name, Object... args) {
        try (PyObject result = bridge.callMethod(name, args)) { return result.getStringValue(); }
    }

    @Override public void close() { bridge.close(); }

    public record Preparation(PreparedVacancy vacancy, ProcessResult rejection) { }
}
