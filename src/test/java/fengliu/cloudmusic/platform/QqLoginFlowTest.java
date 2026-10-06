package fengliu.cloudmusic.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

class QqLoginFlowTest {
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
