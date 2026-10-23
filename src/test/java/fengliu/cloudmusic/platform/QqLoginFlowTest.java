package fengliu.cloudmusic.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

class QqLoginFlowTest {
    @Test void recognizesOfficialConsentRedirectWithoutTreatingItAsLoginSuccess() {
        for (String target : new String[]{"https://graph.qq.com/oauth2.0/show?code=secret", "/oauth2.0/show?code=secret"}) {
            var error = assertThrows(QqLoginFlow.ConsentRequiredException.class,
                    () -> QqLoginFlow.authorizationCode(302, target, "state"));
            assertFalse(error.getMessage().contains("secret"));
        }
        assertThrows(IllegalStateException.class, () -> QqLoginFlow.authorizationCode(302,
                "https://graph.qq.com.attacker.invalid/oauth2.0/show", "state"));
    }
    @Test void redirectDiagnosticsNeverExposeAuthorizationQueryOrUnknownPaths() {
        assertEquals("graph.qq.com/consent-page; error=100010", QqLoginFlow.safeRedirectDiagnostic(
                java.net.URI.create("https://graph.qq.com/oauth2.0/show?code=secret&uin=private&error=100010#token")));
        assertEquals("other-host/other-path", QqLoginFlow.safeRedirectDiagnostic(
                java.net.URI.create("https://private.invalid/secret?code=secret")));
        assertEquals("test-code", QqLoginFlow.authorizationCode(302,
                "https://y.qq.com:443/portal/wx_redirect.html?code=test-code&state=test-state", "test-state"));
    }
    @Test void acceptsRegisteredCallbackWithoutFollowingItsHttpLocation() {
        for (String scheme : new String[]{"https", "http"}) {
            assertEquals("test-code", QqLoginFlow.authorizationCode(302,
                    scheme + "://y.qq.com/portal/wx_redirect.html?login_type=1&code=test-code&state=test-state", "test-state"));
        }
    }

    @Test void rejectsForeignCallbacksAndMismatchedStateWithoutExposingSecrets() {
        for (String location : new String[]{
                "https://y.qq.com.attacker.invalid/portal/wx_redirect.html?code=secret&state=test-state",
                "https://y.qq.com/other?code=secret&state=test-state",
                "https://user@y.qq.com/portal/wx_redirect.html?code=secret&state=test-state",
                "https://y.qq.com/portal/wx_redirect.html?code=secret&state=wrong",
                "https://y.qq.com/portal/wx_redirect.html?state=test-state", ""}) {
            var error = assertThrows(IllegalStateException.class,
                    () -> QqLoginFlow.authorizationCode(302, location, "test-state"));
            assertFalse(error.getMessage().contains("secret"));
        }
        assertTrue(assertThrows(IllegalStateException.class,
                () -> QqLoginFlow.authorizationCode(403, "", "test-state")).getMessage().contains("HTTP 403"));
    }

    @Test void parsesCallbackWithoutEvaluatingServerJavascript() {
        var values = QqLoginFlow.qqCallback("ptuiCB('66','0','','0','等待扫码','');");
        assertEquals("66", values.getFirst());
        assertEquals("waiting", QqLoginFlow.phase(Integer.parseInt(values.getFirst())));
        assertEquals("confirming", QqLoginFlow.phase(404));
        assertEquals("expired", QqLoginFlow.phase(65));
        assertEquals("refused", QqLoginFlow.phase(403));
        assertEquals("success", QqLoginFlow.phase(405));
        assertThrows(IllegalStateException.class, () -> QqLoginFlow.qqCallback("<html>login blocked</html>"));
        assertThrows(IllegalStateException.class, () -> QqLoginFlow.phase(999));
    }

    @Test void usesTheSigned32BitHashRequiredByTencent() {
        assertEquals(108966, QqLoginFlow.hash33("abc", 0));
        assertEquals(193485963, QqLoginFlow.hash33("abc", 5381));
        assertTrue(QqLoginFlow.hash33("a".repeat(100), 0) >= 0);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RIKKA_QQ_QR_SMOKE", matches = "1")
    void officialQqAndWechatEndpointsReturnImagesAndWaitingStates() throws Exception {
        for (String method : new String[]{"qq", "wx"}) {
            try (var flow = new QqLoginFlow(method)) {
            var qr = flow.createQr();
            assertTrue(qr.bytes().length > 100, method);
            assertTrue(qr.contentType().startsWith("image/"), method);
            var poll = flow.poll();
            assertEquals("waiting", poll.status(), method);
            assertNull(poll.credential(), method);
            }
        }
    }
}
