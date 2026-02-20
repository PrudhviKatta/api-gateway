package com.prudhvi.api_gateway.accesslog;

import java.time.Instant;

/**
 * Immutable snapshot of a single request that passed through the gateway.
 *
 * Published to Kafka as a JSON message after every request, regardless of outcome.
 * The Phase 4 dashboard consumes this topic to display live traffic.
 *
 * targetUrl is null when no route matched the incoming path (404 case).
 */
public record AccessLogEvent(
        Instant timestamp,
        String  clientIp,
        String  method,
        String  path,
        String  targetUrl,
        int     statusCode,
        long    latencyMs,
        boolean rateLimited
) {}
