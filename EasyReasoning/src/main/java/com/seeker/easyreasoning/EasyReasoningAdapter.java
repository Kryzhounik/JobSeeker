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
    public String generate(@Parameters(arity = "1..*", paramLabel = "TEXT") String[] parts) {
        return api.generate(String.join(" ", parts));
    }

    @Command(name = "clean-vacancy")
    public String cleanVacancy(@Parameters(arity = "1..*", paramLabel = "TEXT") String[] parts) {
        return api.cleanVacancy(String.join(" ", parts));
    }
}
