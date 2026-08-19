package fengliu.cloudmusic.gui

import fengliu.cloudmusic.command.MusicCommand
import fengliu.cloudmusic.render.MusicIconTexture
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.drawTexQuad
import net.ccbluex.liquidbounce.render.engine.font.HorizontalAnchor
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.render.textureSetup
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

/** QR login uses real window coordinates; no full-screen canvas is scaled. */
class CloudMusicLoginScreen : Screen("RikkaMusic 登录".asPlainText()) {
    private var left = 0f
    private var top = 0f
    private var windowWidth = 480f
    private var windowHeight = 492f
    private var alive = false
    private var qrReady = false
    private var failed = false
    private var status = "正在获取二维码…"

    private fun x(raw: Float) = raw - left
    private fun y(raw: Float) = raw - top

    override fun init() {
        super.init()
        windowWidth = minOf(500f, width * .44f).coerceAtLeast(380f)
        windowHeight = minOf(520f, height * .72f).coerceAtLeast(420f)
        left = (width - windowWidth) / 2f
        top = (height - windowHeight) / 2f
        alive = true
        startLogin()
    }

    override fun removed() { alive = false; super.removed() }

    private fun startLogin() = CloudMusicAsync.run(
        job = { MusicCommand.getLoginMusic163().let { login -> login.qrKey().also(login::getQRCodeTexture) } },
        onSuccess = { key ->
            if (!alive) return@run
            qrReady = true
            status = "请使用网易云音乐 App 扫码登录"
            CloudMusicAsync.run(
                job = { MusicCommand.getLoginMusic163().qrLogin(key) },
                onSuccess = { cookie -> if (alive) { MusicCommand.setCookie(cookie); mc.gui.setScreen(CloudMusicScreen()) } },
                onError = { if (alive) { failed = true; status = "登录超时或二维码已过期" } },
            )
        },
        onError = { if (alive) { failed = true; status = "二维码获取失败，请检查网络连接" } },
    )

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (click.button() == 0 && x(click.x().toFloat()) in 20f..82f && y(click.y().toFloat()) in 18f..46f) mc.gui.setScreen(CloudMusicScreen())
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { mc.gui.setScreen(CloudMusicScreen()); return true }
        return super.keyPressed(input)
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        with(context) {
            drawRoundedRect(left, top, left + windowWidth, top + windowHeight, 6f, CloudMusicGui.BACKGROUND, outlineColor = CloudMusicGui.BORDER)
            pose().pushMatrix(); pose().translate(left, top)
            drawQuad(0f, 0f, windowWidth, windowHeight, CloudMusicGui.BACKGROUND)
            drawRoundedRect(20f, 18f, 82f, 46f, 4f, CloudMusicGui.ACTIVE, outlineColor = CloudMusicGui.BORDER)
            drawCloudMusicText("返回", 51f, 25f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM, horizontalAnchor = HorizontalAnchor.CENTER)
            drawCloudMusicText("RikkaMusic", windowWidth / 2f, 68f, CloudMusicGui.titleScale, CloudMusicGui.TEXT, horizontalAnchor = HorizontalAnchor.CENTER)
            drawCloudMusicText("网易云账号登录", windowWidth / 2f, 98f, CloudMusicGui.bodyScale, CloudMusicGui.TEXT_DIM, horizontalAnchor = HorizontalAnchor.CENTER)
            val size = minOf(236f, windowHeight - 205f); val qrX = (windowWidth - size) / 2f; val qrY = 128f
            drawRoundedRect(qrX - 8f, qrY - 8f, qrX + size + 8f, qrY + size + 8f, 4f, CloudMusicGui.TEXT, outlineColor = CloudMusicGui.BORDER)
            val texture = if (qrReady) mc.textureManager.getTexture(MusicIconTexture.QR_CODE_ID) else null
            if (texture != null) drawTexQuad(texture.textureSetup, qrX, qrY, qrX + size, qrY + size)
            else drawCloudMusicText("二维码加载中…", windowWidth / 2f, qrY + size / 2f - 8f, CloudMusicGui.bodyScale, CloudMusicGui.TEXT_FAINT, horizontalAnchor = HorizontalAnchor.CENTER)
            drawCloudMusicText(status, windowWidth / 2f, qrY + size + 28f, CloudMusicGui.bodyScale, if (failed) CloudMusicGui.ERROR else CloudMusicGui.TEXT_DIM, horizontalAnchor = HorizontalAnchor.CENTER)
            pose().popMatrix()
        }
    }

    override fun extractTransparentBackground(graphics: GuiGraphicsExtractor) = Unit
    override fun isPauseScreen() = false
}
