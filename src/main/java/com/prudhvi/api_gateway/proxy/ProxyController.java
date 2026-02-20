package com.prudhvi.api_gateway.proxy;

import com.prudhvi.api_gateway.accesslog.AccessLogEvent;
import com.prudhvi.api_gateway.accesslog.AccessLogPublisher;
import com.prudhvi.api_gateway.ratelimit.RateLimiterService;
import com.prudhvi.api_gateway.ratelimit.RateLimiterService.RateLimitResult;
import com.prudhvi.api_gateway.route.Route;
import com.prudhvi.api_gateway.route.RouteCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Set;

/**
 * Catch-all proxy controller.
 *
 * Every request that doesn't match a more-specific mapping (e.g. /routes)
 * falls through to this controller. It looks up the matching route in the
 * in-memory cache, builds an outbound HttpRequest, forwards it to the
 * downstream service, and writes the response back to the caller.
 *
 * @Order(LOWEST_PRECEDENCE) ensures Spring MVC resolves the explicit /routes
 * controller first. The catch-all only runs when nothing else matched.
 */
@RestController
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);

    /**
     * Hop-by-hop headers must not be forwarded between proxies.
     * These are connection-level headers that only make sense for a single
     * TCP link and would confuse the downstream service or cause protocol errors.
     */
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "host", "connection", "transfer-encoding", "te", "upgrade",
            "proxy-authorization", "proxy-authenticate", "keep-alive", "trailer"
    );

    private final RouteCache routeCache;
    private final RateLimiterService rateLimiterService;
    private final AccessLogPublisher accessLogPublisher;

    // A single HttpClient is created once and reused. It manages its own
    // internal connection pool, so creating one per request would waste resources.
    private final HttpClient httpClient;

    public ProxyController(RouteCache routeCache, RateLimiterService rateLimiterService,
                           AccessLogPublisher accessLogPublisher) {
        this.routeCache = routeCache;
        this.rateLimiterService = rateLimiterService;
        this.accessLogPublisher = accessLogPublisher;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Handles every inbound request that reaches this controller.
     *
     * Steps:
     * 1. Extract the request path and look it up in the route cache.
     * 2. If no route matches, return 404.
     * 3. Check rate limit for the (client IP, route) pair; return 429 if exceeded.
     * 4. Build the target URL: targetUrl + requestPath + optional query string.
     * 5. Copy the HTTP method, headers (minus hop-by-hop), and body.
     * 6. Send the request synchronously.
     * 7. Write the downstream status, headers, and body back to the caller.
     */
    @RequestMapping("/**")
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String requestPath = request.getRequestURI();
        String method = request.getMethod().toUpperCase();
        long startMs = System.currentTimeMillis();

        // --- Step 1: Route lookup ---
        Route route = routeCache.findMatch(requestPath).orElse(null);

        if (route == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\": \"No route found for path: " + requestPath + "\"}");
            accessLogPublisher.publish(new AccessLogEvent(
                    Instant.now(), extractClientIp(request), method, requestPath,
                    null, HttpServletResponse.SC_NOT_FOUND,
                    System.currentTimeMillis() - startMs, false));
            return;
        }

        // --- Step 2: Rate limit check ---
        // Client identity = IP address. Each (IP, route) pair has its own token bucket.
        String clientIp = extractClientIp(request);
        RateLimitResult rateLimitResult = rateLimiterService.check(clientIp, route);

        if (!rateLimitResult.allowed()) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-RateLimit-Limit", String.valueOf(route.getCapacity()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("Retry-After", "1");
            response.getWriter().write("{\"error\": \"Rate limit exceeded\"}");
            accessLogPublisher.publish(new AccessLogEvent(
                    Instant.now(), clientIp, method, requestPath,
                    route.getTargetUrl(), 429,
                    System.currentTimeMillis() - startMs, true));
            return;
        }

        // Add informational rate-limit headers on allowed requests.
        // Skip the headers when no rate limiting is configured (remaining == -1).
        if (route.getCapacity() != null) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(route.getCapacity()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(rateLimitResult.remaining()));
        }

        // --- Step 4: Build the target URL ---
        // Combine the downstream base URL with the original path (and query string if present).
        String queryString = request.getQueryString();
        String targetUrl = route.getTargetUrl() + requestPath
                + (queryString != null ? "?" + queryString : "");

        log.debug("Proxying {} {} -> {}", request.getMethod(), requestPath, targetUrl);

        // --- Step 5: Build the outbound HttpRequest ---
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl));

        // Forward all request headers except hop-by-hop headers.
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase())) {
                requestBuilder.header(headerName, request.getHeader(headerName));
            }
        }

        // Attach the request body. BodyPublishers.ofInputStream() pipes the
        // raw InputStream directly without buffering the full body in memory.
        // We extract the InputStream first because getInputStream() throws IOException,
        // which is incompatible with the Supplier<InputStream> functional interface.
        var bodyStream = request.getInputStream();
        requestBuilder.method(method, HttpRequest.BodyPublishers.ofInputStream(() -> bodyStream));

        HttpRequest outboundRequest = requestBuilder.build();

        // --- Step 6: Send the request and relay the response ---
        try {
            HttpResponse<byte[]> downstreamResponse =
                    httpClient.send(outboundRequest, BodyHandlers.ofByteArray());

            // Set the status code from the downstream response.
            response.setStatus(downstreamResponse.statusCode());

            // Forward response headers, skipping hop-by-hop and HTTP/2 pseudo-headers.
            // Pseudo-headers (:status, :path, etc.) are HTTP/2-only framing metadata —
            // they must never appear in an HTTP/1.1 response sent back to the client.
            downstreamResponse.headers().map().forEach((name, values) -> {
                if (!name.startsWith(":") && !HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                    values.forEach(value -> response.addHeader(name, value));
                }
            });

            // Write the response body bytes back to the caller.
            response.getOutputStream().write(downstreamResponse.body());

            accessLogPublisher.publish(new AccessLogEvent(
                    Instant.now(), clientIp, method, requestPath,
                    route.getTargetUrl(), downstreamResponse.statusCode(),
                    System.currentTimeMillis() - startMs, false));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\": \"Proxy request interrupted\"}");
            accessLogPublisher.publish(new AccessLogEvent(
                    Instant.now(), clientIp, method, requestPath,
                    route.getTargetUrl(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    System.currentTimeMillis() - startMs, false));
        } catch (Exception e) {
            log.error("Proxy error for {} {}: {}", method, requestPath, e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\": \"Bad gateway: " + e.getMessage() + "\"}");
            accessLogPublisher.publish(new AccessLogEvent(
                    Instant.now(), clientIp, method, requestPath,
                    route.getTargetUrl(), HttpServletResponse.SC_BAD_GATEWAY,
                    System.currentTimeMillis() - startMs, false));
        }
    }

    /**
     * Extracts the originating client IP address.
     *
     * When the gateway sits behind a reverse proxy or load balancer (e.g. nginx,
     * AWS ALB), the actual client IP is passed in the X-Forwarded-For header.
     * That header may contain a comma-separated chain of IPs — the first one is
     * always the original client. We fall back to the TCP remote address when
     * the header is absent (direct connections or local development).
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // "X-Forwarded-For: client, proxy1, proxy2" — take the first entry
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
