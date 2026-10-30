package net.ccbluex.liquidbounce.integration.task;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.Response;
import okhttp3.Request;
import com.google.gson.JsonParser;
import net.ccbluex.liquidbounce.api.core.HttpClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded, in-memory startup diagnostics. Never retain query strings or credentials. */
public final class InitializationNetwork {
    public static final Set<String> AFFECTED_COUNTRIES = Set.of("CN", "IR", "RU", "BY", "SY", "CU", "KP", "TM");
    private static final ArrayDeque<String> HISTORY = new ArrayDeque<>();
    private static final Set<Call> ACTIVE = ConcurrentHashMap.newKeySet();
    public static volatile boolean enabled = true;
    public static volatile boolean optimized;
    public static volatile String phase = "Checking initialization requirements / 检查初始化要求";

    private InitializationNetwork() { }

    public static String detectCountry() {
        // A call timeout also bounds blocking response-body reads, unlike a coroutine timeout alone.
        var client = HttpClient.getClient().newBuilder().callTimeout(4, java.util.concurrent.TimeUnit.SECONDS).build();
        try (var response = client.newCall(new Request.Builder().url("https://ipinfo.io/json").build()).execute()) {
            if (!response.isSuccessful()) return null;
            var value = JsonParser.parseString(response.body().string()).getAsJsonObject().get("country");
            return value == null || value.isJsonNull() ? null : value.getAsString().toUpperCase(java.util.Locale.ROOT);
        } catch (Exception error) {
            report("国家/地区检测失败：" + error.getClass().getSimpleName() + "；可手动选择初始化方式");
            return null;
        }
    }

    public static synchronized void report(String message) {
        if (!enabled) return;
        if (HISTORY.size() == 80) HISTORY.removeFirst();
        HISTORY.addLast(java.time.LocalTime.now().withNano(0) + " " + message);
    }

    public static synchronized List<String> history() { return List.copyOf(HISTORY); }
    public static int activeCount() { return ACTIVE.size(); }
    public static String address(HttpUrl url) {
        return url.scheme() + "://" + url.host() + (url.port() == 443 || url.port() == 80 ? "" : ":" + url.port())
            + url.encodedPath();
    }

    public static EventListener listener() {
        return new EventListener() {
            private boolean tracked;
            private String address;
            @Override public void callStart(Call call) {
                tracked = enabled;
                if (!tracked) return;
                address = address(call.request().url());
                ACTIVE.add(call);
                report("请求/检查缓存 " + call.request().method() + " " + address);
            }
            @Override public void dnsStart(Call call, String host) {
                if (tracked) report("DNS 解析 " + host);
            }
            @Override public void dnsEnd(Call call, String host, List<InetAddress> addresses) {
                if (tracked) report("DNS 完成 " + host);
            }
            @Override public void connectStart(Call call, InetSocketAddress socket, Proxy proxy) {
                if (tracked) report("Connection route: " + proxy.type() + " " + address);
            }
            @Override public void secureConnectStart(Call call) {
                if (tracked) report("正在协商 TLS 加密连接 " + address);
            }
            @Override public void requestHeadersEnd(Call call, okhttp3.Request request) {
                if (tracked) report("已发送请求，等待响应 " + address(request.url()));
            }
            @Override public void responseHeadersEnd(Call call, Response response) {
                if (tracked) report("HTTP " + response.code() + " " + address(response.request().url()));
            }
            @Override public void responseBodyEnd(Call call, long bytes) {
                if (tracked) report("接收完成 " + bytes + " bytes " + address);
            }
            @Override public void callEnd(Call call) { ACTIVE.remove(call); }
            @Override public void callFailed(Call call, IOException error) {
                ACTIVE.remove(call);
                if (tracked) report("请求失败 " + error.getClass().getSimpleName() + " " + address);
            }
        };
    }

    public static void finish() {
        InitializationProxy.finish();
        enabled = false;
        ACTIVE.clear();
    }
}
