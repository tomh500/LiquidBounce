/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package fengliu.cloudmusic.gui

import fengliu.cloudmusic.command.MusicCommand
import fengliu.cloudmusic.render.MusicIconTexture
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.drawTexQuad
import net.ccbluex.liquidbounce.render.engine.font.HorizontalAnchor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.render.textureSetup
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

/**
 * QR code login screen for the merged CloudMusic module.
 */
class CloudMusicLoginScreen : Screen("网易云音乐登录".asPlainText()) {

    private var started = false
    private var status = "正在获取二维码…"
    private var qrReady = false
    private var failed = false
    private var qrKey: String? = null

    override fun init() {
        super.init()
        if (!started) {
            started = true
            startLogin()
        }
    }

    private fun startLogin() {
        CloudMusicAsync.run(
            job = {
                val login = MusicCommand.getLoginMusic163()
                val key = login.qrKey()
                login.getQRCodeTexture(key)
                key
            },
            onSuccess = { key ->
                qrKey = key
                qrReady = true
                status = "请使用网易云音乐 App 扫码登录"
                pollLogin(key)
            },
            onError = {
                failed = true
                status = "二维码获取失败，请检查网络连接"
            },
        )
    }

    private fun pollLogin(key: String) {
        CloudMusicAsync.run(
            job = { MusicCommand.getLoginMusic163().qrLogin(key) },
            onSuccess = { cookie ->
                MusicCommand.setCookie(cookie)
                mc.gui.setScreen(CloudMusicScreen())
            },
            onError = {
                failed = true
                status = "登录超时或二维码已过期"
            },
        )
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (click.button() == 0 && backButtonRect().contains(click.x().toFloat(), click.y().toFloat())) {
            mc.gui.setScreen(CloudMusicScreen())
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            mc.gui.setScreen(CloudMusicScreen())
            return true
        }
        return super.keyPressed(input)
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        with(context) {
            drawQuad(0f, 0f, width.toFloat(), height.toFloat(), CloudMusicGui.BACKGROUND)

            // Back button
            val back = backButtonRect()
            val backHovered = back.contains(mouseX.toFloat(), mouseY.toFloat())
            drawRoundedRect(
                back.x1, back.y1, back.x2, back.y2, 6f,
                fillColor = if (backHovered) CloudMusicGui.HOVER else CloudMusicGui.ACTIVE,
                outlineColor = CloudMusicGui.BORDER,
            )
            drawCloudMusicText(
                "返回", x = back.x1 + 12f, y = back.y1 + 11f,
                scale = CloudMusicGui.bodyScale, color = CloudMusicGui.TEXT_DIM,
            )

            drawCloudMusicText(
                "网易云音乐登录", x = width / 2f, y = height / 2f - 160f,
                scale = CloudMusicGui.titleScale, color = CloudMusicGui.TEXT,
                horizontalAnchor = HorizontalAnchor.CENTER,
            )

            // QR code
            val qrSize = 210f
            val qrX = width / 2f - qrSize / 2f
            val qrY = height / 2f - qrSize / 2f - 10f
            drawRoundedRect(qrX, qrY, qrX + qrSize, qrY + qrSize, 8f, CloudMusicGui.SIDEBAR, outlineColor = CloudMusicGui.BORDER)

            val qrTexture = if (qrReady) mc.textureManager.getTexture(MusicIconTexture.QR_CODE_ID) else null
            if (qrTexture != null) {
                drawTexQuad(qrTexture.textureSetup, qrX + 6f, qrY + 6f, qrX + qrSize - 6f, qrY + qrSize - 6f)
            } else {
                drawCloudMusicText(
                    "二维码加载中…", x = width / 2f, y = qrY + qrSize / 2f - 8f,
                    scale = CloudMusicGui.bodyScale, color = CloudMusicGui.TEXT_FAINT,
                    horizontalAnchor = HorizontalAnchor.CENTER,
                )
            }

            drawCloudMusicText(
                status, x = width / 2f, y = qrY + qrSize + 26f,
                scale = CloudMusicGui.bodyScale,
                color = if (failed) CloudMusicGui.ERROR else CloudMusicGui.TEXT_DIM,
                horizontalAnchor = HorizontalAnchor.CENTER,
            )

            drawCloudMusicText(
                "也可使用 .cloudmusic login 命令设置 Cookie",
                x = width / 2f, y = height - 40f,
                scale = CloudMusicGui.smallScale, color = CloudMusicGui.TEXT_FAINT,
                horizontalAnchor = HorizontalAnchor.CENTER,
            )
        }
    }

    private fun backButtonRect() = Quad(14f, 14f, 100f, 50f)

    private data class Quad(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
        fun contains(x: Float, y: Float): Boolean =
            x in x1..x2 && y in y1..y2
    }
}
