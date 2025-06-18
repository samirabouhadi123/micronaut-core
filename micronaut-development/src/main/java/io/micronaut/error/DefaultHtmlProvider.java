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

import io.micronaut.context.MessageSource;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.LocaleResolver;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.HtmlErrorResponseBodyProvider;
import io.micronaut.http.server.exceptions.response.JsonErrorResponseBodyProvider;
import io.micronaut.http.util.HtmlSanitizer;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static io.micronaut.http.HttpStatus.*;

@Singleton
@Primary
@Requires(classes = {
    io.micronaut.http.server.exceptions.response.HtmlErrorResponseBodyProvider.class,
})
@Requires(env="dev")
public class DefaultHtmlProvider implements HtmlErrorResponseBodyProvider {

    @Value("${filter.prefixes:io.micronaut,io.netty,Unknown Source}")
    protected List<String> filterPrefixes;

    private static final String CSS = """
        *, *::before, *::after {
          box-sizing: border-box;
        }
        * {
          margin: 0;
        }
        html {
          font-size: 16px;
          height: 100%;
        }
        body {
          font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
          font-size: clamp(1rem, 2.5vw, 1.2rem);
          -webkit-font-smoothing: antialiased;
          font-style: normal;
          font-weight: 400;
          letter-spacing: -0.0025em;
          line-height: 1.6;
          min-height: 100vh;
          margin: 0;
          padding: 0;
          display: flex;
          justify-content: center;
          align-items: center;
          background-color: #f7fafc;
          text-rendering: optimizeLegibility;
          -webkit-text-size-adjust: 100%;
        }
        h1 {
            font-size: 1.8em;
            color: #1a202c;
            margin-bottom: 0.5em;
            font-weight: 600;
        }
        h2 {
            font-size: 5em;
            color: #2559a7;
            text-shadow: 2px 2px 0px rgba(0, 0, 0, 0.1);
            transition: all 0.3s ease-in-out;
            margin-top: -0.2em;
            margin-bottom: 0.2em;
            font-weight: 700;
            letter-spacing: -0.03em;
            opacity: 0.8;
        }
        h2:hover {
            opacity: 1;
            transform: scale(1.02);
            text-shadow: 3px 3px 5px rgba(0, 0, 0, 0.2);
        }
        a {
          color: #5a67d8;
          font-weight: 600;
          text-decoration: none;
          position: relative;
          transition: color 0.3s ease;
        }
        a::after {
          content: '';
          position: absolute;
          width: 100%;
          height: 2px;
          bottom: -2px;
          left: 0;
          background-color: #5a67d8;
          transform: scaleX(0);
          transform-origin: bottom right;
          transition: transform 0.3s ease;
        }
        a:hover {
          color: #4c51bf;
        }
        a:hover::after {
          transform: scaleX(1);
          transform-origin: bottom left;
        }
        b, strong {
          font-weight: 600;
          color: #1a202c;
        }
        i, em {
          font-style: italic;
          color: #1a202c;
        }
        main {
          display: flex;
          flex-direction: column;
          gap: 1.5em;
          padding: 2.5em;
          background-color: #ffffff;
          border-radius: 12px;
          box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15), 0 1px 5px rgba(0, 0, 0, 0.1);
          width: 80%;
          max-width: 1200px;
          margin: 2em;
          transition: transform 0.3s ease, box-shadow 0.3s ease;
          animation: fadeIn 0.5s ease;
        }
        main:hover {
          transform: translateY(-5px);
          box-shadow: 0 15px 35px rgba(0, 0, 0, 0.2), 0 5px 15px rgba(0, 0, 0, 0.1);
        }
        main header {
          width: 100%;
          border-bottom: 2px solid #e2e8f0;
          padding-bottom: 1.5em;
          margin-bottom: 1em;
        }
        main article {
          width: 100%;
          color: #e53e3e;
          font-size: 1.2rem;
          line-height: 1.5;
          margin-bottom: 1em;
          padding: 1em;
          background-color: #fff5f5;
          border-left: 4px solid #e53e3e;
          border-radius: 4px;
        }
        .stacktrace-container, .code-container, .request-container {
          margin-top: 1em;
          border: 1px solid #e2e8f0;
          border-radius: 8px;
          overflow: hidden;
          margin-bottom: 1em;
          transition: all 0.3s ease;
          box-shadow: 0 2px 5px rgba(0, 0, 0, 0.05);
        }
        .stacktrace-container:hover, .code-container:hover, .request-container:hover {
          box-shadow: 0 5px 15px rgba(0, 0, 0, 0.1);
          border-color: #cbd5e0;
        }
        .section-header {
          background: #f7fafc;
          padding: 12px 18px;
          border-bottom: 1px solid #e2e8f0;
          display: flex;
          justify-content: space-between;
          align-items: center;
          font-size: 1.1em;
          font-weight: 500;
          color: #4a5568;
        }
        .stacktrace-header {
          background: #f7fafc;
          padding: 12px 18px;
          cursor: pointer;
          border-bottom: 1px solid #e2e8f0;
          display: flex;
          justify-content: space-between;
          align-items: center;
          font-weight: 500;
          color: #4a5568;
          transition: background-color 0.2s ease;
        }
        .stacktrace-header:hover {
          background-color: #edf2f7;
        }
        .stacktrace-content, .code-content {
          padding: 18px;
          background: #ffffff;
          overflow-x: auto;
          display: none;
          border-radius: 0 0 8px 8px;
        }
        .stack-line {
          padding: 4px 8px;
          color: #4a5568;
          font-family: 'Fira Code', 'Menlo', 'Monaco', 'Courier New', monospace;
          font-size: 0.9em;
          line-height: 1.5;
          border-radius: 4px;
          transition: background-color 0.2s ease;
          position: relative;
        }
        .stack-line:hover {
          background-color: #f7fafc;
        }
        .code-snippet {
          background-color: #f8fafc;
          padding: 15px;
          border-radius: 8px;
          margin: 15px 0;
          border-left: 4px solid #5a67d8;
          overflow: hidden;
        }
        .code-line {
          padding: 3px 8px;
          white-space: pre;
          font-family: 'Fira Code', 'Menlo', 'Monaco', 'Courier New', monospace;
          font-size: 0.9em;
          line-height: 1.5;
          transition: background-color 0.2s ease;
        }
        .code-line:hover {
          background-color: #edf2f7;
        }
        .highlighted-line {
          background-color: #fef2f2;
          color: #c53030;
          padding: 3px 8px;
          border-left: 4px solid #c53030;
          font-weight: 500;
          font-family: 'Fira Code', 'Menlo', 'Monaco', 'Courier New', monospace;
          font-size: 0.9em;
          line-height: 1.5;
        }
        .line-number {
          color: #a0aec0;
          margin-right: 12px;
          display: inline-block;
          min-width: 40px;
          text-align: right;
          user-select: none;
        }
        .collapsible {
          cursor: pointer;
          user-select: none;
        }
        .request-info {
          padding: 18px;
          border-radius: 8px;
          margin-top: 10px;
          margin-bottom: 10px;
          background-color: #f8fafc;
        }
        .request-info-item {
          margin: 8px 0;
          font-family: 'Fira Code', 'Menlo', 'Monaco', 'Courier New', monospace;
          font-size: 0.9em;
          padding: 5px 0;
          border-bottom: 1px dotted #e2e8f0;
        }
        .request-info-item:last-child {
          border-bottom: none;
        }
        .button {
          padding: 8px 14px;
          background: #2559a7;
          color: white;
          border: none;
          border-radius: 6px;
          cursor: pointer;
          font-size: 14px;
          font-weight: 500;
          transition: all 0.2s ease;
          margin-left: 12px;
          display: flex;
          align-items: center;
          gap: 6px;
        }
        .button:hover {
          background: #4c51bf;
          transform: translateY(-2px);
          box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
        }
        .button:active {
          transform: translateY(0);
        }
        .copy-button {
          float: right;
        }

         .switch {
                  position: relative;
                  display: inline-block;
                  width: 60px;
                  height: 30px;
                }
           .switch input {
                  opacity: 0;
                  width: 0;
                  height: 0;
                }

                .slider {
                  position: absolute;
                  cursor: pointer;
                  top: 0;
                  left: 0;
                  right: 0;
                  bottom: 0;
                  background-color: #ccc;
                  -webkit-transition: .4s;
                  transition: .4s;
                }

                .slider:before {
                  position: absolute;
                  content: "";
                  height: 26px;
                  width: 26px;
                  left: 4px;
                  bottom: 4px;
                  background-color: white;
                  -webkit-transition: .4s;
                  transition: .4s;
                }

                input:checked + .slider {
                  background-color: #2196F3;
                }

                input:focus + .slider {
                  box-shadow: 0 0 1px #2196F3;
                }

                input:checked + .slider:before {
                  -webkit-transform: translateX(26px);
                  -ms-transform: translateX(26px);
                  transform: translateX(26px);
                }

                /* Rounded sliders */
                .slider.round {
                  border-radius: 34px;
                }

                .slider.round:before {
                  border-radius: 50%;
                }
        .filter-label {
          font-size: 12px;
          color: #4a5568;
          font-weight: 500;
        }
        .filter-toggle {
          display: flex;
          align-items: center;
          margin-left: auto;
        }
        .file-name {
          font-weight: 600;
          margin-bottom: 8px;
          color: #2d3748;
          padding: 5px 10px;
          background-color: #edf2f7;
          border-radius: 4px;
          display: inline-block;
        }
        .json-response-section {
          margin-top: 1em;
          margin-bottom: 1em;
        }
        .json-response-section pre {
          white-space: pre-wrap;
          word-wrap: break-word;
          overflow-x: auto;
          max-width: 100%;
          background-color: #f8fafc;
          padding: 15px;
          border-radius: 8px;
          border: 1px solid #e2e8f0;
          font-family: 'Fira Code', 'Menlo', 'Monaco', 'Courier New', monospace;
          font-size: 0.9em;
          line-height: 1.5;
        }
        .toggle-icon {
          font-size: 0.8em;
          transition: transform 0.3s ease;
        }
        .active .toggle-icon {
          transform: rotate(180deg);
        }
        @media (max-width: 768px) {
          main {
            width: 95%;
            padding: 1.5em;
          }
          h2 {
            font-size: 3.5em;
          }
        }
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }
""";

