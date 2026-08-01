package com.velora.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The public-browsing rules in {@link SecurityConfig} are written as {@code /api/x/**}.
 * Collection endpoints sit at the bare {@code /api/x} with no trailing segment, so this
 * pins the behaviour that {@code /**} also matches zero segments - otherwise the
 * professional directory and portfolio catalog would demand a token despite being listed
 * as public, which no service-level test would catch.
 */
class PublicEndpointPatternTest {

    private final PathPatternParser parser = PathPatternParser.defaultInstance;

    @Test
    void publicGetPatternsMatchTheirCollectionEndpoints() {
        assertThat(matches("/api/professionals/**", "/api/professionals")).isTrue();
        assertThat(matches("/api/portfolio/**", "/api/portfolio")).isTrue();
        assertThat(matches("/api/categories/**", "/api/categories")).isTrue();
    }

    @Test
    void publicGetPatternsStillMatchTheirNestedEndpoints() {
        assertThat(matches("/api/professionals/**", "/api/professionals/7")).isTrue();
        assertThat(matches("/api/portfolio/**", "/api/portfolio/42")).isTrue();
    }

    @Test
    void publicGetPatternsDoNotLeakIntoOtherResources() {
        assertThat(matches("/api/professionals/**", "/api/bookings")).isFalse();
        assertThat(matches("/api/portfolio/**", "/api/portfolios")).isFalse();
    }

    private boolean matches(String pattern, String path) {
        PathPattern parsed = parser.parse(pattern);
        return parsed.matches(PathContainer.parsePath(path));
    }
}
