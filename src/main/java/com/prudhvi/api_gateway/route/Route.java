package com.prudhvi.api_gateway.route;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Represents a single routing rule stored in PostgreSQL.
 *
 * path      — the incoming URL prefix this gateway listens on (e.g. /api/users)
 * targetUrl — the downstream service base URL (e.g. http://user-service:8081)
 *
 * Hibernate requires a no-arg constructor, which Lombok's @NoArgsConstructor provides.
 * @Getter / @Setter replace the boilerplate getters and setters.
 */
@Entity
@Table(name = "route")
@Getter
@Setter
@NoArgsConstructor
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique path prefix that the gateway matches incoming requests against.
    @Column(nullable = false, unique = true)
    private String path;

    // The base URL of the downstream service that requests are forwarded to.
    @Column(nullable = false)
    private String targetUrl;

    // Token bucket rate limiting config. Both null means rate limiting is disabled for this route.
    @Column(nullable = true)
    private Integer capacity;           // max tokens in bucket (e.g. 100)

    @Column(nullable = true)
    private Integer refillRatePerSecond; // tokens added per second (e.g. 10)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Called by Hibernate before the entity is first persisted.
     * Sets both timestamps to the current time.
     */
    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Called by Hibernate before every update to the entity.
     * Keeps updatedAt current without requiring callers to set it manually.
     */
    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