    private static final String JAVASCRIPT = """
        document.addEventListener('DOMContentLoaded', function() {
            const filterToggle = document.getElementById('filter-toggle');
            if (filterToggle) {
                const filteredStackTrace = document.getElementById('filtered-stack-trace');
                const fullStackTrace = document.getElementById('full-stack-trace');
                if (filteredStackTrace && fullStackTrace) {
                    filterToggle.checked = false;
                    if (filterToggle.checked) {
                        fullStackTrace.style.display = 'block';
                        filteredStackTrace.style.display = 'none';
                        expandSection(fullStackTrace);
                    } else {
                        filteredStackTrace.style.display = 'block';
                        fullStackTrace.style.display = 'none';
                        expandSection(filteredStackTrace);
                    }
                }

                filterToggle.addEventListener('change', function() {
                    if (filteredStackTrace && fullStackTrace) {
                        if (this.checked) {
                            fullStackTrace.style.display = 'block';
                            filteredStackTrace.style.display = 'none';
                            expandSection(fullStackTrace);
                        } else {
                            filteredStackTrace.style.display = 'block';
                            fullStackTrace.style.display = 'none';
                            expandSection(filteredStackTrace);
                        }
                    }
                });
            }

    document.querySelectorAll('.copy-button').forEach(function(button) {
        button.addEventListener('click', function(e) {
            const textToCopy = this.closest('.stacktrace-container').querySelector('.stacktrace-content').innerText;
            navigator.clipboard.writeText(textToCopy).then(function() {
                const originalText = button.innerText;
                button.innerText = 'Copied!';
                setTimeout(function() {
                    button.innerText = originalText;
                }, 2000);
            });
        });
    });

    document.querySelectorAll('.collapsible').forEach(function(header) {
        header.addEventListener('click', function() {
            const content = this.nextElementSibling;
            const toggleIcon = this.querySelector('.toggle-icon');
            this.classList.toggle('active');

            if (content.style.display === 'none' || content.style.display === '') {
                content.style.display = 'block';
                content.style.animation = 'fadeIn 0.3s ease';
                if (toggleIcon) toggleIcon.textContent = '▲';
            } else {
                content.style.display = 'none';
                if (toggleIcon) toggleIcon.textContent = '▼';
            }
        });
    });

    function expandSection(element) {
        if (!element) return;

        const headerElement = element.querySelector('.stacktrace-header.collapsible');
        const contentElement = element.querySelector('.stacktrace-content');
        if (headerElement && contentElement) {
            headerElement.classList.add('active');
            contentElement.style.display = 'block';
            const toggleIcon = headerElement.querySelector('.toggle-icon');
            if (toggleIcon) toggleIcon.textContent = '▲';
        }
    }
});
""";

