package io.micronaut.error.test;

import io.micronaut.error.DefaultHtmlProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.*;
import io.micronaut.http.server.exceptions.response.Error;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.simple.SimpleHttpRequest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spock.lang.Specification;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(environments = {"dev"},startApplication = false)
class HtmlErrorResponseBodyProviderTest extends Specification {
    private static final Logger LOG = LoggerFactory.getLogger(HtmlErrorResponseBodyProviderTest.class);

    @Inject
    DefaultHtmlProvider htmlProvider;

    @ParameterizedTest
    @EnumSource(HttpStatus.class)
    void htmlPageforStatus(HttpStatus status) {

        if (status.getCode() >= 400) {

         ErrorContext errorContext=new ErrorContext() {
                @Override
                public @NonNull HttpRequest<?> getRequest() {
                    return new SimpleHttpRequest<>(HttpMethod.GET, "/foobar", null);
                }

                @Override
                public @NonNull Optional<Throwable> getRootCause() {
                    return Optional.empty();
                }

                @Override
                public @NonNull List<Error> getErrors() {
                    return Collections.emptyList();
                }
            };
            HttpResponse<?> response = new HttpResponse<Object>() {
                @Override
                public HttpStatus getStatus() {
                    return status;
                }

                @Override
                public int code() {
                    return status.getCode();
                }

                @Override
                public String reason() {
                    return status.getReason();
                }

                @Override
                public HttpHeaders getHeaders() {
                    return null;
                }

                @Override
                public MutableConvertibleValues<Object> getAttributes() {
                    return null;
                }

                @Override
                public Optional<Object> getBody() {
                    return Optional.empty();
                }
            };
            String html = htmlProvider.body(errorContext, response);

            assertNotNull(html);
            assertExpectedSubstringInHtml(status.getReason(), html);
            assertExpectedSubstringInHtml("<!doctype html>", html);
            if (status.getCode() == 404) {
                assertExpectedSubstringInHtml("The page is not available", html);
                assertExpectedSubstringInHtml("You may have mistyped the address or the page may have moved", html);
            } else if (status.getCode() == 413) {
                assertExpectedSubstringInHtml("The file or data you are trying to upload exceeds the size", html);
                assertExpectedSubstringInHtml("Please try again with a smaller file", html);
            }
        }
    }
    private void assertExpectedSubstringInHtml(String expected, String html) {
        if (!html.contains(expected)) {
            LOG.trace("{}", html);
        }
        assertTrue(html.contains(expected));
    }
    @Test
    void testStackTraceFiltering() {

        StackTraceElement[] testStackTrace = {
            new StackTraceElement("io.micronaut.FilteredClass", "filteredMethod", "Filtered.java", 1),
            new StackTraceElement("com.example.UserClass", "userMethod", "User.java", 42)
        };

        for (StackTraceElement element : testStackTrace) {
            String stackTraceLine = element.toString();
            if (stackTraceLine.contains("io.micronaut")) {
                assertTrue(
                    htmlProvider.shouldFilterLine(stackTraceLine),
                    "Framework traces should be filtered: " + stackTraceLine
                );
            } else {
                assertFalse(
                    htmlProvider.shouldFilterLine(stackTraceLine),
                    "User traces should remain: " + stackTraceLine
                );
            }
        }

    }
    @Nested

    @DisplayName("Request Information Tests")

    class RequestInformationTests {

        @Test

        @DisplayName("Should display request method and URL")

        void testRequestInformationDisplay() {

            RuntimeException exception = new RuntimeException("Request info test");

            SimpleHttpRequest<Object> request = new SimpleHttpRequest<>(HttpMethod.POST, "/api/users", null);

            request.getHeaders().add("Content-Type", "application/json");

            request.getHeaders().add("Accept", "application/json");



            ErrorContext errorContext = createErrorContextWithRequest(exception, request);

            HttpResponse<?> response = createResponse(HttpStatus.BAD_REQUEST);



            String html = htmlProvider.body(errorContext, response);



            assertAll(

                () -> assertContains(html, "Request Information"),

                () -> assertContains(html, "Method:</strong> POST"),

                () -> assertContains(html, "URL:</strong> /api/users"),

                () -> assertContains(html, "Content-Type"),

                () -> assertContains(html, "application/json"),

                () -> assertContains(html, "Accept")

            );

        }



