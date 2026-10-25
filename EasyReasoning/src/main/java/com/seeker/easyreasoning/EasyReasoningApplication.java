package com.seeker.easyreasoning;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.file.Path;

/** Owns one initialized gateway for standalone callers and their repeated operations. */
public final class EasyReasoningApplication implements AutoCloseable {
    private final AnnotationConfigApplicationContext context;
    private final EasyReasoningService service;
    private final VacancyCleaner cleaner;

    public EasyReasoningApplication(Path projectRoot) {
        context = new AnnotationConfigApplicationContext();
        try {
            context.registerBean(Path.class, () -> projectRoot);
            context.register(EasyReasoningConfiguration.class);
            context.refresh();
            service = context.getBean(EasyReasoningService.class);
            cleaner = context.getBean(VacancyCleaner.class);
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
    }

    public String generate(String prompt) {
        return service.generate(prompt);
    }

    public String cleanVacancy(String text) {
        return cleaner.cleanVacancy(text);
    }

    @Override
    public void close() {
        try {
            service.close();
        } finally {
            context.close();
        }
    }
}
