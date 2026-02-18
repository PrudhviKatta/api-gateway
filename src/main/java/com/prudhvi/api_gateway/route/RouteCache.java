package com.prudhvi.api_gateway.route;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of all routes, keyed by path prefix.
 *
 * Why a local cache instead of hitting the DB on every request?
 * The DB is the source of truth, but a database round-trip on every proxied
 * request would add latency and load for what is effectively read-mostly, slowly
 * changing data. A 30-second refresh window is an acceptable trade-off.
 *
 * Thread safety: ConcurrentHashMap handles concurrent reads from the servlet
 * thread pool. The refresh replaces the entire map reference atomically.
 */
@Component
public class RouteCache {

    private static final Logger log = LoggerFactory.getLogger(RouteCache.class);

    private final RouteRepository routeRepository;

    // Volatile ensures the updated reference is immediately visible to all threads.
    private volatile Map<String, Route> cache = new ConcurrentHashMap<>();

    public RouteCache(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    /**
     * Runs once immediately after the Spring bean is fully constructed.
     * Loads all routes so the cache is warm before the first request arrives.
     */
    @PostConstruct
    public void loadOnStartup() {
        refresh();
    }

    /**
     * Refreshes the cache from the database every 30 seconds.
     * fixedDelay means "30s after the previous run finishes", so it can never
     * overlap with itself even if a DB call takes longer than expected.
     *
     * This same method is also called directly by RouteService after any
     * create / update / delete so the cache reflects changes immediately.
     */
    @Scheduled(fixedDelayString = "30000")
    public void refresh() {
        List<Route> routes = routeRepository.findAll();
        Map<String, Route> updated = new ConcurrentHashMap<>();
        for (Route route : routes) {
            updated.put(route.getPath(), route);
        }
        this.cache = updated;
        log.debug("Route cache refreshed — {} routes loaded", routes.size());
    }

    /**
     * Finds the best-matching route for a given incoming request path.
     *
     * Strategy: longest-prefix match.
     * Example: if the cache has /api/users and /api, a request to /api/users/123
     * matches /api/users (more specific wins).
     *
     * @param requestPath the full path of the incoming HTTP request
     * @return the matching Route, or empty if nothing matches
     */
    public Optional<Route> findMatch(String requestPath) {
        return cache.keySet().stream()
                .filter(requestPath::startsWith)           // keep only matching prefixes
                .max((a, b) -> Integer.compare(a.length(), b.length()))  // longest wins
                .map(cache::get);
    }
}
