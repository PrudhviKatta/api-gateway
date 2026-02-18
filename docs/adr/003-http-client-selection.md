# ADR 003 — HTTP Client Selection for Request Proxying

**Date:** 2026-02-17
**Status:** Accepted

---

## Context

The gateway needs to forward incoming HTTP requests to downstream services and relay
the response back to the caller. This requires an HTTP client that can:

- Forward arbitrary HTTP methods (GET, POST, PUT, DELETE, PATCH, etc.)
- Stream request and response bodies without buffering the full payload in memory
- Forward and receive arbitrary headers
- Work with our blocking servlet stack (Spring MVC, not WebFlux)

Three candidates were evaluated:

| Option | Notes |
|--------|-------|
| `RestTemplate` | Spring's classic blocking client. Being deprecated in favour of `RestClient`. Abstracts too much — hard to do raw header/body passthrough cleanly. |
| `WebClient` (Spring WebFlux) | Reactive, non-blocking. Excellent API, but pulls in the full Reactor/Netty stack. We are on the servlet stack; mixing models adds complexity with no benefit at this phase. |
| `java.net.http.HttpClient` | Introduced in Java 11, fully matured in Java 21. Zero extra dependencies. Supports both sync and async sends. First-class body publisher/subscriber API makes header and body passthrough straightforward. |

---

## Decision

Use **`java.net.http.HttpClient`** (JDK built-in) for all outbound proxy requests.

A single `HttpClient` instance is created once and reused across requests (it manages
its own connection pool internally).

---

## Rationale

1. **Zero dependencies** — no extra Maven coordinates, no transitive pulls.
2. **Modern API** — `HttpRequest.Builder` makes it easy to set method, URI, headers,
   and body in a fluent, explicit way. Nothing is hidden.
3. **Sync send fits our stack** — `client.send(request, BodyHandlers.ofByteArray())`
   is a straightforward blocking call that maps cleanly to the servlet thread model.
4. **Body streaming** — `BodyPublishers.ofInputStream()` lets us pipe the raw request
   `InputStream` directly to the outbound request without buffering.
5. **Teaches the right mental model** — using the lowest-level standard API makes
   every proxying step visible and understandable, which aligns with the project's
   "no magic" preference.

---

## Consequences

- **No built-in retry / resilience** — that is intentional at Phase 1; Resilience4j
  circuit breaker (Phase 3) will wrap outbound calls.
- **No automatic load balancing** — `targetUrl` is a single URL per route. Client-side
  load balancing is a Phase 4+ concern.
- **Connection pooling** — the shared `HttpClient` instance reuses connections
  automatically via its internal pool; no manual configuration needed at this scale.
