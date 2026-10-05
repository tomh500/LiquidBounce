package net.ccbluex.liquidbounce.integration.compat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Keeps native browser resources alive until submitted GUI frames have finished using them. */
public final class FrameRetirementQueue {
    public static final FrameRetirementQueue BROWSERS = new FrameRetirementQueue();

    private final ArrayDeque<Retirement> pending = new ArrayDeque<>();
    private long frame;

    public synchronized void defer(Runnable close) {
        pending.addLast(new Retirement(frame + 2, Objects.requireNonNull(close)));
    }

    /** Called after rendering, independently of the client's GameRenderEvent timing. */
    public void endFrame() {
        List<Runnable> ready = new ArrayList<>();
        synchronized (this) {
            frame++;
            while (!pending.isEmpty() && pending.peekFirst().frame() <= frame) {
                ready.add(pending.removeFirst().close());
            }
        }
        runAll(ready);
    }

    /** Called before CEF shutdown, while its runtime is still available. */
    public void flush() {
        List<Runnable> ready;
        synchronized (this) {
            ready = pending.stream().map(Retirement::close).toList();
            pending.clear();
        }
        runAll(ready);
    }

    private static void runAll(List<Runnable> tasks) {
        RuntimeException failure = null;
        for (Runnable task : tasks) {
            try {
                task.run();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else if (failure != exception) {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private record Retirement(long frame, Runnable close) { }
}
