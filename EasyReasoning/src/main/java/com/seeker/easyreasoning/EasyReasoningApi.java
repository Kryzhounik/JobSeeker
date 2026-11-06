package com.seeker.easyreasoning;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.file.Path;

/** Public module API, reused across operations and closed by its owner. */
public final class EasyReasoningApi implements AutoCloseable {
    private final AnnotationConfigApplicationContext context;
    private final EasyReasoningService service;
    private final VacancyCleaner cleaner;
    private final VacancyDescriptionComparator descriptionComparator;

    public EasyReasoningApi(Path projectRoot) {
        context = new AnnotationConfigApplicationContext();
        try {
            context.registerBean(Path.class, () -> projectRoot);
            context.register(EasyReasoningConfiguration.class);
            context.refresh();
            service = context.getBean(EasyReasoningService.class);
            cleaner = context.getBean(VacancyCleaner.class);
            descriptionComparator = context.getBean(VacancyDescriptionComparator.class);
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

    public boolean areDuplicateDescriptions(String firstText, String secondText) {
        return descriptionComparator.areDuplicateDescriptions(firstText, secondText);
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
