package com.seeker.collector.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class JsonSupport {
    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();
    private JsonSupport() {
    }
}
