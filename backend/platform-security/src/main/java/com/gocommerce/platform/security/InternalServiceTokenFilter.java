package com.gocommerce.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Predicate;

/**
 * Guards service-to-service endpoints. Requests that the predicate selects must present a
 * matching {@value InternalServiceTokens#HEADER} header or receive 403; everything else
 * passes through untouched.
 */
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    private final String expectedToken;
    private final Predicate<HttpServletRequest> guarded;

    public InternalServiceTokenFilter(String expectedToken, Predicate<HttpServletRequest> guarded) {
        this.expectedToken = expectedToken;
        this.guarded = guarded;
    }

    /** Convenience for the common case of guarding one path prefix. */
    public static InternalServiceTokenFilter forPathPrefix(String expectedToken, String prefix) {
        return new InternalServiceTokenFilter(expectedToken, request -> request.getRequestURI().startsWith(prefix));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !guarded.test(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!InternalServiceTokens.matches(expectedToken, request.getHeader(InternalServiceTokens.HEADER))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid internal service token");
            return;
        }
        chain.doFilter(request, response);
    }
}
