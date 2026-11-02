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
package net.ccbluex.liquidbounce.integration.task

import kotlinx.coroutines.CompletableDeferred
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.lang.management.ManagementFactory
import java.net.Proxy

enum class InitializationMode { DEFAULT, OPTIMIZED, SKIP }

data class InitializationSelection(val mode: InitializationMode, val proxy: DownloadProxy.Choice)

/** Select the initialization mode and optional proxy before network requests start. */
class DownloadProxyScreen(private val selection: CompletableDeferred<InitializationSelection>) :
    Screen("初始化 / Initialization".asPlainText()) {
    private var mode = 0
    private lateinit var address: EditBox
    private lateinit var defaultChoiceButton: Button
    private var error = ""
    private val timeout = InitializationChoiceTimeout()

    override fun init() {
        val x = width / 2 - 110
        val y = height / 2 - 52
        address = EditBox(font, x, y + 24, 220, 20, Component.literal("Proxy host:port"))
        address.setHint(Component.literal("代理地址 host:port"))
        address.setResponder { timeout.interact() }
        address.isVisible = mode >= 2
        addRenderableWidget(Button.builder(modeLabel()) {
            timeout.interact()
            mode = (mode + 1) % 4
            it.message = modeLabel()
            address.isVisible = mode >= 2
            error = ""
        }.bounds(x, y, 220, 20).build())
        addRenderableWidget(address)
        defaultChoiceButton = addChoice(x, y + 54, "默认初始化", InitializationMode.DEFAULT)
        addChoice(x, y + 79, "优化初始化（优先镜像）", InitializationMode.OPTIMIZED)
        addChoice(x, y + 104, "本次跳过", InitializationMode.SKIP)
    }

    private fun addChoice(x: Int, y: Int, label: String, initialization: InitializationMode): Button {
        return addRenderableWidget(Button.builder(label.asPlainText()) {
            timeout.interact()
            val choice = runCatching {
                if (initialization == InitializationMode.SKIP) return@runCatching DownloadProxy.Choice.Direct
                when (mode) {
                    1 -> {
                        val jvmSystemProxy = ManagementFactory.getRuntimeMXBean().inputArguments.any {
                            it.equals("-Djava.net.useSystemProxies=true", ignoreCase = true)
                        }
                        if (jvmSystemProxy) {
                            DownloadProxy.systemDelegate = null
                        } else if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                            InitializationProxy.prepareSystemProxy()
                            DownloadProxy.systemDelegate = InitializationProxy.selector()
                        } else {
                            error("请在启动器启用系统代理参数并重启")
                        }
                        DownloadProxy.Choice.System
                    }
                    2, 3 -> DownloadProxy.Choice.Custom(
                        DownloadProxy.parseAddress(address.value),
                        if (mode == 3) Proxy.Type.SOCKS else Proxy.Type.HTTP,
                    )
                    else -> DownloadProxy.Choice.Direct
                }
            }.getOrElse {
                error = it.message ?: "Invalid proxy address."
                return@builder
            }
            if (selection.complete(InitializationSelection(initialization, choice))) mc.gui.setScreen(null)
        }.bounds(x, y, 220, 20).build())
    }

    private fun modeLabel() = when (mode) {
        1 -> "代理：系统"
        2 -> "代理：自定义 HTTP/HTTPS"
        3 -> "代理：自定义 SOCKS5"
        else -> "代理：关闭"
    }.asPlainText()

    override fun tick() {
        val remaining = timeout.secondsRemaining()
        defaultChoiceButton.message = (if (remaining < 0) "默认初始化" else "默认初始化（${remaining}s）").asPlainText()
        if (timeout.hasExpired() &&
            selection.complete(InitializationSelection(InitializationMode.DEFAULT, DownloadProxy.Choice.Direct))) {
            mc.gui.setScreen(null)
        }
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(context, mouseX, mouseY, partialTick)
        val y = height / 2 - 52
        context.centeredText(font, title, width / 2, y - 44, -1)
        context.centeredText(font, "可选代理；选择初始化方式后开始".asPlainText(), width / 2, y - 28, -1)
        context.centeredText(font, "跳过后浏览器和 AI 功能不可用".asPlainText(), width / 2, y + 130, -1)
        if (mode == 1 && !System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            context.centeredText(
                font, "Launcher: -Djava.net.useSystemProxies=true".asPlainText(), width / 2, y + 145, -1
            )
        }
        if (error.isNotEmpty()) context.centeredText(font, error.asPlainText(), width / 2, y + 160, 0xFFFF5555.toInt())
    }

    override fun shouldCloseOnEsc() = false
}
