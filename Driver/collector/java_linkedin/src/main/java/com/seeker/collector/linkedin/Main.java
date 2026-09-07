package com.seeker.collector.linkedin;

import com.seeker.collector.linkedin.application.LinkedInApplication;
import com.seeker.collector.linkedin.support.JsonSupport;

import java.io.IOException;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws IOException {
        LinkedInApplication application = new LinkedInApplication(Path.of("."));
        Object result = switch (args[0]) {
            case "login" -> application.login();
            case "batch" -> application.batch();
            default -> throw new IllegalArgumentException("Expected login or batch");
        };
        System.out.println(JsonSupport.GSON.toJson(result));
    }
}
