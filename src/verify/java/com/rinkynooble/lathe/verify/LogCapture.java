package com.rinkynooble.lathe.verify;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** Keeps every warning or error that mentions Lathe, so the checks can report data that failed to load. */
final class LogCapture extends AbstractAppender {
    static final List<String> PROBLEMS = new CopyOnWriteArrayList<>();
    private static boolean installed;

    private LogCapture() {
        super("LatheVerifyCapture", null, null, true, Property.EMPTY_ARRAY);
    }

    static synchronized void install() {
        if (installed) return;
        installed = true;
        LogCapture appender = new LogCapture();
        appender.start();
        ((Logger) LogManager.getRootLogger()).addAppender(appender);
    }

    @Override
    public void append(LogEvent event) {
        if (!event.getLevel().isMoreSpecificThan(Level.WARN)) return;
        String message = event.getMessage().getFormattedMessage();
        Throwable thrown = event.getThrown();
        String text = message + (thrown != null ? " " + thrown : "");
        if (text.toLowerCase(Locale.ROOT).contains("lathe") && !message.startsWith("[verify]")) {
            PROBLEMS.add(event.getLevel() + " " + event.getLoggerName() + ": " + text);
        }
    }
}