    private static final String HTML_TEMPLATE = """
            <!doctype html>
            <html lang="en">
            <head>
                <title>{0} — {1}</title>
                <meta charset="utf-8">
                <meta name="viewport" content="initial-scale=1, width=device-width">
                <meta name="robots" content="noindex, nofollow">
                <style>{2}</style>
                <script>{3}</script>
            </head>
            <body>
                <main>
                    <header>{4}</header>
                    <article>{5}</article>
                    {6}
                    {7}
                    {8}
                    {9}
                </main>
            </body>
            </html>
            """;

    private static final String STACK_TRACE_HEADER_TEMPLATE = """
            <div class="stacktrace-header collapsible">
                <div style="display: flex; align-items: center;">
                    <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 10px;"><path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>
                    <span style="font-weight: 500;">Stack Trace:</span>
                    <span style="font-family: 'Fira Code', monospace; font-size: 0.9em; margin-left: 5px;">{0}</span>
                </div>
                {1}
            </div>
            """;

    private static final String COPY_BUTTON_TEMPLATE = """
            <button class="button copy-button"><svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 5px;"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"></path></svg>Copy</button>
            """;

    private static final String TOGGLE_FILTER_TEMPLATE = """
        <div style="display: flex; align-items: center;">
            <span class="filter-label">Show Full Stack:</span>
            <label class="switch">
              <input type="checkbox" id="filter-toggle">
              <span class="slider round"></span>
            </label>
            <span class="toggle-icon" style="margin-left: 15px;">▼</span>
            {0}
        </div>
        """;

