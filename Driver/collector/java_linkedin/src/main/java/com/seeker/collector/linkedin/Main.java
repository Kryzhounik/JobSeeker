package com.seeker.collector.linkedin;

import com.seeker.collector.linkedin.application.LinkedInApplication;
import com.seeker.collector.linkedin.support.JsonSupport;
import com.seeker.collector.linkedin.support.ProcessLog;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        PrintStream consoleErr = System.err;
        int exitCode = 0;
        try (ProcessLog log = ProcessLog.install(Path.of("."))) {
            System.err.println("LinkedIn collector log: " + log.path());
            try {
                run(args);
            } catch (Throwable error) {
                error.printStackTrace(System.err);
                exitCode = 1;
            }
        } catch (IOException error) {
            error.printStackTrace(consoleErr);
            exitCode = 1;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static void run(String[] args) throws IOException {
        LinkedInApplication application = new LinkedInApplication(Path.of("."));
        Object result = switch (args[0]) {
            case "login" -> application.login();
            case "batch" -> application.batch();
            default -> throw new IllegalArgumentException("Expected login or batch");
        };
        System.out.println(JsonSupport.GSON.toJson(result));
    }
}
