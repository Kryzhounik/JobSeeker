package com.seeker.collector.common;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Map;

/** Omit absent new metadata even when a legacy caller uses Gson.serializeNulls(). */
public final class OptionalFieldsAdapter extends TypeAdapter<Map<String, Object>> {
    private final TypeAdapter<Map<String, Object>> delegate = new Gson()
            .getAdapter(new TypeToken<Map<String, Object>>(){});

    @Override public void write(JsonWriter out, Map<String, Object> value) throws IOException {
        if (value != null) {
            delegate.write(out, value);
            return;
        }
        boolean serializeNulls = out.getSerializeNulls();
        out.setSerializeNulls(false);
        try { out.nullValue(); }
        finally { out.setSerializeNulls(serializeNulls); }
    }

    @Override public Map<String, Object> read(JsonReader in) throws IOException { return delegate.read(in); }
}
