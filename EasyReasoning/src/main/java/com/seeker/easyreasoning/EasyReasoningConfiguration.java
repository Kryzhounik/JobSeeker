package com.seeker.easyreasoning;

import com.seeker.easyreasoning.llm.OllamaConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Gateway wiring. Ollama is the only currently implemented backend. */
@Configuration(proxyBeanMethods = false)
@Import(OllamaConfiguration.class)
public class EasyReasoningConfiguration {
    @Bean
    public Properties easyReasoningProperties(Path projectRoot) throws IOException {
        Properties properties = new Properties();
        Path file = projectRoot.resolve("EasyReasoning/easy-reasoning.properties");
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }

    @Bean
    public VacancyCleaner vacancyCleaner(@Qualifier("easyReasoningProperties") Properties easyReasoningProperties, Environment environment,
                                         EasyReasoningService service, Path projectRoot) {
        String value = environment.getProperty("vacancy.trimming.enabled",
                easyReasoningProperties.getProperty("vacancy.trimming.enabled", "false"));
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("vacancy.trimming.enabled must be true or false");
        }
        return new VacancyCleaner(Boolean.parseBoolean(value), service,
                projectRoot.resolve("Data/easy-reasoning/trimming"),
                projectRoot.resolve("EasyReasoning/prompts/clean-vacancy.md"));
    }
}
