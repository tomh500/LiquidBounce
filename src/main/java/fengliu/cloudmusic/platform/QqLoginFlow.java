package fengliu.cloudmusic.platform;

import com.google.gson.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/** One isolated official QQ/WeChat authorization attempt; never logs cookies, callback URLs or codes. */
public final class QqLoginFlow implements QqMusicLogin.Attempt {
    private static final String WX_APP = "wx48db31d50e334801";
    private static final String QQ_APP = "100497308";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final String method;
    private final String oauthState = UUID.randomUUID().toString();
    private String qrIdentifier;
    public QqLoginFlow(String method) {
        if (!Set.of("qq", "wx").contains(method)) throw new IllegalArgumentException("请选择 QQ 或微信扫码");
        this.method = method;
    }
    @Override public void close() { http.shutdownNow(); }

    public record QrImage(byte[] bytes, String contentType) { }
    public record Poll(String status, JsonObject credential) { }

    public QrImage createQr() throws IOException, InterruptedException {
        if (method.equals("qq")) {
            var response = get("https://ssl.ptlogin2.qq.com/ptqrshow", Map.of(
                    "appid", "716027609", "e", "2", "l", "M", "s", "3", "d", "72", "v", "4",
                    "t", Double.toString(Math.random()), "daid", "383", "pt_3rd_aid", QQ_APP),
                    "https://xui.ptlogin2.qq.com/", "", 20);
            qrIdentifier = responseCookies(response).get("qrsig");
            if (qrIdentifier == null || qrIdentifier.isBlank()) throw new IllegalStateException("QQ 二维码获取失败，请刷新");
            return image(response, "image/png");
        }
        var page = get("https://open.weixin.qq.com/connect/qrconnect", Map.of(
                "appid", WX_APP, "redirect_uri", redirect("2"), "response_type", "code", "scope", "snsapi_login",
                "state", oauthState, "href", "https://y.qq.com/mediastyle/music_v17/src/css/popup_wechat.css#wechat_redirect"),
                "https://y.qq.com/", "", 20);
        var matcher = Pattern.compile("(?:uuid=|/connect/qrcode/)([A-Za-z0-9_-]+)").matcher(text(page));
        if (!matcher.find()) throw new IllegalStateException("微信二维码获取失败，请刷新");
        qrIdentifier = matcher.group(1);
        return image(get("https://open.weixin.qq.com/connect/qrcode/" + qrIdentifier, Map.of(),
                "https://open.weixin.qq.com/connect/qrconnect", "", 20), "image/jpeg");
    }

    private static QrImage image(HttpResponse<byte[]> response, String mime) {
        byte[] bytes = response.body();
        boolean png = bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        boolean jpeg = bytes.length > 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8;
        if (response.statusCode() != 200 || bytes.length > 2_000_000 || !(png || jpeg))
            throw new IllegalStateException("二维码图片不可用，请稍后重试");
        return new QrImage(bytes, png ? "image/png" : mime);
    }

