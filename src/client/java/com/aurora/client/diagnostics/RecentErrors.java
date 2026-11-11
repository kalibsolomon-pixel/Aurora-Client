package com.aurora.client.diagnostics;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The last few warnings and errors Aurora logged, kept in memory for the
 * diagnostics report (ADR 0004). Nothing is written or sent anywhere.
 */
public final class RecentErrors {
    private RecentErrors() {}

    private static final int LIMIT = 20;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static boolean installed;

    /** Start listening. Safe to call more than once; never throws. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            AbstractAppender appender = new AbstractAppender("AuroraRecentErrors", null, null, true, Property.EMPTY_ARRAY) {
                @Override public void append(LogEvent event) { record(event); }
            };
            appender.start();
            context.getConfiguration().getRootLogger().addAppender(appender, Level.WARN, null);
            context.updateLoggers();
        } catch (Throwable ignored) {
            // A different logging backend: the report simply has no error section.
        }
    }

    private static void record(LogEvent event) {
        String logger = event.getLoggerName();
        String message = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
        if (!isAurora(logger, message)) return;
        StringBuilder line = new StringBuilder()
                .append(TIME.format(Instant.ofEpochMilli(event.getTimeMillis()))).append(' ')
                .append(event.getLevel()).append(' ').append(logger).append(": ").append(firstLine(message));
        Throwable thrown = event.getThrown();
        if (thrown != null) {
            line.append(" (").append(thrown.getClass().getSimpleName());
            if (thrown.getMessage() != null) line.append(": ").append(firstLine(thrown.getMessage()));
            line.append(')');
        }
        add(line.toString());
    }

    static boolean isAurora(String logger, String message) {
        String name = logger == null ? "" : logger.toLowerCase(Locale.ROOT);
        return name.startsWith("aurora") || (message != null && message.contains("aurora.mixins.json"));
    }

    static String firstLine(String text) {
        int nl = text.indexOf('\n');
        String line = (nl >= 0 ? text.substring(0, nl) : text).trim();
        return line.length() > 240 ? line.substring(0, 240) + "..." : line;
    }

    static synchronized void add(String line) {
        if (LINES.size() == LIMIT) LINES.removeFirst();
        LINES.addLast(line);
    }

    /** Oldest first. */
    public static synchronized List<String> lines() { return new ArrayList<>(LINES); }

    static synchronized void clearForTests() { LINES.clear(); }
}
