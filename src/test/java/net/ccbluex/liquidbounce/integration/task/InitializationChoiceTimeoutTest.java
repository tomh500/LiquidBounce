package net.ccbluex.liquidbounce.integration.task;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitializationChoiceTimeoutTest {
    @Test
    void autoContinuesAfterFiveSecondsWithoutInteraction() {
        var timeout = new InitializationChoiceTimeout(100L);
        assertEquals(5L, timeout.secondsRemaining(100L));
        assertFalse(timeout.hasExpired(100L + TimeUnit.SECONDS.toNanos(4)));
        assertEquals(1L, timeout.secondsRemaining(100L + TimeUnit.SECONDS.toNanos(4)));
        assertTrue(timeout.hasExpired(100L + TimeUnit.SECONDS.toNanos(5)));
    }

    @Test
    void interactionCancelsAutomaticSelection() {
        var timeout = new InitializationChoiceTimeout(100L);
        timeout.interact();
        assertEquals(-1L, timeout.secondsRemaining(100L));
        assertFalse(timeout.hasExpired(100L + TimeUnit.SECONDS.toNanos(30)));
    }
}
