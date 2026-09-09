package com.gocommerce.platform.security;

import java.time.Instant;

/** A deliberately PII-minimal administrative access record. */
public record AuditRecord(
        Instant occurredAt,
        String actorId,
        String actorRole,
        String method,
        String path,
        int status,
        String requestId) {
}
