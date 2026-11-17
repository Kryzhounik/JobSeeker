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
                        (duplicate, original) -> database.saveDecision(batchId, duplicate, original));
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
        Set<Long> unresolved = new HashSet<>();
        for (VacancySnapshot vacancy : vacancies) {
            String title = vacancy.title().replaceAll("[\\s\\p{Z}]+", " ").strip().toLowerCase(Locale.ROOT);
            if (vacancy.companyId() == null || title.isBlank()) {
                if (!vacancy.checked()) save.accept(vacancy.id(), null);
                continue;
            }
            Key key = new Key(vacancy.companyId(), title);
            var group = samples.computeIfAbsent(key, k -> new ArrayList<>());
            if (vacancy.checked()) {
                if (vacancy.duplicateOf() == null) group.add(vacancy);
                continue;
            }
            boolean incomplete = false;
            VacancySnapshot original = null;
            for (var sample : group) {
                boolean duplicate;
                try { duplicate = compare.test(sample.text(), vacancy.text()); }
                catch (RuntimeException error) {
                    System.err.println("Semantic comparison failed: " + sample.source() + ":" + sample.jobId() + " vs "
                            + vacancy.source() + ":" + vacancy.jobId() + ": " + error);
                    incomplete = true;
                    continue;
                }
                if (duplicate) {
                    if (unresolved.contains(sample.id())) { incomplete = true; continue; }
                    original = sample;
                    break;
                }
            }
            if (original == null && incomplete) {
                unresolved.add(vacancy.id());
                group.add(vacancy);
                continue;
            }
            if (original == null) {
                save.accept(vacancy.id(), null);
                group.add(vacancy);
            }
            else {
                save.accept(vacancy.id(), original.id());
                System.err.println("Semantic duplicate: " + vacancy.source() + ":" + vacancy.jobId()
                        + " -> " + original.source() + ":" + original.jobId());
            }
        }
    }
}
