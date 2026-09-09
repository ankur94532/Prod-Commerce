package com.gocommerce.platform.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuditFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void selectedAdminOutcomeRecordsActorStatusAndRequestId() throws Exception {
        List<AuditRecord> events = new ArrayList<>();
        AdminAuditFilter filter = new AdminAuditFilter(request -> true, events::add);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/admin/products/7");
        request.addHeader("X-Request-Id", "request-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(409);
        AuthenticatedUser user = new AuthenticatedUser("admin-1", "private@example.com", "Admin", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.actorId()).isEqualTo("admin-1");
            assertThat(event.actorRole()).isEqualTo("ADMIN");
            assertThat(event.status()).isEqualTo(409);
            assertThat(event.requestId()).isEqualTo("request-1");
            assertThat(event.toString()).doesNotContain("private@example.com");
        });
    }

    @Test
    void unselectedRequestProducesNoAuditRecord() throws Exception {
        List<AuditRecord> events = new ArrayList<>();
        AdminAuditFilter filter = new AdminAuditFilter(request -> false, events::add);

        filter.doFilter(new MockHttpServletRequest("GET", "/health"), new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(events).isEmpty();
    }
}
