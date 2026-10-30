package net.ccbluex.liquidbounce.integration.task;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;

/** Proxy routing is limited to initialization and restored once initialization finishes. */
public final class InitializationProxy extends ProxySelector {
    public enum Mode { DIRECT, SYSTEM, CUSTOM }
    private static final InitializationProxy INSTANCE = new InitializationProxy();
    private static final ProxySelector ORIGINAL = ProxySelector.getDefault();
    private static volatile Mode mode = Mode.DIRECT;
    private static volatile Proxy custom = Proxy.NO_PROXY;
    private static volatile Proxy systemHttp = Proxy.NO_PROXY;
    private static volatile Proxy systemHttps = Proxy.NO_PROXY;
    private static volatile String bypass = "";

    private InitializationProxy() { }
    public static ProxySelector selector() { return INSTANCE; }

    public static void configure(Mode choice, String address) throws IOException {
        Proxy http = Proxy.NO_PROXY;
        Proxy https = Proxy.NO_PROXY;
        String exclusions = "";
        Proxy selected = Proxy.NO_PROXY;
        if (choice == Mode.CUSTOM) selected = parse(address);
        if (choice == Mode.SYSTEM) {
            if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
                throw new IOException("System proxy selection is only supported on Windows");
            }
            String settings = registry();
            if (value(settings, "AutoConfigURL") != null || "0x1".equalsIgnoreCase(value(settings, "AutoDetect"))) {
                throw new IOException("System PAC/automatic detection is not supported; enter an HTTP proxy manually");
            }
            if (!"0x1".equalsIgnoreCase(value(settings, "ProxyEnable"))) {
                throw new IOException("Windows system proxy is disabled");
            }
            String servers = value(settings, "ProxyServer");
            if (servers == null || servers.isBlank()) throw new IOException("Windows proxy address is missing");
            if (!servers.contains("=")) {
                http = https = parse(servers);
            } else {
                for (String entry : servers.split(";")) {
                    String[] pair = entry.split("=", 2);
                    if (pair.length != 2) continue;
                    if (pair[0].equalsIgnoreCase("http")) http = parse(pair[1]);
                    if (pair[0].equalsIgnoreCase("https")) https = parse(pair[1]);
                }
                if (http == Proxy.NO_PROXY && https == Proxy.NO_PROXY) throw new IOException("No HTTP proxy in system settings");
            }
            exclusions = value(settings, "ProxyOverride");
        }
        custom = selected;
        systemHttp = http;
        systemHttps = https;
        bypass = exclusions == null ? "" : exclusions;
        mode = choice;
        ProxySelector.setDefault(INSTANCE);
        InitializationNetwork.report("Initialization proxy: " + choice);
    }

    public static void finish() {
        mode = Mode.DIRECT;
        if (ProxySelector.getDefault() == INSTANCE) ProxySelector.setDefault(ORIGINAL);
    }

    static Proxy parse(String text) throws IOException {
        try {
            String input = text.trim();
            URI uri = URI.create(input.contains("://") ? input : "http://" + input);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1 ||
                uri.getPort() > 65535 || uri.getUserInfo() != null || !uri.getPath().isEmpty() ||
                uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException();
            return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        } catch (RuntimeException error) {
            throw new IOException("Enter HTTP proxy as host:port (without credentials)");
        }
    }

    @Override public List<Proxy> select(URI uri) {
        String host = uri.getHost();
        if (host == null || host.equalsIgnoreCase("localhost") || host.startsWith("127.") ||
            host.equals("::1") || host.equals("[::1]") ||
            !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            return List.of(Proxy.NO_PROXY);
        }
        if (mode == Mode.DIRECT) return List.of(Proxy.NO_PROXY);
        if (mode == Mode.CUSTOM) return List.of(custom);
        for (String pattern : bypass.split(";")) {
            pattern = pattern.trim();
            if (pattern.equalsIgnoreCase("<local>") && !host.contains(".")) return List.of(Proxy.NO_PROXY);
            if (!pattern.isEmpty() && host.toLowerCase(Locale.ROOT).matches(
                java.util.regex.Pattern.quote(pattern.toLowerCase(Locale.ROOT)).replace("*", "\\E.*\\Q"))) {
                return List.of(Proxy.NO_PROXY);
            }
        }
        return List.of(uri.getScheme().equalsIgnoreCase("https") ? systemHttps : systemHttp);
    }

    @Override public void connectFailed(URI uri, SocketAddress address, IOException error) {
        InitializationNetwork.report("Proxy connection failed: " + error.getClass().getSimpleName());
    }

    private static String registry() throws IOException {
        Process process = new ProcessBuilder("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings")
            .redirectErrorStream(true).start();
        try {
            byte[] output = process.getInputStream().readAllBytes();
            if (process.waitFor() != 0) throw new IOException("Cannot read Windows proxy settings");
            return new String(output, Charset.defaultCharset());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Reading system proxy interrupted", error);
        }
    }

    private static String value(String output, String key) {
        for (String line : output.split("\\R")) {
            String[] parts = line.trim().split("\\s+", 3);
            if (parts.length == 3 && parts[0].equalsIgnoreCase(key)) return parts[2].trim();
        }
        return null;
    }
}
