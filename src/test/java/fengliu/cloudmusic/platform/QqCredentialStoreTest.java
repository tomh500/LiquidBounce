package fengliu.cloudmusic.platform;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class QqCredentialStoreTest {
    @TempDir Path directory;

    @Test void credentialsSurviveReloadButNeverAppearInPublicStateAndLogoutClearsDisk() throws Exception {
        Path file = directory.resolve("account.json");
        QqMusicApi.initializeStorage(file);
        JsonObject credential = new JsonObject();
        credential.addProperty("musicid", "123456");
        credential.addProperty("musickey", "W_X_dummy-test-credential");
        credential.addProperty("nick", "Test account");
        credential.addProperty("musickeyCreateTime", 1);
        credential.addProperty("keyExpiresIn", 10);
        QqMusicApi.installLogin(credential, "wx");
        assertTrue(Files.exists(file));
        QqMusicApi.initializeStorage(file);
        var state = QqMusicApi.sessionState();
        assertTrue(state.get("configured").getAsBoolean());
        assertTrue(state.get("persisted").getAsBoolean());
        assertTrue(state.get("expired").getAsBoolean());
        assertEquals("wx", state.get("method").getAsString());
        assertFalse(state.toString().contains("dummy-test-credential"));
        assertFalse(QqMusicLogin.state().toString().contains("dummy-test-credential"));
        QqMusicLogin.logout();
        assertFalse(Files.exists(file));
        QqMusicApi.initializeStorage(file);
        assertFalse(QqMusicApi.hasSession());
    }

    @Test void unreadableFileIsPreservedAndReportedWithoutItsContents() throws Exception {
        Path file = directory.resolve("account.json");
        Files.writeString(file, "secret broken data");
        QqMusicApi.initializeStorage(file);
        assertFalse(QqMusicApi.hasSession());
        assertFalse(QqMusicApi.sessionState().get("storageError").getAsString().isEmpty());
        assertFalse(QqMusicApi.sessionState().toString().contains("secret broken data"));
        assertEquals("secret broken data", Files.readString(file));
    }

    @Test void cancelledQrCannotStillBeRead() {
        long version = QqMusicLogin.state().get("qrVersion").getAsLong();
        QqMusicLogin.cancel();
        assertThrows(IllegalArgumentException.class, () -> QqMusicLogin.image(version));
        assertFalse(QqMusicLogin.state().get("qrReady").getAsBoolean());
    }

    @Test void lateAuthorizationCannotRestoreALoggedOutAccount() throws Exception {
        Path file = directory.resolve("account.json");
        QqMusicApi.initializeStorage(file);
        CountDownLatch polling = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        QqMusicLogin.Attempt delayed = new QqMusicLogin.Attempt() {
            public QqLoginFlow.QrImage createQr() { return new QqLoginFlow.QrImage(new byte[]{1}, "image/png"); }
            public QqLoginFlow.Poll poll() {
                polling.countDown();
                boolean finished = false;
                while (!finished) try { release.await(); finished = true; } catch (InterruptedException ignored) { }
                JsonObject credential = new JsonObject();
                credential.addProperty("musicid", 123);
                credential.addProperty("musickey", "test-late-result");
                return new QqLoginFlow.Poll("success", credential);
            }
            public void close() { closed.countDown(); }
        };
        try {
            QqMusicLogin.start("qq", delayed);
            assertTrue(polling.await(5, TimeUnit.SECONDS));
            QqMusicLogin.logout();
            release.countDown();
            assertTrue(closed.await(5, TimeUnit.SECONDS));
            assertFalse(QqMusicApi.hasSession());
            assertFalse(Files.exists(file));
            assertEquals("idle", QqMusicLogin.state().get("status").getAsString());
        } finally { release.countDown(); QqMusicLogin.cancel(); }
    }
}
