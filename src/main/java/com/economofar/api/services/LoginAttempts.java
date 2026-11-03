package com.economofar.api.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Counts the failed sign-in attempts of each e-mail address, in memory.
 *
 * After maxFailures failures inside the window the address is locked until the window,
 * which starts at the first failure, is over. A correct sign-in clears the count.
 * The table is bounded: when it holds more than maxTracked addresses, the expired ones are
 * dropped first, then the addresses that are not locked, then the locked ones, the oldest
 * first each time. The address that is being recorded is never dropped. A flood of made-up
 * addresses therefore cannot fill the memory of the server, and it cannot push out a locked
 * address unless it locks as many addresses itself.
 */
final class LoginAttempts {

    static final int MAX_FAILURES = 5;
    static final long WINDOW_MILLIS = 15L * 60L * 1000L;
    static final int MAX_TRACKED = 10_000;
    static final int MAX_KEY_LENGTH = 254;

    private final LongSupplier clock;
    private final int maxFailures;
    private final long windowMillis;
    private final int maxTracked;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    LoginAttempts() {
        this(System::currentTimeMillis, MAX_FAILURES, WINDOW_MILLIS, MAX_TRACKED);
    }

    LoginAttempts(LongSupplier clock, int maxFailures, long windowMillis, int maxTracked) {
        if (clock == null || maxFailures < 1 || windowMillis < 1 || maxTracked < 1) {
            throw new IllegalArgumentException("the limits of the sign-in attempts are not valid");
        }

        this.clock = clock;
        this.maxFailures = maxFailures;
        this.windowMillis = windowMillis;
        this.maxTracked = maxTracked;
    }

    boolean isLocked(String key) {
        String name = normalise(key);
        Entry entry = entries.get(name);

        if (entry == null) {
            return false;
        }

        if (expired(entry, clock.getAsLong())) {
            entries.remove(name, entry);
            return false;
        }

        return entry.count() >= maxFailures;
    }

    void recordFailure(String key) {
        String name = normalise(key);
        long now = clock.getAsLong();

        entries.compute(name, (ignored, existing) -> {
            if (existing == null || expired(existing, now)) {
                return new Entry(now, 1);
            }

            return new Entry(existing.firstFailureAt(), Math.min(existing.count() + 1, maxFailures));
        });

        if (entries.size() > maxTracked) {
            trim(now, name);
        }
    }

    void clear(String key) {
        entries.remove(normalise(key));
    }

    int tracked() {
        return entries.size();
    }

    private boolean expired(Entry entry, long now) {
        return now - entry.firstFailureAt() > windowMillis;
    }

    private static String normalise(String key) {
        if (key == null) {
            return "";
        }

        return key.length() > MAX_KEY_LENGTH ? key.substring(0, MAX_KEY_LENGTH) : key;
    }

    private synchronized void trim(long now, String keep) {
        if (entries.size() <= maxTracked) {
            return;
        }

        entries.values().removeIf(entry -> expired(entry, now));

        int excess = entries.size() - maxTracked;

        if (excess <= 0) {
            return;
        }

        int toRemove = excess + Math.max(1, maxTracked / 10);
        List<Map.Entry<String, Entry>> candidates = new ArrayList<>();

        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            if (item.getKey().equals(keep) == false) {
                candidates.add(Map.entry(item.getKey(), item.getValue()));
            }
        }

        candidates.sort(
                Comparator.comparingInt((Map.Entry<String, Entry> item) -> item.getValue().count() >= maxFailures ? 1 : 0)
                        .thenComparingLong(item -> item.getValue().firstFailureAt())
        );

        for (int index = 0; index < toRemove && index < candidates.size(); index++) {
            entries.remove(candidates.get(index).getKey(), candidates.get(index).getValue());
        }
    }

    private record Entry(long firstFailureAt, int count) {
    }
}
