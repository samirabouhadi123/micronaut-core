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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class ClassUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ClassUtil.class);
    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath();

    private static final List<String> SOURCE_DIRECTORIES = Arrays.asList(
            "src/main/java/",
            "src/test/java/"
    );

    private static final String COLOR_AT = "#718096";
    private static final String COLOR_CLASS = "#4A5568";
    private static final String COLOR_METHOD = "#5A67D8";
    private static final String COLOR_FILE_INFO = "#805AD5";

    public static String getStackTraceAsString(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    public static String formatStackTraceLine(String line) {
        StringBuilder formattedLine = new StringBuilder();

        if (line.trim().startsWith("at ")) {
            formattedLine.append("<span style=\"color: ").append(COLOR_AT).append(";\">at </span>");
            line = line.substring(line.indexOf("at ") + 3);
        }

        int openParenIndex = line.indexOf('(');
        if (openParenIndex > 0) {
            String methodInfo = line.substring(0, openParenIndex);
            String fileInfo = line.substring(openParenIndex);

            appendFormattedMethodInfo(formattedLine, methodInfo);
            formattedLine.append("<span style=\"color: ").append(COLOR_FILE_INFO).append(";\">").append(fileInfo).append("</span>");
        } else {
            formattedLine.append(line);
        }

        return formattedLine.toString();
    }

    private static void appendFormattedMethodInfo(StringBuilder formattedLine, String methodInfo) {
        int lastDot = methodInfo.lastIndexOf('.');
        if (lastDot > 0) {
            String className = methodInfo.substring(0, lastDot + 1);
            String methodName = methodInfo.substring(lastDot + 1);

            formattedLine.append("<span style=\"color: ").append(COLOR_CLASS).append(";\">").append(className).append("</span>")
                    .append("<span style=\"color: ").append(COLOR_METHOD).append("; font-weight: 500;\">").append(methodName).append("</span>");
        } else {
            formattedLine.append(methodInfo);
        }
    }

    public static Optional<StackTraceElement> parseStackTraceLine(String line) {
        try {
            if (!line.startsWith("at")) return Optional.empty();
            line = line.substring(3);

            int openParen = line.indexOf('(');
            int closeParen = line.indexOf(')');
            if (openParen == -1 || closeParen == -1) return Optional.empty();

            String methodInfo = line.substring(0, openParen);
            String fileInfo = line.substring(openParen + 1, closeParen);

            if (!fileInfo.contains(":")) return Optional.empty();

            String[] parts = fileInfo.split(":");
            String fileName = parts[0];
            int lineNumber = Integer.parseInt(parts[1]);

            int lastDot = methodInfo.lastIndexOf('.');
            String className = methodInfo.substring(0, lastDot);

            return Optional.of(new StackTraceElement(className, "", fileName, lineNumber));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Path findSourceFile(String className) {
        List<String> classesToCheck = getClassNamesToCheck(className);

        for (String classToCheck : classesToCheck) {
            String relativePath = classToCheck.replace('.', File.separatorChar) + ".java";

            for (String sourceDir : SOURCE_DIRECTORIES) {
                Path path = PROJECT_ROOT.resolve(sourceDir + relativePath);
                if (Files.exists(path)) {
                    return path;
                }
            }
        }

        return null;
    }

    private static List<String> getClassNamesToCheck(String className) {
        List<String> classNames = new ArrayList<>();
        classNames.add(className);

        if (className.contains("$")) {
            String outerClassName = className.substring(0, className.indexOf('$'));
            classNames.add(outerClassName);
        }

        return classNames;
    }

    public static String getCodeSnippet(StackTraceElement element, CodeSnippetFormat format) {
        CodeSnippetFormat snippetFormat = (format != null) ? format : CodeSnippetFormat.PLAIN_TEXT;

        try {
            Path sourceFile = findSourceFile(element.getClassName());
            if (sourceFile == null || !Files.exists(sourceFile)) {
                return null;
            }

            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(sourceFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }

            int lineNumber = element.getLineNumber();
            int start = Math.max(0, lineNumber - 5);
            int end = Math.min(lines.size(), lineNumber + 2);

            return generateSnippet(lines, start, end, lineNumber, snippetFormat);
        } catch (Exception e) {
            LOG.debug("Error getting code snippet for {}: {}", element, e.getMessage());
            return null;
        }
    }

    private static String generateSnippet(List<String> lines, int start, int end, int lineNumber, CodeSnippetFormat format) {
        StringBuilder snippet = new StringBuilder();

        for (int i = start; i < end; i++) {
            boolean isErrorLine = (i == lineNumber - 1);
            String lineContent = lines.get(i);
            int displayLineNumber = i + 1;

            if (format == CodeSnippetFormat.HTML) {
                appendHtmlLine(snippet, lineContent, displayLineNumber, isErrorLine);
            } else {
                appendPlainTextLine(snippet, lineContent, displayLineNumber, isErrorLine);
            }
        }

        return snippet.toString();
    }

    private static void appendPlainTextLine(StringBuilder snippet, String lineContent, int lineNumber, boolean isErrorLine) {
        if (isErrorLine) {
            snippet.append(">>> ");
        }
        snippet.append(String.format("%2d", lineNumber)).append(": ");
        snippet.append(lineContent).append("\n");
    }

    private static void appendHtmlLine(StringBuilder snippet, String lineContent, int lineNumber, boolean isErrorLine) {
        String cssClass = isErrorLine ? "highlighted-line" : "code-line";
        snippet.append("<div class=\"")
                .append(cssClass)
                .append("\">")
                .append("<span class=\"line-number\">")
                .append(lineNumber)
                .append("</span> ")
                .append(lineContent)
                .append("</div>");
    }

    public enum CodeSnippetFormat {
        PLAIN_TEXT,
        HTML
    }
}
