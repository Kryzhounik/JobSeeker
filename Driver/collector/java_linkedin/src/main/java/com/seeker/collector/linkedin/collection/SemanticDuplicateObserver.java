package com.seeker.collector.linkedin.collection;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/** One ordered worker, with persistence exclusively on the calling collector thread. */
final class SemanticDuplicateObserver implements AutoCloseable {
    record Result(VacancySnapshot vacancy, VacancySnapshot original, List<String> errors) { }
    private record Key(long companyId, String title) { }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Queue<Future<Result>> pending = new ArrayDeque<>();
    private final Map<Key, List<VacancySnapshot>> samples = new HashMap<>();
    private final BiPredicate<String, String> compare;

    SemanticDuplicateObserver(BiPredicate<String, String> compare) { this.compare = compare; }

    void submit(VacancySnapshot vacancy) {
        pending.add(worker.submit(() -> compare(vacancy)));
    }

    private Result compare(VacancySnapshot vacancy) {
        List<String> errors = new ArrayList<>();
        String title = vacancy.title().replaceAll("[\\s\\p{Z}]+", " ").strip().toLowerCase(Locale.ROOT);
        if (vacancy.companyId() == null || title.isBlank()) return new Result(vacancy, null, errors);
        List<VacancySnapshot> group = samples.computeIfAbsent(new Key(vacancy.companyId(), title), k -> new ArrayList<>());
        for (VacancySnapshot sample : group) {
            try {
                if (compare.test(sample.text(), vacancy.text())) return new Result(vacancy, sample, errors);
            } catch (RuntimeException error) {
                errors.add("Semantic comparison failed: " + sample.jobId() + " vs " + vacancy.jobId() + ": " + error);
            }
        }
        group.add(vacancy);
        return new Result(vacancy, null, errors);
    }

    boolean hasPending() { return !pending.isEmpty(); }

    void drain(boolean wait, Consumer<Result> receive) {
        while (!pending.isEmpty() && (wait || pending.peek().isDone())) {
            Result result;
            try {
                result = pending.remove().get();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted waiting for semantic comparison", error);
            } catch (ExecutionException error) {
                throw new IllegalStateException("Semantic comparison worker failed", error.getCause());
            }
            receive.accept(result);
        }
    }

    @Override public void close() {
        worker.shutdown();
        try {
            while (!worker.awaitTermination(1, TimeUnit.DAYS)) { }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted stopping semantic comparison", error);
        }
    }
}
