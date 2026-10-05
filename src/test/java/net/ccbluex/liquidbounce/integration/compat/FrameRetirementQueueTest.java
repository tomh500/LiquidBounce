package net.ccbluex.liquidbounce.integration.compat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameRetirementQueueTest {
    @Test
    void keepsResourcesAliveForTwoFramesAndPreservesContextOrder() {
        var queue = new FrameRetirementQueue();
        var closed = new ArrayList<String>();
        queue.defer(() -> closed.add("browser"));
        queue.defer(() -> closed.add("context"));
        queue.endFrame();
        assertEquals(List.of(), closed);
        queue.endFrame();
        assertEquals(List.of("browser", "context"), closed);
        queue.endFrame();
        assertEquals(2, closed.size());
    }

    @Test
    void laterRetirementsWaitForTheirOwnFrames() {
        var queue = new FrameRetirementQueue();
        var closed = new ArrayList<String>();
        queue.defer(() -> closed.add("first"));
        queue.endFrame();
        queue.defer(() -> closed.add("second"));
        queue.endFrame();
        assertEquals(List.of("first"), closed);
        queue.endFrame();
        assertEquals(List.of("first", "second"), closed);
    }

    @Test
    void flushesBeforeShutdownWithoutClosingAnythingTwice() {
        var queue = new FrameRetirementQueue();
        var closed = new ArrayList<String>();
        queue.defer(() -> closed.add("browser"));
        queue.defer(() -> closed.add("context"));
        queue.flush();
        queue.flush();
        queue.endFrame();
        queue.endFrame();
        assertEquals(List.of("browser", "context"), closed);
    }

    @Test
    void aFailingCloseDoesNotLeakOtherRetiredResources() {
        var queue = new FrameRetirementQueue();
        var closed = new ArrayList<String>();
        queue.defer(() -> { throw new IllegalStateException("Failed native close"); });
        queue.defer(() -> closed.add("context"));
        assertThrows(IllegalStateException.class, queue::flush);
        assertEquals(List.of("context"), closed);
        queue.flush();
        assertEquals(1, closed.size());
    }

    @Test
    void resourcesRetiredDuringCleanupWaitForTheirOwnFrames() {
        var queue = new FrameRetirementQueue();
        var closed = new ArrayList<String>();
        queue.defer(() -> queue.defer(() -> closed.add("next")));
        queue.endFrame();
        queue.endFrame();
        queue.endFrame();
        assertEquals(List.of(), closed);
        queue.endFrame();
        assertEquals(List.of("next"), closed);
    }
}
