package com.prudhvi.api_gateway.stream;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Exposes a Server-Sent Events endpoint for the React Live Traffic tab.
 *
 * GET /dashboard/stream returns an SseEmitter — Spring MVC keeps the HTTP
 * connection open indefinitely. Kafka events flow through AccessLogConsumer
 * → SseEmitterRegistry → here → browser.
 *
 * This controller is registered with default (high) priority, so it wins
 * over the ProxyController's catch-all @RequestMapping("/**") for this path.
 */
@RestController
public class TrafficStreamController {

    private final SseEmitterRegistry registry;

    public TrafficStreamController(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/dashboard/stream")
    public SseEmitter stream() {
        return registry.register();
    }
}
