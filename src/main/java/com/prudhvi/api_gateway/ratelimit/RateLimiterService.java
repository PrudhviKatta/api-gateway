package com.prudhvi.api_gateway.ratelimit;

import com.prudhvi.api_gateway.route.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Token bucket rate limiter backed by Redis.
 *
 * Each (clientIp, routePath) pair gets its own bucket stored as a Redis Hash
 * with two fields: "tokens" (current count) and "lastRefill" (epoch millis).
 *
 * Atomicity is guaranteed by running the entire check-and-consume logic inside
 * a Lua script. Redis executes Lua scripts as a single atomic command, so
 * concurrent requests from the same client cannot interleave and cause
 * double-spending of the same token.
 *
 * Key format:  rl:{routePath}:{clientIp}
 * Example:     rl:/api/users:192.168.1.10
 */
@Service
public class

RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    /**
     * The result of a rate-limit check.
     *
     * @param allowed   true if the request may proceed, false if it should be rejected
     * @param remaining how many tokens are left in the bucket after this check;
     *                  -1 when rate limiting is skipped (no config or Redis unavailable)
     */
    public record RateLimitResult(boolean allowed, int remaining) {}

    /**
     * Lua script that implements the token bucket algorithm atomically.
     *
     * KEYS[1]  — the Redis key for this (client, route) bucket
     * ARGV[1]  — capacity (max tokens)
     * ARGV[2]  — refillRatePerSecond
     * ARGV[3]  — current time in milliseconds
     * ARGV[4]  — TTL in seconds for the key (auto-expire idle buckets)
     *
     * Returns a two-element array: { allowed (1 or 0), remaining tokens (floor) }
     *
     * How it works:
     * 1. HMGET the stored "tokens" and "lastRefill" from the Hash.
     * 2. If the key doesn't exist yet, initialize with a full bucket.
     * 3. Compute elapsed seconds since the last refill.
     * 4. Add elapsed * refillRate tokens, capped at capacity.
     * 5. If there is at least one token, consume it and return allowed=1.
     * 6. Otherwise return allowed=0 (bucket is empty).
     * 7. Persist the updated state and refresh the TTL.
     */
    private static final String LUA_SCRIPT = """
            local key     = KEYS[1]
            local cap     = tonumber(ARGV[1])
            local rate    = tonumber(ARGV[2])
            local now     = tonumber(ARGV[3])
            local ttl     = tonumber(ARGV[4])

            local data = redis.call('HMGET', key, 'tokens', 'lastRefill')
            local tokens    = tonumber(data[1])
            local lastRefill = tonumber(data[2])

            if tokens == nil then
                -- First request for this (client, route): start with a full bucket.
                tokens    = cap
                lastRefill = now
            end

            -- How many seconds have elapsed since we last topped up the bucket?
            local elapsed = (now - lastRefill) / 1000.0

            -- Add tokens proportional to elapsed time, but never exceed capacity.
            local newTokens = math.min(cap, tokens + elapsed * rate)

            local allowed = 0
            if newTokens >= 1.0 then
                newTokens = newTokens - 1.0
                allowed = 1
            end

            -- Persist updated state and reset TTL so idle buckets are cleaned up.
            redis.call('HMSET', key, 'tokens', tostring(newTokens), 'lastRefill', tostring(now))
            redis.call('EXPIRE', key, ttl)

            return { allowed, math.floor(newTokens) }
            """;

    // RedisScript is created once and reused. Spring caches its SHA1 digest so
    // after the first EVAL Redis uses EVALSHA for all subsequent calls.
    private static final RedisScript<List<Long>> SCRIPT =
            RedisScript.of(LUA_SCRIPT, (Class<List<Long>>) (Class<?>) List.class);

    // StringRedisTemplate is Spring Boot's pre-configured RedisTemplate that uses
    // StringRedisSerializer for both keys and values — exactly what we need since
    // our Lua script keys and arguments are all plain strings.
    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks whether the given client is within the rate limit for the given route.
     *
     * Returns allowed=true (with remaining=-1) immediately if:
     *   - the route has no rate-limit config (capacity is null), or
     *   - Redis is unavailable (fail-open behaviour)
     */
    public RateLimitResult check(String clientIp, Route route) {
        if (route.getCapacity() == null || route.getRefillRatePerSecond() == null) {
            return new RateLimitResult(true, -1);
        }

        try {
            String key = "rl:" + route.getPath() + ":" + clientIp;
            int capacity = route.getCapacity();
            int refillRate = route.getRefillRatePerSecond();
            long nowMs = System.currentTimeMillis();

            // TTL: time for an empty bucket to fully refill, doubled for a safety margin.
            // This keeps Redis clean — buckets that see no traffic expire automatically.
            int ttlSeconds = (int) Math.ceil((double) capacity / refillRate) * 2;

            List<Long> result = redisTemplate.execute(
                    SCRIPT,
                    List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(nowMs),
                    String.valueOf(ttlSeconds)
            );

            // result[0] = 1 if allowed, 0 if rejected
            // result[1] = floor(remaining tokens)
            boolean allowed = result != null && result.get(0) == 1L;
            int remaining  = (result != null) ? result.get(1).intValue() : 0;

            return new RateLimitResult(allowed, remaining);

        } catch (Exception e) {
            // Redis is unavailable or the script failed. Fail open: let the request through.
            log.warn("Rate limiter Redis error for client={} route={}: {} — failing open",
                    clientIp, route.getPath(), e.getMessage());
            return new RateLimitResult(true, -1);
        }
    }
}