    private static final String SECTION_HEADER_TEMPLATE = """
            <div class="{0}-header collapsible">{1} <span class="toggle-icon">▼</span></div>
            """;

    private static final String COLLAPSIBLE_SECTION_TEMPLATE = """
            <div class="{0}-section">
                <div class="{0}-container">
                    {1}
                    <div class="{0}-content" style="{2}">
                        {3}
                    </div>
                </div>
            </div>
            """;


    private static final Map<Integer, String> DEFAULT_ERROR_BOLD = Map.of(
            NOT_FOUND.getCode(), "The page is not available",
            REQUEST_ENTITY_TOO_LARGE.getCode(), "The file or data you are trying to upload exceeds the size",
            INTERNAL_SERVER_ERROR.getCode(), "An internal server error occurred"
    );

    private static final Map<Integer, String> DEFAULT_ERROR = Map.of(
            NOT_FOUND.getCode(), "You may have mistyped the address or the page may have moved",
            REQUEST_ENTITY_TOO_LARGE.getCode(), "Please try again with a smaller file"
    );


    private final HtmlSanitizer htmlSanitizer;
    private final MessageSource messageSource;
    private final LocaleResolver<HttpRequest<?>> localeResolver;
    private final JsonMapper jsonMapper;
    private final JsonErrorResponseBodyProvider<JsonError> jsonErrorResponseBodyProvider;

    DefaultHtmlProvider(HtmlSanitizer htmlSanitizer,
                        MessageSource messageSource,
                        LocaleResolver<HttpRequest<?>> localeResolver,
                        JsonMapper jsonMapper,
                        JsonErrorResponseBodyProvider<JsonError> jsonErrorResponseBodyProvider
    ) {
        this.htmlSanitizer = htmlSanitizer;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
        this.jsonMapper = jsonMapper;
        this.jsonErrorResponseBodyProvider = jsonErrorResponseBodyProvider;
    }

    @Override
    public String body(ErrorContext errorContext, HttpResponse<?> response) {
        HtmlErrorPage errorPage = createErrorPage(errorContext, response);
        return renderHtml(errorPage, errorContext, response);
    }

    private HtmlErrorPage createErrorPage(ErrorContext errorContext, HttpResponse<?> response) {
        int httpStatusCode = response.code();
        Locale locale = localeResolver.resolveOrDefault(errorContext.getRequest());

        String errorBold = getMessage(httpStatusCode + ".error.bold", DEFAULT_ERROR_BOLD.get(httpStatusCode), locale);
        String error = getMessage(httpStatusCode + ".error", DEFAULT_ERROR.get(httpStatusCode), locale);
        String httpStatusReason = htmlSanitizer.sanitize(response.reason());

        return new HtmlErrorPage(locale, httpStatusCode, httpStatusReason, error, errorBold);
    }


