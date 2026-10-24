package com.seeker.api;

import com.seeker.easyreasoning.EasyReasoningConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.file.Path;

/** Application wiring; the entry point supplies the project root. */
@Configuration(proxyBeanMethods = false)
@Import(EasyReasoningConfiguration.class)
public class DriverConfiguration {
    @Bean
    public DriverApi driverApi(Path projectRoot) throws IOException {
        return new DriverApi(projectRoot);
    }
}
