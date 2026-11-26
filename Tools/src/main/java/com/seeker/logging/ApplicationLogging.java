package com.seeker.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;

/** Binds process logging to the project selected by the application. */
public final class ApplicationLogging {
    private ApplicationLogging() { }

    /**
     * Selects the file destination for this JVM; repeated selection of the same
     * normalized root is a no-op. An explicit Logback configuration remains authoritative.
     */
    public static synchronized void initialize(Path projectRoot) {
        String root = projectRoot.toAbsolutePath().normalize().toString();
        if (System.getProperty("logback.configurationFile") != null) return;
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        if (root.equals(context.getProperty("seeker.logging.root"))) return;
        Logger logger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        var previous = logger.getAppender("FILE");
        if (previous != null) {
            logger.detachAppender(previous);
            previous.stop();
        }
        context.putProperty("seeker.logging.root", root);
        var configurator = new JoranConfigurator();
        configurator.setContext(context);
        try {
            configurator.doConfigure(ApplicationLogging.class.getResource("/seeker-log-file.xml"));
        } catch (JoranException error) {
            throw new IllegalStateException("Cannot configure application log files", error);
        }
    }
}
