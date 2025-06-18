/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.error;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.ConsoleAppender;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Requires(env = "dev")
@Requires(property = "console.source-code-logging.enabled", value = "true")
public class EnhancedErrorAppender extends ConsoleAppender<ILoggingEvent> {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(EnhancedErrorAppender.class);

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_BRIGHT_RED = "\u001B[91m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BRIGHT_YELLOW = "\u001B[93m";
    private static final String ANSI_BACKGROUND_RED = "\u001B[41m";
    private static final String ANSI_WHITE = "\u001B[97m";
    private static final String ANSI_GREEN = "\u001B[32m";

    private final ConcurrentHashMap<ErrorSignature, String> cache = new ConcurrentHashMap<>();
    private final Set<String> applicationPackages = new HashSet<>();
    private final ApplicationContext applicationContext;

    @Inject
    public EnhancedErrorAppender(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        initializeApplicationPackages();
        setupAppender();
    }


    private void setupAppender() {
        setName("ENHANCED_ERROR_APPENDER");
        setTarget("System.err");

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder encoder = createEncoder(context);

        setEncoder(encoder);
        setContext(context);
        start();
    }

    private PatternLayoutEncoder createEncoder(LoggerContext context) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();
        return encoder;
    }

    private void initializeApplicationPackages() {
        Collection<String> packages = applicationContext.getEnvironment().getPackages();
        if (!packages.isEmpty()) {
            applicationPackages.addAll(packages);
        }
    }

    @EventListener

    public void onServerStartup(ServerStartupEvent event) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(this);
    }

    @Override
    public void append(ILoggingEvent event) {
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy instanceof ThrowableProxy throwableProxyImpl) {
            Throwable throwable = throwableProxyImpl.getThrowable();

            StackTraceElement relevantElement = findRelevantStackTraceElement(throwable);
            if (relevantElement == null) {
                LOG.debug("No application classes found in stack trace for exception: {}", throwable.getClass().getName());
                return;
            }
            ErrorSignature key = new ErrorSignature(
                    throwable.getClass().getName(),
                    throwable.getMessage(),
                    relevantElement.getClassName(),
                    relevantElement.getLineNumber()
            );

            String enhancedLog = cache.computeIfAbsent(key, k -> createEnhancedErrorReport(throwable));

            if (enhancedLog != null) {
                try {
                    this.getOutputStream().write(enhancedLog.getBytes());
                } catch (IOException e) {
                    LOG.error("Failed to write enhanced error log", e);
                }
            }
        }
    }

    private String createEnhancedErrorReport(Throwable throwable) {
        try {
            StackTraceElement relevantElement = findRelevantStackTraceElement(throwable);

            return buildErrorReport(throwable, relevantElement);
        } catch (Exception e) {
            LOG.debug("Error while trying to enhance logging with source code", e);
            return null;
        }
    }

    private StackTraceElement findRelevantStackTraceElement(Throwable throwable) {
        StackTraceElement[] stackTrace = throwable.getStackTrace();

        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (isApplicationClass(className)) {
                return element;
            }
        }
        return null;
    }

    private String buildErrorReport(Throwable throwable, StackTraceElement relevantElement) {
        StringBuilder errorReport = new StringBuilder();
        String divider = "=".repeat(60);

        appendReportHeader(errorReport, divider);

        if (relevantElement != null) {
            appendApplicationSourceReport(errorReport, throwable, relevantElement, divider);
        } else {
            appendNoSourceReport(errorReport, throwable, divider);
        }

        appendReportFooter(errorReport, divider);
        return errorReport.toString();
    }

    private void appendReportHeader(StringBuilder errorReport, String divider) {
        errorReport.append("\n").append(ANSI_RED).append(divider).append("\n");
    }

    private void appendApplicationSourceReport(StringBuilder errorReport, Throwable throwable,
                                               StackTraceElement element, String divider) {
        appendSourceFileInfo(errorReport, element);
        appendExceptionInfo(errorReport, throwable);
        errorReport.append(divider).append("\n\n");
        appendCodeSnippet(errorReport, element);
        errorReport.append(ANSI_RED).append("\n");
    }

    private void appendSourceFileInfo(StringBuilder errorReport, StackTraceElement element) {
        errorReport.append(ANSI_BOLD).append(ANSI_BRIGHT_YELLOW)
                .append(element.getFileName())
                .append(" (line ").append(element.getLineNumber()).append(")")
                .append(ANSI_RESET).append(ANSI_RED).append("\n");
    }

    private void appendExceptionInfo(StringBuilder errorReport, Throwable throwable) {
        errorReport.append(ANSI_BOLD).append(ANSI_BRIGHT_RED)
                .append("EXCEPTION: ").append(throwable.getClass().getName()).append("\n");
        errorReport.append(ANSI_BOLD).append(ANSI_BRIGHT_RED)
                .append("MESSAGE: ").append(ANSI_RESET).append(ANSI_RED)
                .append(getExceptionMessage(throwable)).append("\n");
    }

    private String getExceptionMessage(Throwable throwable) {
        return throwable.getMessage() != null ? throwable.getMessage() : "";
    }

    private void appendCodeSnippet(StringBuilder errorReport, StackTraceElement element) {
        String codeSnippet = ClassUtil.getCodeSnippet(element, ClassUtil.CodeSnippetFormat.PLAIN_TEXT);
        if (codeSnippet != null) {
            String[] lines = codeSnippet.split("\n");
            for (String line : lines) {
                if (line.contains(">>>")) {
                    errorReport.append(ANSI_BACKGROUND_RED).append(ANSI_WHITE).append(ANSI_BOLD)
                            .append(line).append(ANSI_RESET).append("\n");
                } else {
                    errorReport.append(line).append("\n");
                }
            }
        }
    }

    private void appendNoSourceReport(StringBuilder errorReport, Throwable throwable, String divider) {
        errorReport.append(ANSI_YELLOW).append("NO APPLICATION SOURCE CODE FOUND IN STACK TRACE\n\n");
        appendExceptionInfo(errorReport, throwable);
        errorReport.append(ANSI_RED).append(divider).append("\n");
    }

    private void appendReportFooter(StringBuilder errorReport, String divider) {
        errorReport.append("\n").append(divider).append(ANSI_RESET).append("\n");
        errorReport.append(ANSI_GREEN).append("<")
                .append("=".repeat(10)).append(">")
                .append(ANSI_RESET).append(" EXECUTING\n");
    }

    private boolean isApplicationClass(String className) {
        return applicationPackages.stream()
                .anyMatch(className::startsWith);
    }

    private record ErrorSignature(
            String exceptionClass,
            String message,
            String className,
            Integer lineNumber
    ) {}
}
