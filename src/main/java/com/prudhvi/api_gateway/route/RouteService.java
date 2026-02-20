package com.prudhvi.api_gateway.route;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Business logic layer for Route management.
 *
 * Sits between the REST controller and the repository.
 * After any write operation (create, update, delete) it triggers an immediate
 * cache refresh so the change is visible to the proxy without waiting 30 seconds.
 */
@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final RouteCache routeCache;

    public RouteService(RouteRepository routeRepository, RouteCache routeCache) {
        this.routeRepository = routeRepository;
        this.routeCache = routeCache;
    }

    /**
     * Persists a new route and immediately refreshes the cache.
     */
    public Route create(Route route) {
        Route saved = routeRepository.save(route);
        routeCache.refresh();
        return saved;
    }

    /**
     * Returns all routes from the database (not the cache — this is for the
     * management API where we always want the authoritative DB state).
     */
    public List<Route> findAll() {
        return routeRepository.findAll();
    }

    /**
     * Returns a single route by its database ID, or empty if not found.
     */
    public Optional<Route> findById(Long id) {
        return routeRepository.findById(id);
    }

    /**
     * Updates an existing route's path and targetUrl, then refreshes the cache.
     *
     * We load the existing entity first so that Hibernate manages the update
     * and @PreUpdate fires correctly to set updatedAt.
     *
     * @return the updated Route, or empty if the ID doesn't exist
     */
    public Optional<Route> update(Long id, Route incoming) {
        return routeRepository.findById(id).map(existing -> {
            existing.setPath(incoming.getPath());
            existing.setTargetUrl(incoming.getTargetUrl());
            existing.setCapacity(incoming.getCapacity());
            existing.setRefillRatePerSecond(incoming.getRefillRatePerSecond());
            Route saved = routeRepository.save(existing);
            routeCache.refresh();
            return saved;
        });
    }

    /**
     * Deletes a route by ID and refreshes the cache.
     *
     * @return true if the route existed and was deleted, false if not found
     */
    public boolean delete(Long id) {
        if (!routeRepository.existsById(id)) {
            return false;
        }
        routeRepository.deleteById(id);
        routeCache.refresh();
        return true;
    }
}
