package net.ccbluex.liquidbounce.integration.task;

import java.util.concurrent.TimeUnit;

/** Auto-select the default startup choice unless the user begins configuring it. */
public final class InitializationChoiceTimeout {
    private static final long DURATION = TimeUnit.SECONDS.toNanos(5);

    private final long started;
    private boolean interacted;

    public InitializationChoiceTimeout() {
        this(System.nanoTime());
    }

    InitializationChoiceTimeout(long started) {
        this.started = started;
    }

    public void interact() {
        interacted = true;
    }

    public boolean hasExpired() {
        return hasExpired(System.nanoTime());
    }

    boolean hasExpired(long now) {
        return !interacted && now - started >= DURATION;
    }

    public long secondsRemaining() {
        return secondsRemaining(System.nanoTime());
    }

    long secondsRemaining(long now) {
        if (interacted) return -1;
        long remaining = Math.max(0, DURATION - (now - started));
        return (remaining + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1);
    }
}
