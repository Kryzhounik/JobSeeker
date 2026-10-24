package com.seeker.easyreasoning.llm;

import com.seeker.easyreasoning.EasyReasoningService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Manual Spring AI wiring; constructing the context does not contact Ollama. */
@Configuration(proxyBeanMethods = false)
public class OllamaConfiguration {
    @Bean
    OllamaSettings ollamaSettings(Environment environment, Path projectRoot) throws IOException {
        Properties settings = new Properties();
        Path configFile = projectRoot.resolve("EasyReasoning/easy-reasoning.properties");
        if (Files.exists(configFile)) {
            try (var reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                settings.load(reader);
            }
        }
        String baseUrl = environment.getProperty("spring.ai.ollama.base-url",
                settings.getProperty("spring.ai.ollama.base-url", "http://localhost:11434"));
        String model = environment.getProperty("spring.ai.ollama.chat.model",
                settings.getProperty("spring.ai.ollama.chat.model", "qwen2.5:0.5b"));
        Assert.hasText(model, "spring.ai.ollama.chat.model must not be blank");
        return new OllamaSettings(baseUrl, model);
    }

    @Bean
    public OllamaApi ollamaApi(OllamaSettings settings) {
        return OllamaApi.builder().baseUrl(settings.baseUrl()).build();
    }

    @Bean
    public ChatModel chatModel(OllamaApi ollamaApi, OllamaSettings settings) {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(OllamaChatOptions.builder().model(settings.model()).keepAlive("-1m").build())
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
                .modelManagementOptions(ModelManagementOptions.builder()
                        .pullModelStrategy(PullModelStrategy.NEVER).build())
                .build();
    }

    @Bean
    public EasyReasoningService easyReasoningService(ChatModel chatModel, OllamaApi ollamaApi) {
        return new OllamaService(chatModel, ollamaApi);
    }

    record OllamaSettings(String baseUrl, String model) {}
}
