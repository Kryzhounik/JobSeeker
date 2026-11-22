package com.seeker.guifx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Playwright;
import com.seeker.api.DriverApi;
import com.seeker.collector.linkedin.application.LinkedInApplication;
import com.seeker.orchestrator.WorkflowOrchestrator;
import javafx.fxml.FXMLLoader;
import org.jpy.PyModule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

public final class FatJarSmoke {
    public static void main(String[] args) throws Exception {
        Path jarPath = source(SeekerLauncher.class);
        if (!Files.isRegularFile(jarPath)) throw new AssertionError("Run this check against the packaged GUI jar");
        for (Class<?> type : List.of(DriverApi.class, LinkedInApplication.class,
                WorkflowOrchestrator.class, Playwright.class, FXMLLoader.class,
                PyModule.class, ObjectMapper.class)) {
            if (!Files.isSameFile(jarPath, source(type))) {
                throw new AssertionError(type.getName() + " was loaded outside the fat GUI jar");
            }
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var attributes = jar.getManifest().getMainAttributes();
            if (attributes.getValue("Class-Path") != null
                    || !SeekerLauncher.class.getName().equals(attributes.getValue("Main-Class"))) {
                throw new AssertionError("GUI manifest still uses an external classpath or wrong launcher");
            }
            if (jar.getEntry("com/seeker/guifx/main-view.fxml") == null
                    || jar.getEntry("com/seeker/guifx/application.css") == null
                    || jar.stream().noneMatch(entry -> entry.getName().endsWith("glass.dll"))
                    || jar.stream().noneMatch(entry -> entry.getName().endsWith("/node.exe"))
                    || jar.stream().noneMatch(entry -> entry.getName().startsWith("META-INF/services/"))) {
                throw new AssertionError("Bundled GUI resources, native runtime, or services are missing");
            }
        }
        System.out.println("Fat GUI jar isolation OK; no external runtime jars");
    }

    private static Path source(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
