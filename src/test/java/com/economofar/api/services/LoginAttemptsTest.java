package com.economofar.api.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class LoginAttemptsTest {

    private static final long MINUTE = 60_000L;

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private LoginAttempts attempts() {
        return new LoginAttempts(now::get, 5, 15 * MINUTE, 1_000);
    }

    private static void fail(LoginAttempts attempts, String key, int times) {
        for (int count = 0; count < times; count++) {
            attempts.recordFailure(key);
        }
    }

    @Test
    @DisplayName("an address that never failed is not locked")
    void freshAddress() {
        assertFalse(attempts().isLocked("bob@mail.com"));
    }

    @Test
    @DisplayName("four failures do not lock, the fifth does")
    void locksOnTheFifth() {
        LoginAttempts attempts = attempts();

        fail(attempts, "bob@mail.com", 4);
        assertFalse(attempts.isLocked("bob@mail.com"));

        attempts.recordFailure("bob@mail.com");
        assertTrue(attempts.isLocked("bob@mail.com"));
    }

    @Test
    @DisplayName("more failures than the limit keep the address locked")
    void staysLocked() {
        LoginAttempts attempts = attempts();

        fail(attempts, "bob@mail.com", 50);

        assertTrue(attempts.isLocked("bob@mail.com"));
    }

    @Test
    @DisplayName("the lock lasts 15 minutes from the first failure, not from the last one")
    void windowStartsAtTheFirstFailure() {
        LoginAttempts attempts = attempts();

        attempts.recordFailure("bob@mail.com");
        now.addAndGet(14 * MINUTE);
        fail(attempts, "bob@mail.com", 4);
        assertTrue(attempts.isLocked("bob@mail.com"));

        now.addAndGet(MINUTE);
        assertTrue(attempts.isLocked("bob@mail.com"), "exactly 15 minutes after the first failure is still inside the window");

        now.addAndGet(1);
        assertFalse(attempts.isLocked("bob@mail.com"), "one millisecond later the window is over");
    }

    @Test
    @DisplayName("after the window a new failure starts a new count at one")
    void newWindowStartsAtOne() {
        LoginAttempts attempts = attempts();

        fail(attempts, "bob@mail.com", 5);
        now.addAndGet(16 * MINUTE);

        attempts.recordFailure("bob@mail.com");
        assertFalse(attempts.isLocked("bob@mail.com"));

        fail(attempts, "bob@mail.com", 3);
        assertFalse(attempts.isLocked("bob@mail.com"));

        attempts.recordFailure("bob@mail.com");
        assertTrue(attempts.isLocked("bob@mail.com"));
    }

    @Test
    @DisplayName("a correct sign-in clears the count")
    void clearResets() {
        LoginAttempts attempts = attempts();

        fail(attempts, "bob@mail.com", 5);
        assertTrue(attempts.isLocked("bob@mail.com"));

        attempts.clear("bob@mail.com");
        assertFalse(attempts.isLocked("bob@mail.com"));
        assertEquals(0, attempts.tracked());

        fail(attempts, "bob@mail.com", 4);
        assertFalse(attempts.isLocked("bob@mail.com"));
    }

    @Test
    @DisplayName("each address has its own count")
    void addressesAreIndependent() {
        LoginAttempts attempts = attempts();

        fail(attempts, "bob@mail.com", 5);

        assertTrue(attempts.isLocked("bob@mail.com"));
        assertFalse(attempts.isLocked("alice@mail.com"));

        attempts.clear("alice@mail.com");
        assertTrue(attempts.isLocked("bob@mail.com"));
    }

    @Test
    @DisplayName("an expired entry is dropped when it is looked at")
    void expiredEntryIsDropped() {
        LoginAttempts attempts = attempts();

        fail(attempts, "bob@mail.com", 2);
        assertEquals(1, attempts.tracked());

        now.addAndGet(16 * MINUTE);
        assertFalse(attempts.isLocked("bob@mail.com"));
        assertEquals(0, attempts.tracked());
    }

    @Test
    @DisplayName("the default limits are 5 failures in 15 minutes")
    void defaultLimits() {
        assertEquals(5, LoginAttempts.MAX_FAILURES);
        assertEquals(15L * 60L * 1000L, LoginAttempts.WINDOW_MILLIS);
        assertEquals(10_000, LoginAttempts.MAX_TRACKED);

        LoginAttempts attempts = new LoginAttempts();

        fail(attempts, "bob@mail.com", 4);
        assertFalse(attempts.isLocked("bob@mail.com"));

        attempts.recordFailure("bob@mail.com");
        assertTrue(attempts.isLocked("bob@mail.com"));
    }

    @Test
    @DisplayName("the table never holds more addresses than its limit")
    void tableIsBounded() {
        LoginAttempts attempts = new LoginAttempts(now::get, 5, 15 * MINUTE, 50);

        for (int number = 0; number < 500; number++) {
            attempts.recordFailure("user" + number + "@mail.com");
            now.addAndGet(10);
            assertTrue(attempts.tracked() <= 50, "after " + (number + 1) + " addresses the table holds " + attempts.tracked());
        }

        assertTrue(attempts.tracked() > 40, "the table is trimmed a little, not emptied");
    }

    @Test
    @DisplayName("when the table is full the oldest addresses go first and the newest stay")
    void oldestGoFirst() {
        LoginAttempts attempts = new LoginAttempts(now::get, 5, 15 * MINUTE, 20);

        for (int number = 0; number < 100; number++) {
            fail(attempts, "user" + number + "@mail.com", 5);
            now.addAndGet(10);
        }

        assertTrue(attempts.isLocked("user99@mail.com"));
        assertTrue(attempts.isLocked("user95@mail.com"));
        assertFalse(attempts.isLocked("user0@mail.com"));
        assertFalse(attempts.isLocked("user10@mail.com"));
    }

    @Test
    @DisplayName("when the table is full the expired addresses are dropped before the others")
    void expiredGoBeforeFresh() {
        LoginAttempts attempts = new LoginAttempts(now::get, 5, 15 * MINUTE, 10);

        for (int number = 0; number < 10; number++) {
            fail(attempts, "old" + number + "@mail.com", 5);
            now.addAndGet(10);
        }

        now.addAndGet(20 * MINUTE);

        fail(attempts, "fresh@mail.com", 5);
        assertEquals(1, attempts.tracked());
        assertTrue(attempts.isLocked("fresh@mail.com"));
    }

    @Test
    @DisplayName("a very long address is cut, so one request cannot store a huge key")
    void longKeysAreCut() {
        LoginAttempts attempts = attempts();
        String head = "a".repeat(LoginAttempts.MAX_KEY_LENGTH);

        fail(attempts, head + "1", 3);
        fail(attempts, head + "2", 2);

        assertEquals(1, attempts.tracked());
        assertTrue(attempts.isLocked(head + "3"));
        assertTrue(attempts.isLocked(head));
    }

    @Test
    @DisplayName("an address of exactly the maximum length is kept whole")
    void keyOfTheMaximumLength() {
        LoginAttempts attempts = attempts();
        String exact = "b".repeat(LoginAttempts.MAX_KEY_LENGTH);
        String other = "b".repeat(LoginAttempts.MAX_KEY_LENGTH - 1) + "c";

        fail(attempts, exact, 5);

        assertTrue(attempts.isLocked(exact));
        assertFalse(attempts.isLocked(other));
    }

    @Test
    @DisplayName("a missing address is handled like an empty one")
    void nullKey() {
        LoginAttempts attempts = attempts();

        fail(attempts, null, 5);

        assertTrue(attempts.isLocked(null));
        assertTrue(attempts.isLocked(""));
        attempts.clear(null);
        assertFalse(attempts.isLocked(""));
    }

    @Test
    @DisplayName("limits that make no sense are refused")
    void invalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> new LoginAttempts(null, 5, 1_000, 10));
        assertThrows(IllegalArgumentException.class, () -> new LoginAttempts(now::get, 0, 1_000, 10));
        assertThrows(IllegalArgumentException.class, () -> new LoginAttempts(now::get, 5, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new LoginAttempts(now::get, 5, 1_000, 0));
    }

    @Test
    @DisplayName("a limit of one failure locks at the first failure")
    void limitOfOne() {
        LoginAttempts attempts = new LoginAttempts(now::get, 1, MINUTE, 10);

        assertFalse(attempts.isLocked("bob@mail.com"));
        attempts.recordFailure("bob@mail.com");
        assertTrue(attempts.isLocked("bob@mail.com"));
    }

    @Test
    @DisplayName("the smallest limits that make sense are accepted")
    void smallestLimits() {
        LoginAttempts attempts = new LoginAttempts(now::get, 1, 1, 1);

        attempts.recordFailure("bob@mail.com");
        assertTrue(attempts.isLocked("bob@mail.com"));

        now.addAndGet(2);
        assertFalse(attempts.isLocked("bob@mail.com"));
    }

    @Test
    @DisplayName("a full table drops 10 percent more than it must, so it does not sort again at every failure")
    void trimmedSize() {
        LoginAttempts attempts = new LoginAttempts(now::get, 5, 15 * MINUTE, 50);

        for (int number = 0; number < 51; number++) {
            fail(attempts, "user" + number + "@mail.com", 5);
            now.addAndGet(10);
        }

        assertEquals(45, attempts.tracked());
        assertFalse(attempts.isLocked("user5@mail.com"));
        assertTrue(attempts.isLocked("user6@mail.com"));
        assertTrue(attempts.isLocked("user50@mail.com"));
    }

    @Test
    @DisplayName("a very small table drops at least one more address than it must")
    void smallTableDropsOneMore() {
        LoginAttempts attempts = new LoginAttempts(now::get, 5, 15 * MINUTE, 5);

        for (int number = 0; number < 6; number++) {
            fail(attempts, "user" + number + "@mail.com", 5);
            now.addAndGet(10);
        }

        assertEquals(4, attempts.tracked());
        assertFalse(attempts.isLocked("user1@mail.com"));
        assertTrue(attempts.isLocked("user2@mail.com"));
    }

    @Test
    @DisplayName("when the expired addresses are enough to make room, no fresh address is dropped")
    void freshAddressesStayWhenTheExpiredAreEnough() {
        LoginAttempts attempts = new LoginAttempts(now::get, 5, 15 * MINUTE, 3);

        fail(attempts, "old@mail.com", 5);
        now.addAndGet(16 * MINUTE);

        fail(attempts, "a@mail.com", 5);
        fail(attempts, "b@mail.com", 5);
        fail(attempts, "c@mail.com", 5);

        assertEquals(3, attempts.tracked());
        assertTrue(attempts.isLocked("a@mail.com"));
        assertTrue(attempts.isLocked("b@mail.com"));
        assertTrue(attempts.isLocked("c@mail.com"));
    }

    @Test
    @DisplayName("a flood of made-up addresses does not push out a locked address")
    void lockedAddressSurvivesAFlood() {
        LoginAttempts attempts = new LoginAttempts(now::get, 5, 15 * MINUTE, 20);

        fail(attempts, "victim@mail.com", 5);
        now.addAndGet(10);

        for (int number = 0; number < 200; number++) {
            attempts.recordFailure("made-up" + number + "@mail.com");
            now.addAndGet(10);
            assertTrue(attempts.tracked() <= 20);
        }

        assertTrue(attempts.isLocked("victim@mail.com"));
    }

    @Test
    @DisplayName("the address that is being recorded is never the one dropped")
    void recordedAddressIsKept() {
        LoginAttempts attempts = new LoginAttempts(now::get, 1, 15 * MINUTE, 1);

        attempts.recordFailure("a@mail.com");
        now.addAndGet(10);
        attempts.recordFailure("b@mail.com");

        assertEquals(1, attempts.tracked());
        assertTrue(attempts.isLocked("b@mail.com"));
        assertFalse(attempts.isLocked("a@mail.com"));
    }

    @Test
    @DisplayName("when everything else is locked, the oldest locked addresses go and the new one stays")
    void lockedAddressesGoLast() {
        LoginAttempts attempts = new LoginAttempts(now::get, 5, 15 * MINUTE, 5);

        for (int number = 0; number < 5; number++) {
            fail(attempts, "locked" + number + "@mail.com", 5);
            now.addAndGet(10);
        }

        attempts.recordFailure("new@mail.com");

        assertEquals(4, attempts.tracked());
        assertFalse(attempts.isLocked("locked0@mail.com"));
        assertFalse(attempts.isLocked("locked1@mail.com"));
        assertTrue(attempts.isLocked("locked2@mail.com"));
        assertTrue(attempts.isLocked("locked4@mail.com"));

        fail(attempts, "new@mail.com", 4);
        assertTrue(attempts.isLocked("new@mail.com"));
    }
}