        @Test

        @DisplayName("Should handle requests with no headers")

        void testRequestWithNoHeaders() {

            RuntimeException exception = new RuntimeException("No headers test");

            SimpleHttpRequest<Object> request = new SimpleHttpRequest<>(HttpMethod.GET, "/simple", null);



            ErrorContext errorContext = createErrorContextWithRequest(exception, request);

            HttpResponse<?> response = createResponse(HttpStatus.INTERNAL_SERVER_ERROR);



            String html = htmlProvider.body(errorContext, response);



            assertAll(

                () -> assertContains(html, "Request Information"),

                () -> assertContains(html, "Method:</strong> GET"),

                () -> assertContains(html, "URL:</strong> /simple")

            );

        }

    }
    private ErrorContext createErrorContextWithRequest(Throwable throwable, HttpRequest<?> request) {

        return new ErrorContext() {

            @Override

            public @NonNull HttpRequest<?> getRequest() {

                return request;

            }



            @Override

            public @NonNull Optional<Throwable> getRootCause() {

                return Optional.ofNullable(throwable);

            }



            @Override

            public @NonNull List<Error> getErrors() {

                return Collections.emptyList();

            }

        };

    }


    @Test
    @DisplayName("Should handle deep 5-level exception chain")

    void testDeepExceptionChain() {


        IOException level5 = new IOException("Network timeout");

        SQLException level4 = new SQLException("Connection pool exhausted", level5);

        RuntimeException level3 = new RuntimeException("Repository operation failed", level4);

        IllegalStateException level2 = new IllegalStateException("Service unavailable", level3);

        RuntimeException level1 = new RuntimeException("Business operation failed", level2);

        ErrorContext errorContext = createErrorContext(level1);

        HttpResponse<?> response = createResponse(HttpStatus.INTERNAL_SERVER_ERROR);



        String html = htmlProvider.body(errorContext, response);


        assertAll(

            () -> assertContains(html, "Business operation failed"),

            () -> assertContains(html, "Service unavailable"),

            () -> assertContains(html, "Repository operation failed"),

            () -> assertContains(html, "Network timeout"),

            () -> {



                int businessIndex = html.indexOf("Business operation failed");

                int networkIndex = html.indexOf("Network timeout");

                assertTrue(businessIndex < networkIndex, "Top-level exception should appear before top level exception");

            }

        );

    }

    private ErrorContext createErrorContext(Throwable throwable) {

        return new ErrorContext() {

            @Override

            public @NonNull HttpRequest<?> getRequest() {

                return new SimpleHttpRequest<>(HttpMethod.GET, "/test", null);

            }

            @Override

            public @NonNull Optional<Throwable> getRootCause() {

                return Optional.ofNullable(throwable);

            }
            @Override

            public @NonNull List<Error> getErrors() {

                return Collections.emptyList();

            }

        };

    }
    private HttpResponse<?> createResponse(HttpStatus status) {

        return new HttpResponse<Object>() {

            @Override

            public HttpStatus getStatus() {

                return status;

            }
            @Override

            public int code() {

                return status.getCode();

            }
            @Override

            public String reason() {

                return status.getReason();

            }
            @Override

            public HttpHeaders getHeaders() {

                return null;

            }
            @Override

            public MutableConvertibleValues<Object> getAttributes() {

                return MutableConvertibleValues.of(null);

            }

            @Override

            public Optional<Object> getBody() {

                return Optional.empty();

            }

        };

    }
    private void assertContains(String actual, String expected) {

        if (!actual.contains(expected)) {

            LOG.debug("Expected '{}' not found in HTML:\n{}", expected, actual);

        }
        assertTrue(actual.contains(expected),

            String.format("Expected HTML to contain '%s'", expected));
    }

    }
