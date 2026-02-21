package com.prudhvi.api_gateway.accesslog;

import com.prudhvi.api_gateway.stream.SseEmitterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the gateway.access-logs topic.
 *
 * groupId = "dashboard" is an independent consumer group — it maintains its own
 * Kafka offset and doesn't interfere with any other consumers of this topic.
 *
 * auto-offset-reset = latest (set in application.yaml) means the consumer only
 * picks up events published after the app starts, not replaying old history.
 * This is the right behaviour for a live-traffic dashboard.
 *
 * Each consumed event is broadcast to every open SSE browser connection via
 * the SseEmitterRegistry.
 */
@Component
public class AccessLogConsumer {

    private final SseEmitterRegistry registry;

    public AccessLogConsumer(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @KafkaListener(topics = "gateway.access-logs", groupId = "dashboard")
    public void consume(AccessLogEvent event) {
        registry.broadcast(event);
    }
}