    public Poll poll() throws IOException, InterruptedException {
        if (qrIdentifier == null) throw new IllegalStateException("请先获取二维码");
        if (method.equals("wx")) {
            HttpResponse<byte[]> response;
            try {
                response = get("https://lp.open.weixin.qq.com/connect/l/qrconnect",
                        Map.of("uuid", qrIdentifier, "_", Long.toString(System.currentTimeMillis())),
                        "https://open.weixin.qq.com/", "", 35);
            } catch (HttpTimeoutException timeout) { return new Poll("waiting", null); }
            String body = text(response);
            var status = Pattern.compile("wx_errcode\\s*=\\s*(\\d+)").matcher(body);
            if (!status.find()) throw new IllegalStateException("微信扫码状态获取失败，请刷新");
            String phase = phase(Integer.parseInt(status.group(1)));
            if (!phase.equals("success")) return new Poll(phase, null);
            var code = Pattern.compile("wx_code\\s*=\\s*['\"]([A-Za-z0-9_-]+)['\"]").matcher(body);
            if (!code.find()) throw new IllegalStateException("微信授权码不可用，请重新扫码");
            return new Poll("success", exchange("music.login.LoginServer", "Login",
                    Map.of("code", code.group(1), "strAppid", WX_APP), 1));
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("u1", "https://graph.qq.com/oauth2.0/login_jump");
        params.put("ptqrtoken", Integer.toString(hash33(qrIdentifier, 0)));
        params.putAll(Map.of("ptredirect", "0", "h", "1", "t", "1", "g", "1", "from_ui", "1", "ptlang", "2052",
                "action", "0-0-" + System.currentTimeMillis(), "js_ver", "20102616", "js_type", "1", "pt_uistyle", "40"));
        params.putAll(Map.of("aid", "716027609", "daid", "383", "pt_3rd_aid", QQ_APP, "has_onekey", "1"));
        var response = get("https://ssl.ptlogin2.qq.com/ptqrlogin", params,
                "https://xui.ptlogin2.qq.com/", "qrsig=" + qrIdentifier, 20);
        List<String> args = qqCallback(text(response));
        String phase = phase(Integer.parseInt(args.get(0)));
        if (!phase.equals("success")) return new Poll(phase, null);
        if (args.size() < 3) throw new IllegalStateException("QQ 授权响应不完整，请重新扫码");
        Map<String, String> callback = query(URI.create(args.get(2)).getRawQuery());
        String uin = callback.getOrDefault("uin", "");
        String signature = callback.getOrDefault("ptsigx", "");
        if (!uin.matches("[0-9]+") || signature.isEmpty()) throw new IllegalStateException("QQ 授权响应无效，请重新扫码");
        return new Poll("success", authorizeQq(uin, signature));
    }

    private JsonObject authorizeQq(String uin, String signature) throws IOException, InterruptedException {
        Map<String, String> params = new LinkedHashMap<>();
        params.putAll(Map.of("uin", uin, "pttype", "1", "service", "ptqrlogin", "nodirect", "0", "ptsigx", signature,
                "s_url", "https://graph.qq.com/oauth2.0/login_jump", "ptlang", "2052", "ptredirect", "100", "aid", "716027609", "daid", "383"));
        params.putAll(Map.of("j_later", "0", "low_login_hour", "0", "regmaster", "0", "pt_login_type", "3", "pt_aid", "0",
                "pt_aaid", "16", "pt_light", "0", "pt_3rd_aid", QQ_APP));
        var checked = get("https://ssl.ptlogin2.graph.qq.com/check_sig", params, "https://xui.ptlogin2.qq.com/", "", 20);
        Map<String, String> cookies = responseCookies(checked);
        if (!cookies.containsKey("p_skey")) throw new IllegalStateException("QQ 授权已失效，请重新扫码");
        Map<String, String> form = new LinkedHashMap<>();
        form.putAll(Map.of("response_type", "code", "client_id", QQ_APP, "redirect_uri", redirect("1"), "scope", "get_user_info,get_app_friends",
                "state", oauthState, "switch", "", "from_ptlogin", "1", "src", "1", "update_auth", "1", "openapi", "1010_1030"));
        form.put("g_tk", Integer.toString(hash33(cookies.get("p_skey"), 5381)));
        form.put("auth_time", Long.toString(System.currentTimeMillis()));
        form.put("ui", UUID.randomUUID().toString());
        String cookie = String.join("; ", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
        var response = http.send(base("https://graph.qq.com/oauth2.0/authorize", "https://graph.qq.com/", cookie, 20)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form))).build(), HttpResponse.BodyHandlers.ofByteArray());
        String location = response.headers().firstValue("Location").orElse("");
        URI uri = URI.create(location);
        if (!"y.qq.com".equals(uri.getHost()) || !"https".equals(uri.getScheme()))
            throw new IllegalStateException("QQ 音乐授权未完成，请在手机确认后重试");
        var values = query(uri.getRawQuery());
        if (!oauthState.equals(values.get("state")) || values.getOrDefault("code", "").isEmpty())
            throw new IllegalStateException("QQ 音乐授权码无效，请重新扫码");
        return exchange("QQConnectLogin.LoginServer", "QQLogin", Map.of("code", values.get("code")), 2);
    }

    private JsonObject exchange(String module, String method, Map<String, String> params, int type) throws IOException, InterruptedException {
        String body = new Gson().toJson(Map.of("comm", Map.of("ct", 24, "cv", 0, "uin", 0, "format", "json", "tmeLoginType", type),
                "req_0", Map.of("module", module, "method", method, "param", params)));
        var response = http.send(base("https://u.y.qq.com/cgi-bin/musicu.fcg", "https://y.qq.com/", "", 20)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
        JsonObject root = JsonParser.parseString(text(response)).getAsJsonObject();
        var block = root.getAsJsonObject("req_0");
        if (block == null || block.get("code").getAsInt() != 0 || !block.has("data"))
            throw new IllegalStateException("QQ 音乐未接受本次授权，请重新扫码或在官方客户端完成安全验证");
        var data = block.getAsJsonObject("data");
        if (data.has("code") && data.get("code").getAsInt() != 0)
            throw new IllegalStateException("QQ 音乐账号授权受限，请在官方客户端确认账号状态");
        data.addProperty("loginType", type);
        return data;
    }

    static List<String> qqCallback(String body) {
        var callback = Pattern.compile("ptuiCB\\((.*?)\\)", Pattern.DOTALL).matcher(body);
        if (!callback.find()) throw new IllegalStateException("QQ 扫码状态无效，请刷新");
        var quoted = Pattern.compile("'((?:\\\\.|[^'])*)'").matcher(callback.group(1));
        List<String> args = new ArrayList<>();
        while (quoted.find()) args.add(quoted.group(1));
        if (args.isEmpty() || !args.get(0).matches("[0-9]+")) throw new IllegalStateException("QQ 扫码状态无效，请刷新");
        return args;
    }
    static String phase(int code) {
        return switch (code) {
            case 0, 405 -> "success";
            case 66, 408 -> "waiting";
            case 67, 404 -> "confirming";
            case 65, 402 -> "expired";
            case 68, 403 -> "refused";
            default -> throw new IllegalStateException("扫码暂不可用，请稍后刷新二维码");
        };
    }
    static int hash33(String text, int seed) { int value = seed; for (int i = 0; i < text.length(); i++) value = value * 33 + text.charAt(i); return value & 0x7fffffff; }
    private static String redirect(String type) { return "https://y.qq.com/portal/wx_redirect.html?login_type=" + type + "&surl=https://y.qq.com/"; }
    private static String encode(Map<String, String> params) {
        return String.join("&", params.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).toList());
    }
    private static Map<String, String> query(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw != null) for (String part : raw.split("&")) {
            String[] entry = part.split("=", 2);
            if (entry.length == 2) result.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8), URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
        }
        return result;
    }
    private HttpRequest.Builder base(String url, String referer, String cookie, int timeout) {
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeout))
                .header("Referer", referer).header("User-Agent", "Mozilla/5.0");
        if (!cookie.isEmpty()) builder.header("Cookie", cookie);
        return builder;
    }
    private HttpResponse<byte[]> get(String url, Map<String, String> params, String referer, String cookie, int timeout) throws IOException, InterruptedException {
        return http.send(base(url + (params.isEmpty() ? "" : "?" + encode(params)), referer, cookie, timeout).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static String text(HttpResponse<byte[]> response) { return new String(response.body(), StandardCharsets.UTF_8); }
    private static Map<String, String> responseCookies(HttpResponse<?> response) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String header : response.headers().allValues("Set-Cookie"))
            for (HttpCookie cookie : HttpCookie.parse(header)) result.put(cookie.getName(), cookie.getValue());
        return result;
    }
}
