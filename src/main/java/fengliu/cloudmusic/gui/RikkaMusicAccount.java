package fengliu.cloudmusic.gui;

import com.google.gson.JsonObject;
import fengliu.cloudmusic.command.MusicCommand;
import java.util.concurrent.*;

/** Owns cancellable login requests; stale QR workers cannot restore a logged-out account. */
public final class RikkaMusicAccount {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "RikkaMusic Account");
        thread.setDaemon(true);
        return thread;
    });
    private static Future<?> pending;
    private static long generation;
    private static String status = "idle";
    private static String message = "请选择登录方式";
    private static long lastCaptchaSent;

    public static synchronized JsonObject state() {
        JsonObject result = new JsonObject();
        result.addProperty("status", status);
        result.addProperty("message", message);
        return result;
    }

    public static synchronized JsonObject cancel() {
        generation++;
        if (pending != null) pending.cancel(true);
        status = "idle";
        message = "请选择登录方式";
        return state();
    }

    public static synchronized void logout() {
        cancel();
        MusicCommand.logout();
        RikkaMusicBrowse.clear();
    }

    public static synchronized JsonObject start(String method, String account, String secret, int countryCode) {
        cancel();
        long request = generation;
        status = "loading";
        message = method.equals("qr") ? "正在获取二维码" : "正在登录";
        pending = EXECUTOR.submit(() -> {
            try {
                var login = MusicCommand.getLoginMusic163();
                String cookie;
                if (method.equals("qr")) {
                    String key = login.qrKey();
                    synchronized (RikkaMusicAccount.class) {
                        if (request != generation) return;
                        login.getQRCodeTexture(key);
                        status = "waiting";
                        message = "请使用网易云音乐 App 扫码登录";
                    }
                    cookie = login.qrLogin(key);
                } else if (method.equals("password")) {
                    cookie = account.contains("@") ? login.email(account, secret)
                            : login.cellphone(Long.parseLong(account), secret, countryCode);
                } else if (method.equals("captcha")) {
                    cookie = login.cellphone(Long.parseLong(account), Integer.parseInt(secret), countryCode);
                } else throw new IllegalArgumentException("请选择有效的登录方式");
                synchronized (RikkaMusicAccount.class) {
                    if (request != generation || Thread.currentThread().isInterrupted()) return;
                    MusicCommand.setCookie(cookie);
                    RikkaMusicBrowse.clear();
                    status = "success";
                    message = "登录成功";
                }
            } catch (Exception error) {
                synchronized (RikkaMusicAccount.class) {
                    if (request != generation) return;
                    status = "error";
                    message = "登录失败，请检查账号信息；若平台要求安全验证，请使用扫码登录";
                }
            }
        });
        return state();
    }

    public static synchronized void sendCaptcha(String account, int countryCode) {
        long now = System.nanoTime();
        if (lastCaptchaSent != 0 && now - lastCaptchaSent < TimeUnit.SECONDS.toNanos(60))
            throw new IllegalStateException("请稍后再发送验证码");
        lastCaptchaSent = now;
        MusicCommand.getLoginMusic163().sendCaptcha(Long.parseLong(account), countryCode);
    }

    private RikkaMusicAccount() { }
}
