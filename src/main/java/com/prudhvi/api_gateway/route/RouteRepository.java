package com.prudhvi.api_gateway.route;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for Route entities.
 *
 * JpaRepository already provides save, findById, findAll, delete, etc.
 * We add one custom finder for looking up a route by its exact path string.
 */
public interface RouteRepository extends JpaRepository<Route, Long> {

    /**
     * Finds a route whose path column exactly matches the given string.
     * Used during cache population and for duplicate-path validation.
     */
    Optional<Route> findByPath(String path);
}
