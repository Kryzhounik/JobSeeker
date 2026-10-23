package com.seeker.guifx;

import com.seeker.api.DriverApi;
import com.seeker.llm.LlmConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/** Checks the real application lifecycle without opening windows or starting Python. */
public final class SpringContextSmoke {
    public static void main(String[] args) throws Exception {
        var application = new SeekerApplication();
        application.init();
        var field = SeekerApplication.class.getDeclaredField("context");
        field.setAccessible(true);
        var context = (ConfigurableApplicationContext) field.get(application);
        try {
            if (!context.isActive() || context.getBean(DriverApi.class) == null
                    || context.getBean(LlmConfiguration.class) == null) {
                throw new AssertionError("Application context was not initialized");
            }
        } finally {
            application.stop();
        }
        if (context.isActive()) throw new AssertionError("Application context was not closed");
        System.out.println("Application Spring context initialization and shutdown OK");
    }
}
