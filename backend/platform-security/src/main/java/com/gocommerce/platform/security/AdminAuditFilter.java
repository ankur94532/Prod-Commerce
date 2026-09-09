package com.gocommerce.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Emits one structured, PII-minimal record for every selected admin request outcome. */
public class AdminAuditFilter extends OncePerRequestFilter {

    private static final Logger audit = LoggerFactory.getLogger("GOCOMMERCE_AUDIT");
    private final Predicate<HttpServletRequest> selected;
    private final Consumer<AuditRecord> sink;

    public AdminAuditFilter(Predicate<HttpServletRequest> selected) {
        this(selected, AdminAuditFilter::log);
    }

    AdminAuditFilter(Predicate<HttpServletRequest> selected, Consumer<AuditRecord> sink) {
        this.selected = selected;
        this.sink = sink;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !selected.test(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
            requestId = UUID.randomUUID().toString();
        }
        try {
            chain.doFilter(request, response);
        } finally {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String actorId = "anonymous";
            String actorRole = "none";
            if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
                actorId = user.id();
                actorRole = user.role();
            }
            sink.accept(new AuditRecord(Instant.now(), actorId, actorRole, request.getMethod(),
                    request.getRequestURI(), response.getStatus(), requestId));
        }
    }

    private static void log(AuditRecord event) {
        audit.info("admin_audit occurred_at={} actor_id={} actor_role={} method={} path={} status={} request_id={}",
                event.occurredAt(), safe(event.actorId()), safe(event.actorRole()), event.method(),
                safe(event.path()), event.status(), safe(event.requestId()));
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t ]", "_");
    }
}
