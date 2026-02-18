package com.prudhvi.api_gateway.route;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing routes.
 *
 * All endpoints live under /routes. This controller is matched by Spring MVC
 * before the catch-all ProxyController because it has an explicit mapping.
 */
@RestController
@RequestMapping("/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    /**
     * POST /routes
     * Registers a new route. The request body must contain "path" and "targetUrl".
     * Returns 201 Created with the persisted route (including its generated id and timestamps).
     */
    @PostMapping
    public ResponseEntity<Route> create(@RequestBody Route route) {
        Route created = routeService.create(route);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /routes
     * Returns the full list of routes from the database.
     */
    @GetMapping
    public List<Route> findAll() {
        return routeService.findAll();
    }

    /**
     * GET /routes/{id}
     * Returns a single route by database ID, or 404 if not found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Route> findById(@PathVariable Long id) {
        return routeService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /routes/{id}
     * Updates an existing route's path and targetUrl.
     * Returns 200 with the updated route, or 404 if the ID doesn't exist.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Route> update(@PathVariable Long id, @RequestBody Route incoming) {
        return routeService.update(id, incoming)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /routes/{id}
     * Removes a route. Returns 204 No Content on success, 404 if not found.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (routeService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
