package com.seeker.collector.linkedin.collection;

import com.seeker.collector.linkedin.config.ProjectPaths;
import com.seeker.collector.linkedin.python.JpyPythonGateway;
import com.seeker.collector.linkedin.python.PythonRuntime;
import com.seeker.easyreasoning.EasyReasoningApi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.BiConsumer;

/** Synchronous observational deduplication of a saved collection batch. */
public final class SemanticDuplicateBatch {
    private final Path root;
    private record Key(long companyId, String title) { }

    public SemanticDuplicateBatch(Path root) { this.root = root; }

    public void processBatch(String batchId) {
        try {
            Properties config = new Properties();
            Path file = root.resolve("EasyReasoning/easy-reasoning.properties");
            if (Files.exists(file)) {
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { config.load(reader); }
            }
            String mode = config.getProperty("dedup.mode", "qwen").strip();
            if (mode.equals("off")) return;
            if (!mode.equals("qwen")) throw new IllegalArgumentException("Unsupported dedup.mode: " + mode);
            ProjectPaths paths = ProjectPaths.fromProjectRoot(root);
            PythonRuntime runtime = PythonRuntime.load(paths.collectorRoot().resolve("java_linkedin/runtime.properties"));
            var reasoning = new EasyReasoningApi(root);
            try (var python = new JpyPythonGateway(paths, runtime, reasoning)) {
                process(python.loadSemanticBatch(batchId), reasoning::areDuplicateDescriptions,
                        (duplicate, original) -> python.saveSemanticBatchLink(batchId, duplicate, original));
            } finally {
                try { reasoning.close(); }
                catch (RuntimeException error) {
                    System.err.println("Semantic comparison model cleanup failed: " + error);
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    static void process(List<VacancySnapshot> vacancies, BiPredicate<String, String> compare,
                        BiConsumer<Long, Long> save) {
        Map<Key, List<VacancySnapshot>> samples = new HashMap<>();
        for (VacancySnapshot vacancy : vacancies) {
            String title = vacancy.title().replaceAll("[\\s\\p{Z}]+", " ").strip().toLowerCase(Locale.ROOT);
            if (vacancy.companyId() == null || title.isBlank()) continue;
            var group = samples.computeIfAbsent(new Key(vacancy.companyId(), title), k -> new ArrayList<>());
            VacancySnapshot original = null;
            for (var sample : group) {
                boolean duplicate;
                try { duplicate = compare.test(sample.text(), vacancy.text()); }
                catch (RuntimeException error) {
                    System.err.println("Semantic comparison failed: " + sample.jobId() + " vs "
                            + vacancy.jobId() + ": " + error);
                    continue;
                }
                if (duplicate) { original = sample; break; }
            }
            if (original == null) group.add(vacancy);
            else {
                save.accept(vacancy.id(), original.id());
                System.err.println("Semantic duplicate: " + vacancy.jobId() + " -> " + original.jobId());
            }
        }
    }
}
