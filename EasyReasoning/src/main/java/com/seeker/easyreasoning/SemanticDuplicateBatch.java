package com.seeker.easyreasoning;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.BiConsumer;

/** Synchronous observational deduplication of a saved collection batch. */
final class SemanticDuplicateBatch {
    private final Path root;
    private final BiPredicate<String, String> compare;
    private final Runnable release;
    private record Key(long companyId, String title) { }

    SemanticDuplicateBatch(Path root, BiPredicate<String, String> compare, Runnable release) {
        this.root = root;
        this.compare = compare;
        this.release = release;
    }

    void processBatch(String batchId) {
        try {
            Properties config = new Properties();
            Path file = root.resolve("EasyReasoning/easy-reasoning.properties");
            if (Files.exists(file)) {
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { config.load(reader); }
            }
            String mode = config.getProperty("dedup.mode", "qwen").strip();
            if (mode.equals("off")) return;
            if (!mode.equals("qwen")) throw new IllegalArgumentException("Unsupported dedup.mode: " + mode);
            try (var database = new DeduplicationDatabase(root)) {
                process(database.loadBatch(batchId), compare,
                        (duplicate, original) -> database.saveLink(batchId, duplicate, original));
            } finally {
                try { release.run(); }
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
