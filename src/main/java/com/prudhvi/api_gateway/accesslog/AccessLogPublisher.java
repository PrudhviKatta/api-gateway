package com.prudhvi.api_gateway.accesslog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes access log events to Kafka after every proxied request.
 *
 * Fire-and-forget: we do not block waiting for broker acknowledgment.
 * If Kafka is unavailable, the failure is logged at WARN and the request
 * is unaffected — access logging should never degrade proxy performance.
 *
 * Topic: gateway.access-logs
 * Key:   clientIp  — routes all events from the same client to the same partition,
 *                    so a consumer sees per-client events in arrival order.
 */
@Service
public class AccessLogPublisher {

    private static final Logger log = LoggerFactory.getLogger(AccessLogPublisher.class);
    private static final String TOPIC = "gateway.access-logs";

    private final KafkaTemplate<String, AccessLogEvent> kafka;

    public AccessLogPublisher(KafkaTemplate<String, AccessLogEvent> kafka) {
        this.kafka = kafka;
    }

    public void publish(AccessLogEvent event) {
        kafka.send(TOPIC, event.clientIp(), event)
             .exceptionally(ex -> {
                 log.warn("Kafka access log publish failed: {}", ex.getMessage());
                 return null;
             });
    }
}
