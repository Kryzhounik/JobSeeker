package com.seeker.easyreasoning;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** Command declarations; the API owns operations and the shared runner owns output. */
@Command(name = "easy-reasoning")
public final class EasyReasoningAdapter {
    private final EasyReasoningApi api;

    public EasyReasoningAdapter(EasyReasoningApi api) {
        this.api = api;
    }

    @Command(name = "generate")
    public String generate(@Parameters(index = "0", arity = "1", paramLabel = "TEXT") String text) {
        return api.generate(text);
    }

    @Command(name = "clean-vacancy")
    public String cleanVacancy(@Parameters(index = "0", arity = "1", paramLabel = "TEXT") String text) {
        return api.cleanVacancy(text);
    }
}
