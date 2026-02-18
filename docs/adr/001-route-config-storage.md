# ADR 001: Route Configuration Storage

## Status
Accepted

## Context
The gateway needs to know which downstream service handles each route.
This config must be readable at request time and updatable without downtime.

## Options Considered
- **Config file (YAML):** Simple but requires gateway restart on every change
- **In-memory + API:** Fast but state is lost on restart, needs re-registration
- **PostgreSQL:** Persistent, updatable via API at runtime, auditable

## Decision
Store route configurations in PostgreSQL.

## Reasoning
A gateway is a control plane — it needs to reflect changes in real time
without restarts. PostgreSQL gives us persistence, the ability to expose
a management API for route CRUD, and a natural audit log of changes.
The added infrastructure cost is justified by operational flexibility.

## Consequences
- Gateway startup loads routes from DB into memory for fast lookups
- Routes are cached in-memory and refreshed periodically (every 30s)
- A management REST API will expose route registration endpoints