    private String getMessage(String code, String defaultMessage, Locale locale) {
        return defaultMessage != null
                ? messageSource.getMessage(code, defaultMessage, locale)
                : messageSource.getMessage(code, locale).orElse(null);
    }


    private String renderHtml(@NonNull HtmlErrorPage htmlErrorPage, ErrorContext errorContext, HttpResponse<?> response) {
        final String errorTitleCode = htmlErrorPage.httpStatusCode() + ".error.title";
        final String errorTitle = messageSource.getMessage(errorTitleCode, htmlErrorPage.httpStatusReason(), htmlErrorPage.locale());

        String header = renderHeader(errorTitle, htmlErrorPage.httpStatusCode());
        String articleContent = renderArticle(htmlErrorPage);
        String sourceCodeHtml = renderSourceCodeSection(extractCodeSnippets(errorContext));
        String stackTraceHtml = renderStackTraceSection(errorContext);
        String requestInfoHtml = renderRequestInfoSection(errorContext);
        String jsonResponseHtml = renderJsonResponseSection(errorContext, response);

        return MessageFormat.format(HTML_TEMPLATE,
                htmlErrorPage.httpStatusCode(),
                errorTitle,
                CSS,
                JAVASCRIPT,
                header,
                articleContent,
                sourceCodeHtml,
                stackTraceHtml,
                requestInfoHtml,
                jsonResponseHtml
        );
    }


    private String renderHeader(String errorTitle, int statusCode) {
        return "<h1>" + errorTitle + "</h1><h2>" + statusCode + "</h2>";
    }

    private String renderArticle(@NonNull HtmlErrorPage htmlErrorPage) {

        StringBuilder sb = new StringBuilder("<p>");
        if (htmlErrorPage.errorBold() != null) {
            sb.append("<strong>").append(htmlErrorPage.errorBold()).append("</strong>. ");
        }
        if (htmlErrorPage.error() != null) {
            sb.append(htmlErrorPage.error()).append(".");
        }
        sb.append("</p>");
        return sb.toString();
    }
    private final Map<String, List<CodeSnippet>> cache = new ConcurrentHashMap<>();

    private List<CodeSnippet> extractCodeSnippets(ErrorContext errorContext) {
        Optional<Throwable> exception = errorContext.getRootCause();
        if (exception.isEmpty()) {
            return new ArrayList<>();
        }

        String stackTraceText = ClassUtil.getStackTraceAsString(exception.get());

        return cache.computeIfAbsent(stackTraceText, this::processStackTrace);
    }

