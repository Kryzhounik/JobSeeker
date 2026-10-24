package com.seeker.easyreasoning;

import com.seeker.easyreasoning.llm.OllamaConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Gateway wiring. Ollama is the only currently implemented backend. */
@Configuration(proxyBeanMethods = false)
@Import(OllamaConfiguration.class)
public class EasyReasoningConfiguration {
}
