package net.ccbluex.liquidbounce.integration.task;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class InitializationNetworkTest {
    @Test void hidesCredentialsAndQueriesAndTracksFailedCalls() {
        InitializationNetwork.enabled = true;
        var client = new OkHttpClient();
        var call = client.newCall(new Request.Builder().url("https://user:secret@example.com/library?token=private#fragment").build());
        var listener = InitializationNetwork.listener();
        try {
            listener.callStart(call);
            assertEquals(1, InitializationNetwork.activeCount());
            listener.callFailed(call, new IOException("token=private"));
            assertEquals(0, InitializationNetwork.activeCount());
            var diagnostics = String.join("\n", InitializationNetwork.history());
            assertTrue(diagnostics.contains("https://example.com/library"));
            assertFalse(diagnostics.contains("secret"));
            assertFalse(diagnostics.contains("private"));
            assertFalse(diagnostics.contains("fragment"));
        } finally {
            InitializationNetwork.finish();
        }
    }

    @Test void boundsHistoryAndStopsTrackingAfterInitialization() {
        InitializationNetwork.enabled = true;
        for (int i = 0; i < 100; i++) InitializationNetwork.report("entry-" + i);
        assertEquals(80, InitializationNetwork.history().size());
        assertTrue(InitializationNetwork.history().getFirst().endsWith("entry-20"));
        InitializationNetwork.finish();
        var before = InitializationNetwork.history();
        InitializationNetwork.report("must not be recorded");
        assertEquals(before, InitializationNetwork.history());
        var call = new OkHttpClient().newCall(new Request.Builder().url("https://example.com/").build());
        InitializationNetwork.listener().callStart(call);
        assertEquals(0, InitializationNetwork.activeCount());
    }
}
