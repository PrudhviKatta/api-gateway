package com.prudhvi.api_gateway.stream;

import com.prudhvi.api_gateway.accesslog.AccessLogEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry of all open SSE connections.
 *
 * CopyOnWriteArrayList is safe to iterate while concurrent add/remove
 * operations happen — which is exactly the pattern here: the Kafka listener
 * iterates to broadcast while browsers connect and disconnect concurrently.
 *
 * Each emitter represents one open browser tab showing the Live Traffic view.
 */
@Component
public class SseEmitterRegistry {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Creates a new SSE emitter and registers cleanup callbacks.
     * Called once per browser connection to /dashboard/stream.
     *
     * Timeout 0L means no server-side timeout — the connection stays open
     * until the browser closes it or an error occurs.
     */
    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(()   -> emitters.remove(emitter));
        emitter.onError(e      -> emitters.remove(emitter));
        // Send a comment immediately to flush the HTTP response headers.
        // Without this, Spring holds the headers until the first real event,
        // so the browser's EventSource never receives a response and loops
        // through onerror → reconnect indefinitely.
        // An SSE comment (: ...) is ignored by EventSource but commits the
        // response, which triggers the browser's onopen callback.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /**
     * Sends an access log event to every connected browser.
     * Silently drops disconnected clients — send() throws IOException when
     * the browser has closed the connection.
     */
    public void broadcast(AccessLogEvent event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(event));
            } catch (IOException e) {
                emitters.remove(emitter); // disconnected client — remove and move on
            }
        }
    }
}
