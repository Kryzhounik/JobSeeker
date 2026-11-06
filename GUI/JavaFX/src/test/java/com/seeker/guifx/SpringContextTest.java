package com.seeker.guifx;

import com.seeker.api.DriverApi;
import com.seeker.easyreasoning.EasyReasoningConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the real application lifecycle without opening windows or starting Python. */
class SpringContextTest {
    @Test
    void applicationInitializesAndClosesContext() throws Exception {
        var application = new SeekerApplication();
        application.init();
        var field = SeekerApplication.class.getDeclaredField("context");
        field.setAccessible(true);
        var context = (ConfigurableApplicationContext) field.get(application);
        try {
            assertTrue(context.isActive(), "Application context was not initialized");
            assertNotNull(context.getBean(DriverApi.class));
            assertNotNull(context.getBean(EasyReasoningConfiguration.class));
        } finally {
            application.stop();
        }
        assertFalse(context.isActive(), "Application context was not closed");
    }
}
