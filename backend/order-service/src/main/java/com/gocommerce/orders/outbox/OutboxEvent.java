package com.gocommerce.orders.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 128)
    private String aggregateType;

    @Column(nullable = false, length = 128)
    private String aggregateId;

    @Column(nullable = false, length = 128)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.id = UUID.randomUUID().toString();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.attempts = 0;
        this.nextAttemptAt = Instant.now();
    }

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = createdAt;
        }
    }

    public String getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public void markFailedAttempt() {
        this.attempts++;
        long backoffSeconds = Math.min(300, (long) Math.pow(2, Math.min(attempts, 8)));
        this.nextAttemptAt = Instant.now().plusSeconds(backoffSeconds);
    }
}
