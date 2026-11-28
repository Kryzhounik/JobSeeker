package com.seeker.easyreasoning;

import java.io.*;
import com.seeker.logging.LogInput;
import com.seeker.logging.LogOutput;
import com.seeker.logging.LogException;
import org.slf4j.event.Level;
import java.util.function.Consumer;
import com.seeker.tools.api.ProgressSnapshot;
import com.seeker.tools.api.ImmutableProgressSnapshot;
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

    void processBatch(String batchId) { processBatch(batchId, ignored -> { }); }

    void processBatch(String batchId, Consumer<ProgressSnapshot> progress) {
        try {
            Properties config = new Properties();
            Path file = root.resolve("EasyReasoning/easy-reasoning.properties");
            if (Files.exists(file)) {
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { config.load(reader); }
            }
            String mode = config.getProperty("dedup.mode", "qwen").strip();
            if (mode.equals("off")) { publish(progress, batchId, 0, 0, "", "Complete"); return; }
            if (!mode.equals("qwen")) throw new IllegalArgumentException("Unsupported dedup.mode: " + mode);
            try (var database = new DeduplicationDatabase(root)) {
                process(database.loadBatch(batchId), compare,
                        (duplicate, original) -> database.saveDecision(batchId, duplicate, original), batchId, progress);
            } finally {
                try { releaseModel(); }
                catch (RuntimeException ignored) {
                    // releaseModel logs the failure; cleanup must not discard the completed batch.
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    @LogException(level = Level.WARN, types = RuntimeException.class)
    private void releaseModel() {
        release.run();
    }

    static void process(List<VacancySnapshot> vacancies, BiPredicate<String, String> compare,
                        BiConsumer<Long, Long> save) {
        process(vacancies, compare, save, null, ignored -> { });
    }

    static void process(List<VacancySnapshot> vacancies, BiPredicate<String, String> compare,
                        BiConsumer<Long, Long> save, String batchId, Consumer<ProgressSnapshot> progress) {
        new BatchRun(vacancies, compare, save, batchId, progress).run();
    }

    private static Key key(VacancySnapshot vacancy) {
        String title = vacancy.title().replaceAll("[\\s\\p{Z}]+", " ").strip().toLowerCase(Locale.ROOT);
        return vacancy.companyId() == null || title.isBlank() ? null : new Key(vacancy.companyId(), title);
    }

    private static void publish(Consumer<ProgressSnapshot> listener, String batchId, long done,
                                long total, String pair, String action) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Comparisons", done + " / " + total);
        fields.put("Compared vacancies", pair);
        listener.accept(new ImmutableProgressSnapshot(batchId, "Deduplication", action,
                "Deduplication " + done + " / " + total, fields, pair));
    }

    private static final class BatchRun {
        private final List<VacancySnapshot> vacancies;
        private final BiPredicate<String, String> compare;
        private final BiConsumer<Long, Long> save;
        private final String batchId;
        private final Consumer<ProgressSnapshot> listener;
        private final Map<Key, List<VacancySnapshot>> samples = new HashMap<>();
        private final Set<Long> unresolved = new HashSet<>();
        private final Set<Long> excluded = new HashSet<>();
        private long completed;
        private String lastPair = "";

        BatchRun(List<VacancySnapshot> vacancies, BiPredicate<String, String> compare,
                 BiConsumer<Long, Long> save, String batchId, Consumer<ProgressSnapshot> listener) {
            this.vacancies = vacancies;
            this.compare = compare;
            this.save = save;
            this.batchId = batchId;
            this.listener = listener;
        }

        void run() {
            long remaining = remainingComparisons(0, 0);
            publish(listener, batchId, completed, completed + remaining, lastPair, "Comparing descriptions");
            for (int index = 0; index < vacancies.size(); index++) processVacancy(index);
            publish(listener, batchId, completed, completed, lastPair, "Complete");
        }

        private void processVacancy(int index) {
            VacancySnapshot vacancy = vacancies.get(index);
            if (excluded.contains(vacancy.id())) return;
            Key key = key(vacancy);
            if (key == null) {
                if (!vacancy.checked()) save.accept(vacancy.id(), null);
                return;
            }
            var group = samples.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (vacancy.checked()) {
                if (vacancy.duplicateOf() == null) group.add(vacancy);
                return;
            }
            compareVacancy(index, vacancy, group);
        }

        private void compareVacancy(int index, VacancySnapshot vacancy, List<VacancySnapshot> group) {
            if (group.isEmpty()) {
                finishVacancy(vacancy, group, false);
                return;
            }
            boolean incomplete = false;
            int pairIndex = 0;
            while (pairIndex < group.size()) {
                VacancySnapshot sample = group.get(pairIndex);
                Boolean duplicate;
                try { duplicate = comparePair(sample, vacancy); }
                catch (RequestTooLargeException ignored) {
                    excludeLonger(sample, vacancy);
                    boolean vacancyExcluded = excluded.contains(vacancy.id());
                    reportNextPosition(index, pairIndex, vacancyExcluded);
                    if (vacancyExcluded) return;
                    continue;
                }
                completed++;
                if (Boolean.TRUE.equals(duplicate) && !unresolved.contains(sample.id())) {
                    saveDuplicate(vacancy, sample);
                    report(index + 1, 0);
                    return;
                }
                incomplete |= duplicate == null || Boolean.TRUE.equals(duplicate);
                pairIndex++;
                boolean lastPair = pairIndex == group.size();
                if (lastPair) finishVacancy(vacancy, group, incomplete);
                reportNextPosition(index, pairIndex, lastPair);
                if (lastPair) return;
            }
            finishVacancy(vacancy, group, incomplete);
        }

        @LogOutput(include = {"#vacancy.source", "#vacancy.jobId", "#original.source", "#original.jobId"})
        private void saveDuplicate(VacancySnapshot vacancy, VacancySnapshot original) {
            save.accept(vacancy.id(), original.id());
        }

        private void excludeLonger(VacancySnapshot first, VacancySnapshot second) {
            VacancySnapshot removed = characters(first.text()) > characters(second.text()) ? first : second;
            excludeVacancy(removed);
        }

        @LogOutput(level = Level.WARN, include = {"#vacancy.source", "#vacancy.jobId"})
        private void excludeVacancy(VacancySnapshot vacancy) {
            excluded.add(vacancy.id());
            samples.values().forEach(group -> group.removeIf(item -> item.id() == vacancy.id()));
            unresolved.remove(vacancy.id());
        }

        private int characters(String text) { return text.codePointCount(0, text.length()); }

        private void reportNextPosition(int vacancyIndex, int nextSample, boolean vacancyFinished) {
            if (vacancyFinished) {
                report(vacancyIndex + 1, 0);
                return;
            }
            report(vacancyIndex, nextSample);
        }

        @LogInput(include = {"#sample.source", "#sample.jobId", "#vacancy.source", "#vacancy.jobId"})
        private Boolean comparePair(VacancySnapshot sample, VacancySnapshot vacancy) {
            lastPair = sample.source() + ":" + sample.jobId() + " vs " + vacancy.source() + ":" + vacancy.jobId();
            try { return compareDescriptions(sample.text(), vacancy.text()); }
            catch (RequestTooLargeException error) { throw error; }
            catch (RuntimeException ignored) { return null; }
        }

        @LogException(level = Level.WARN, types = RuntimeException.class)
        private boolean compareDescriptions(String firstText, String secondText) {
            return compare.test(firstText, secondText);
        }

        private void finishVacancy(VacancySnapshot vacancy, List<VacancySnapshot> group, boolean incomplete) {
            if (incomplete) unresolved.add(vacancy.id());
            else save.accept(vacancy.id(), null);
            group.add(vacancy);
        }

        // Assume no future duplicates: each pending vacancy becomes another candidate.
        private long remainingComparisons(int nextVacancy, int nextSample) {
            Map<Key, Long> counts = new HashMap<>();
            samples.forEach((key, group) -> counts.put(key, (long) group.size()));
            long remaining = 0;
            for (int index = nextVacancy; index < vacancies.size(); index++) {
                VacancySnapshot vacancy = vacancies.get(index);
                Key key = key(vacancy);
                if (key == null || excluded.contains(vacancy.id())) continue;
                long count = counts.getOrDefault(key, 0L);
                if (!vacancy.checked()) remaining += count - (index == nextVacancy ? nextSample : 0);
                if (!vacancy.checked() || vacancy.duplicateOf() == null) counts.put(key, count + 1);
            }
            return remaining;
        }

        private void report(int nextVacancy, int nextSample) {
            long remaining = remainingComparisons(nextVacancy, nextSample);
            publish(listener, batchId, completed, completed + remaining, lastPair, "Comparing descriptions");
        }
    }
}
