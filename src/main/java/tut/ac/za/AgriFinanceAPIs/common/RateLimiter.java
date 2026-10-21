package tut.ac.za.AgriFinanceAPIs.common;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Sliding-window limiter kept in memory (one API instance). Used to slow down password guessing and
 * password-reset abuse. For several API instances behind a load balancer, move the counters to Redis.
 */
public class RateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public RateLimiter() { this(System::currentTimeMillis); }

    public RateLimiter(LongSupplier clock) { this.clock = clock; }

    /** Records an attempt and returns false when the key already used max attempts inside the window. */
    public boolean tryAcquire(String key, int max, long windowMillis) {
        long now = clock.getAsLong();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMillis) q.pollFirst();
            if (q.size() >= max) return false;
            q.addLast(now);
            return true;
        }
    }

    /** Seconds until the oldest attempt leaves the window (for a Retry-After hint). */
    public long retryAfterSeconds(String key, long windowMillis) {
        Deque<Long> q = hits.get(key);
        if (q == null) return 0;
        synchronized (q) {
            if (q.isEmpty()) return 0;
            return Math.max(1, (windowMillis - (clock.getAsLong() - q.peekFirst())) / 1000);
        }
    }

    public void reset(String key) { hits.remove(key); }

    /** Drops empty windows so the map does not grow forever. */
    public void cleanup(long windowMillis) {
        long now = clock.getAsLong();
        hits.entrySet().removeIf(e -> {
            Deque<Long> q = e.getValue();
            synchronized (q) {
                return q.isEmpty() || now - q.peekLast() > windowMillis;
            }
        });
    }
}
