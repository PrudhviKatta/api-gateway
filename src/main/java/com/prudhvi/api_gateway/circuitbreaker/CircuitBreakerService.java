package com.prudhvi.api_gateway.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around Resilience4j's CircuitBreakerRegistry.
 *
 * Each route path gets its own CircuitBreaker instance, so one flaky
 * downstream service does not affect traffic to other routes. Breakers
 * are created lazily on first access using the "default" config defined
 * in application.yaml.
 */
@Service
public class CircuitBreakerService {

    private final CircuitBreakerRegistry registry;

    public CircuitBreakerService(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    /**
     * Returns the CircuitBreaker for the given route path, creating one
     * on first call. Subsequent calls with the same path return the same
     * instance (the registry is the source of truth).
     */
    public CircuitBreaker forRoute(String routePath) {
        return registry.circuitBreaker(routePath);
    }
}
