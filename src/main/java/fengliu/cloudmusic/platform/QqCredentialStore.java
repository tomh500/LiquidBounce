package fengliu.cloudmusic.platform;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;

/** Local credentials only. This file must never be exposed through REST or included in Git. */
public final class QqCredentialStore {
    private final Path path;
    public QqCredentialStore(Path path) { this.path = path; }

    public JsonObject load() {
        if (!Files.exists(path)) return new JsonObject();
        try {
            JsonObject data = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (data.get("version").getAsInt() != 1) throw new IllegalArgumentException();
            return data;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("QQ 登录文件无法读取，原文件已保留，请重新登录");
        }
    }

    public void save(String cookie, String method, String nickname, long expiresAt) {
        JsonObject data = new JsonObject();
        data.addProperty("version", 1);
        data.addProperty("cookie", cookie);
        data.addProperty("method", method);
        data.addProperty("nickname", nickname);
        data.addProperty("expiresAt", expiresAt);
        Path temp = null;
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            temp = Files.createTempFile(path.toAbsolutePath().getParent(), "rikkamusic-qq-account-", ".tmp");
            Files.writeString(temp, new Gson().toJson(data));
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new IllegalStateException("QQ 登录凭据保存失败，请检查本机目录权限");
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    public void clear() {
        try { Files.deleteIfExists(path); }
        catch (IOException error) { throw new IllegalStateException("QQ 本地凭据清除失败，请检查目录权限"); }
    }
}
