package net.ccbluex.liquidbounce.integration.task;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class InitializationProxyTest {
    @Test void customProxyRoutesOkHttpAndJavaDownloadsAndRestoresSelector() throws Exception {
        var original = java.net.ProxySelector.getDefault();
        try (var server = new ServerSocket(0)) {
            server.setSoTimeout(5000);
            var requests = CompletableFuture.supplyAsync(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        try (var socket = server.accept()) {
                            socket.setSoTimeout(5000);
                            var input = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                            String line;
                            while ((line = input.readLine()) != null && !line.isEmpty()) { }
                            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".getBytes(StandardCharsets.US_ASCII));
                        }
                    }
                    return true;
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            InitializationProxy.configure(InitializationProxy.Mode.CUSTOM, "127.0.0.1:" + server.getLocalPort());
            assertEquals(Proxy.Type.DIRECT, InitializationProxy.selector().select(URI.create("http://localhost:9")).getFirst().type());
            var client = new OkHttpClient.Builder().proxySelector(InitializationProxy.selector()).build();
            try (var response = client.newCall(new Request.Builder().url("http://example.invalid/a").build()).execute()) {
                assertEquals("OK", response.body().string());
            }
            var connection = new URL("http://example.invalid/b").openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            assertEquals("OK", new String(connection.getInputStream().readAllBytes(), StandardCharsets.US_ASCII));
            assertTrue(requests.get(5, TimeUnit.SECONDS));
        } finally {
            InitializationProxy.finish();
            assertSame(original, java.net.ProxySelector.getDefault());
        }
    }

    @Test void rejectsUnsafeOrInvalidProxyAddresses() {
        assertThrows(java.io.IOException.class, () -> InitializationProxy.parse("http://user:password@example.com:1234"));
        assertThrows(java.io.IOException.class, () -> InitializationProxy.parse("socks://example.com:1080"));
        assertThrows(java.io.IOException.class, () -> InitializationProxy.parse("example.com:99999"));
    }
}