    private List<CodeSnippet> processStackTrace(String stackTraceText) {
        var snippets = new ArrayList<CodeSnippet>();

        Arrays.stream(stackTraceText.split("\n"))
                .filter(line -> !shouldFilterLine(line))
                .map(String::trim)
                .map(ClassUtil::parseStackTraceLine)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(element -> {
                    Path sourcePath = ClassUtil.findSourceFile(element.getClassName());
                    if (sourcePath != null && Files.exists(sourcePath)) {
                        String codeSnippet = ClassUtil.getCodeSnippet(element, ClassUtil.CodeSnippetFormat.HTML);

                        if (codeSnippet != null && !codeSnippet.isEmpty()) {
                            snippets.add(new CodeSnippet(
                                    element.getClassName(),
                                    sourcePath.getFileName().toString(),
                                    element.getLineNumber(),
                                    codeSnippet
                            ));
                        }
                    }
                });

        return snippets;
    }
    public boolean shouldFilterLine(String line) {
        for (String prefix : filterPrefixes) {
            if (line.contains(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String renderSourceCodeSection(List<CodeSnippet> codeSnippets) {
        if (codeSnippets.isEmpty()) return "";

        StringBuilder codeContent = new StringBuilder();
        for (CodeSnippet snippet : codeSnippets) {
            codeContent.append("<div class=\"code-snippet\">")
                    .append("<div class=\"file-name\">").append(snippet.fileName()).append("</div>")
                    .append(snippet.codeHtml())
                    .append("</div>");
        }

        String sectionHeader = MessageFormat.format(SECTION_HEADER_TEMPLATE, "stacktrace", "Source Code");
        return MessageFormat.format(COLLAPSIBLE_SECTION_TEMPLATE,
                "code",
                sectionHeader,
                "border-top: 1px solid #e2e8f0;",
                codeContent.toString());
    }

    private String renderStackTraceSection(ErrorContext errorContext) {
        if (errorContext == null) return "";

        return errorContext.getRootCause()
                .map(throwable -> "<div class=\"exception-section\">" +
                        renderStackTraceContainer(throwable, false) +
                        renderStackTraceContainer(throwable, true) +
                        "</div>")
                .orElse("");
    }

    private String renderStackTraceContainer(Throwable exception, boolean showFullStackTrace) {
        String exceptionInfo = exception.getClass().getName() +
                (exception.getMessage() != null ? ": " + exception.getMessage() : "");
        String containerId = showFullStackTrace ? "full-stack-trace" : "filtered-stack-trace";
        String initialStyle = showFullStackTrace ? "display: none;" : "";



        String headerControls = MessageFormat.format(TOGGLE_FILTER_TEMPLATE, COPY_BUTTON_TEMPLATE);

        String header = MessageFormat.format(STACK_TRACE_HEADER_TEMPLATE, exceptionInfo, headerControls);

        StringBuilder contentBuilder = new StringBuilder();
        String stackTraceText = ClassUtil.getStackTraceAsString(exception);
        String[] lines = stackTraceText.split("\n");
        boolean isFirstLine = true;

        for (String line : lines) {
            if (!showFullStackTrace && shouldFilterLine(line)) continue;

            if (isFirstLine) {
                contentBuilder.append("<div class=\"stack-line highlighted-line\" style=\"background-color: #FFF5F5; color: #C53030; padding: 10px; margin-bottom: 10px; border-radius: 4px; font-weight: 500;\">")
                        .append(htmlSanitizer.sanitize(line))
                        .append("</div>");
                isFirstLine = false;
            } else {
                contentBuilder.append("<div class=\"stack-line\">")
                        .append(ClassUtil.formatStackTraceLine(line))
                        .append("</div>");
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<div id=\"").append(containerId).append("\" class=\"stacktrace-container\" style=\"").append(initialStyle).append("\">")
                .append(header)
                .append("<div class=\"stacktrace-content\" style=\"border-top: 1px solid #e2e8f0;\">")
                .append(contentBuilder)
                .append("</div></div>");

        return builder.toString();
    }


    private String renderRequestInfoSection(ErrorContext errorContext) {
        HttpRequest<?> request = errorContext.getRequest();
        var contentBuilder = new StringBuilder();


        contentBuilder.append("<div class=\"request-info\">")
                .append("<div class=\"request-info-item\"><strong>Method:</strong> ").append(request.getMethod()).append("</div>")
                .append("<div class=\"request-info-item\"><strong>URL:</strong> ").append(request.getUri().toString()).append("</div>");


        contentBuilder.append(MessageFormat.format(SECTION_HEADER_TEMPLATE, "stacktrace", "Headers"));
        contentBuilder.append("<div class=\"stacktrace-content\">");

        request.getHeaders().forEach((name, values) ->
                contentBuilder.append("<div class=\"request-info-item\">")
                        .append(name).append(": ").append(String.join(", ", values))
                        .append("</div>")
        );

        contentBuilder.append("</div></div>");

        String sectionHeader = "<div class=\"section-header\">Request Information</div>";
        return MessageFormat.format(COLLAPSIBLE_SECTION_TEMPLATE,
                "error",
                sectionHeader,
                "",
                contentBuilder.toString());
    }

    private String renderJsonResponseSection(ErrorContext errorContext, HttpResponse<?> response) {


        try {
            JsonError jsonBody = jsonErrorResponseBodyProvider.body(errorContext, response);
            String jsonString = jsonMapper.writeValueAsString(jsonBody);

            String headerContent = "JSON Response";
            String sectionHeader = MessageFormat.format(SECTION_HEADER_TEMPLATE, "stacktrace", headerContent);
            String content = "<pre style=\"white-space: pre-wrap; overflow-x: auto;\">" +
                    htmlSanitizer.sanitize(jsonString) +
                    "</pre>";

            return "<div class=\"json-response-section\">" +
                    sectionHeader +
                    "<div class=\"stacktrace-content\">" +
                    content +
                    "</div>" +
                    "</div>";
        } catch (Exception ignored) {
            return "";
        }
    }

    private record HtmlErrorPage(
            Locale locale,
            int httpStatusCode,
            String httpStatusReason,
            String error,
            String errorBold
    ) {}

    private record CodeSnippet(
            String className,
            String fileName,
            int lineNumber,
            String codeHtml
    ) {}
}
