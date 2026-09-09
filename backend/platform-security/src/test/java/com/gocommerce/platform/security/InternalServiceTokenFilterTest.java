package com.gocommerce.platform.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InternalServiceTokenFilterTest {

    private static final String TOKEN = "internal-service-token-value";

    private final InternalServiceTokenFilter filter =
            InternalServiceTokenFilter.forPathPrefix(TOKEN, "/api/v1/internal/");

    private MockHttpServletRequest request(String path, String provided) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        if (provided != null) {
            request.addHeader(InternalServiceTokens.HEADER, provided);
        }
        return request;
    }

    @Test
    void aGuardedRequestWithTheRightTokenPasses() throws Exception {
        MockHttpServletRequest request = request("/api/v1/internal/inventory", TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        assertThat(filter.shouldNotFilter(request)).isFalse();
        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void aGuardedRequestWithAWrongOrMissingTokenIsForbiddenAndNeverReachesTheHandler() throws Exception {
        for (String provided : new String[] { null, "", "wrong-token" }) {
            MockHttpServletRequest request = request("/api/v1/internal/inventory", provided);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(403);
            verifyNoInteractions(chain);
        }
    }

    @Test
    void unguardedPathsAreNotFiltered() {
        assertThat(filter.shouldNotFilter(request("/api/v1/products/1", null))).isTrue();
        assertThat(filter.shouldNotFilter(request("/actuator/health", null))).isTrue();
    }

    @Test
    void aPredicateCanGuardSomethingOtherThanAPathPrefix() throws Exception {
        InternalServiceTokenFilter byMethod =
                new InternalServiceTokenFilter(TOKEN, candidate -> "DELETE".equals(candidate.getMethod()));

        MockHttpServletRequest deletion = new MockHttpServletRequest("DELETE", "/api/v1/search/products/1");
        assertThat(byMethod.shouldNotFilter(deletion)).isFalse();
        assertThat(byMethod.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/search"))).isTrue();
    }
}
