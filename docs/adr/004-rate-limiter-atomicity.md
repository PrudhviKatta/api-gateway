# ADR 004 — Rate Limiter Atomicity Strategy

**Status:** Accepted
**Date:** 2026-02-19

---

## Context

Phase 2 introduces per-client rate limiting using the token bucket algorithm.
The bucket state — current token count and last-refill timestamp — lives in Redis
so that all application instances share the same counters.

When two concurrent requests arrive from the same client at the same instant,
they both need to read the token count, decide whether to allow the request,
and decrement the bucket. If those three steps happen non-atomically, both
requests can read the same pre-decrement count and both get approved even though
only one token was available. This is a classic TOCTOU (time-of-check /
time-of-use) race condition.

The question is: how do we guarantee that check-and-consume is atomic?

---

## Options Considered

### Option A — Lua script evaluated on Redis

Redis guarantees that a Lua script runs as a single, uninterruptible command.
All reads and writes in the script happen without any other client being able
to interleave. This gives us full atomicity with no distributed locking overhead.

### Option B — Redis WATCH / MULTI / EXEC (optimistic locking)

WATCH marks a key; if it changes before EXEC, the transaction is aborted and
the client retries. This works but adds retry logic and extra round-trips. Under
high contention (many clients hitting the same key) the abort rate grows,
increasing latency.

### Option C — Application-level locking (e.g. synchronized, Redis SETNX)

Distributed locks (via Redisson, or SETNX + expiry) introduce lock-acquisition
latency, risk of lock expiry under GC pauses, and a separate failure mode
(what if the lock key expires before the work is done?). Significantly more
complex for no benefit over Lua.

### Option D — Two separate Redis keys with SET NX / atomic increment

Split into a `tokens` key and a `lastRefill` key and use `INCR`/`DECR`. Does
not solve the refill step (computing elapsed time and capping at capacity)
atomically. Still requires Lua or WATCH to coordinate the two keys.

---

## Decision

**Use a Lua script (Option A).**

The script is loaded once at startup; Redis caches it by SHA1 and subsequent
calls use `EVALSHA`, avoiding the cost of re-sending the script body every time.

---

## Data Structure: Redis Hash

The bucket state is stored as a single Redis Hash with two fields:

| Field       | Value                             |
|-------------|-----------------------------------|
| `tokens`    | current token count (float)       |
| `lastRefill`| epoch milliseconds of last refill |

**Why a Hash over two separate string keys?**

A single `HMGET` fetches both fields in one round-trip. A single `HMSET` writes
both fields atomically (within the Lua script, the entire script is already
atomic, but using one key also keeps the namespace clean and halves the number
of Redis keys per client).

---

## TTL Strategy

After every write the script calls `EXPIRE key ttlSeconds`, where:

```
ttlSeconds = ceil(capacity / refillRatePerSecond) * 2
```

This is the time it takes for a completely empty bucket to refill to capacity,
doubled as a safety margin. If a client goes quiet for that long, its bucket
key is automatically evicted from Redis.

**Why this matters:** Without TTL, every unique (client IP, route path) pair
that ever sent a request accumulates a permanent key in Redis. Over time this
inflates memory usage. The TTL bounds worst-case memory to the number of
*active* clients rather than all historical clients.

---

## Fail-Open Behaviour

If Redis is unavailable (connection refused, timeout, etc.) the
`RateLimiterService` catches the exception, logs a warning, and returns
`allowed=true`. The request is proxied normally.

**Rationale:** The gateway's primary job is to route traffic. Rate limiting is
a secondary control; losing it briefly during a Redis outage is acceptable and
far better than rejecting all traffic (fail-closed) because a caching tier is
temporarily unreachable.

---

## Consequences

- Atomicity is guaranteed with no extra infrastructure (no lock service, no
  retry loops).
- The Lua script couples some logic to Redis. If we ever move away from Redis
  (unlikely), the script must be rewritten. Acceptable trade-off.
- Float arithmetic in Lua handles sub-token accumulation correctly, enabling
  smooth token refill across short time windows.
