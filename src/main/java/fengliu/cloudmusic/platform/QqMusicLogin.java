package fengliu.cloudmusic.platform;

import com.google.gson.JsonObject;
import java.util.concurrent.*;

/** Generation-scoped workers ensure cancel/logout/new QR always win over late network responses. */
public final class QqMusicLogin {
    interface Attempt extends AutoCloseable {
        QqLoginFlow.QrImage createQr() throws Exception;
        QqLoginFlow.Poll poll() throws Exception;
        @Override void close();
    }
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "RikkaMusic QQ Login");
        thread.setDaemon(true);
        return thread;
    });
    private static Future<?> pending;
    private static long generation;
    private static String method = "qq";
    private static String status = "idle";
    private static String message = "请选择 QQ 或微信扫码登录";
    private static QqLoginFlow.QrImage qr;

    public static synchronized JsonObject state() {
        JsonObject data = QqMusicApi.sessionState();
        data.addProperty("status", status);
        data.addProperty("message", message);
        data.addProperty("qrMethod", method);
        data.addProperty("qrVersion", generation);
        data.addProperty("qrReady", qr != null);
        // A clean official entry point, never the session-bearing redirect from the QR request.
        if (status.equals("consent_required")) data.addProperty("authorizationUrl", "https://y.qq.com/");
        return data;
    }

    public static synchronized QqLoginFlow.QrImage image(long version) {
        if (version != generation || qr == null) throw new IllegalArgumentException("二维码已失效，请刷新");
        return new QqLoginFlow.QrImage(qr.bytes().clone(), qr.contentType());
    }

    public static synchronized JsonObject cancel() {
        generation++;
        if (pending != null) pending.cancel(true);
        pending = null;
        qr = null;
        status = "idle";
        message = "请选择 QQ 或微信扫码登录";
        return state();
    }

    public static synchronized JsonObject logout() {
        cancel();
        QqMusicApi.setSession("");
        message = "已退出 QQ 音乐，本机保存的凭据已清除";
        return state();
    }

    public static synchronized JsonObject saveManual(String cookie) {
        cancel();
        QqMusicApi.setSession(cookie);
        message = cookie.isBlank() ? "QQ 登录凭据已清除" : "QQ 登录凭据已保存在本机，播放时验证权限";
        return state();
    }

    public static synchronized JsonObject start(String requestedMethod) {
        return start(requestedMethod, new QqLoginFlow(requestedMethod));
    }

    static synchronized JsonObject start(String requestedMethod, Attempt flow) {
        cancel();
        method = requestedMethod;
        long attempt = generation;
        status = "loading";
        message = "正在获取" + (method.equals("qq") ? " QQ " : "微信") + "二维码";
        pending = EXECUTOR.submit(() -> run(flow, attempt, requestedMethod));
        return state();
    }

    private static void run(Attempt flow, long attempt, String requestedMethod) {
        try {
            var image = flow.createQr();
            synchronized (QqMusicLogin.class) {
                if (attempt != generation || Thread.currentThread().isInterrupted()) return;
                qr = image;
                status = "waiting";
                message = "请使用" + (requestedMethod.equals("qq") ? "手机 QQ" : "微信") + "扫码，并在手机上确认登录";
            }
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);
            while (System.nanoTime() < deadline) {
                var result = flow.poll();
                synchronized (QqMusicLogin.class) {
                    if (attempt != generation || Thread.currentThread().isInterrupted()) return;
                    status = result.status();
                    switch (status) {
                        case "success" -> {
                            QqMusicApi.installLogin(result.credential(), requestedMethod);
                            qr = null;
                            message = "QQ 音乐登录成功，凭据已保存在本机";
                            return;
                        }
                        case "confirming" -> message = "已扫码，请在手机上确认登录";
                        case "expired" -> { qr = null; message = "二维码已过期，请刷新"; return; }
                        case "refused" -> { qr = null; message = "已取消授权，可重新获取二维码"; return; }
                        default -> { }
                    }
                }
                Thread.sleep(1500);
            }
            synchronized (QqMusicLogin.class) {
                if (attempt == generation) { status = "expired"; qr = null; message = "二维码已过期，请刷新"; }
            }
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            synchronized (QqMusicLogin.class) {
                if (attempt != generation) return;
                status = error instanceof QqLoginFlow.ConsentRequiredException ? "consent_required" : "error";
                qr = null;
                // All flow IllegalStateException messages are authored locally; never forward HTTP bodies/URLs.
                message = error instanceof IllegalStateException ? error.getMessage() : "扫码登录失败，请检查网络后刷新二维码";
            }
        } finally {
            flow.close();
        }
    }

    private QqMusicLogin() { }
}
