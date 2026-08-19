/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package fengliu.cloudmusic.gui

import fengliu.cloudmusic.command.MusicCommand
import fengliu.cloudmusic.config.Configs
import fengliu.cloudmusic.hud.CloudMusicHudComponent
import fengliu.cloudmusic.music163.IMusic
import fengliu.cloudmusic.music163.data.DjMusic
import fengliu.cloudmusic.music163.data.Music
import fengliu.cloudmusic.render.MusicIconTexture
import fi.dy.masa.malilib.config.IConfigBase
import fi.dy.masa.malilib.config.options.ConfigBoolean
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed
import fi.dy.masa.malilib.config.options.ConfigColor
import fi.dy.masa.malilib.config.options.ConfigDouble
import fi.dy.masa.malilib.config.options.ConfigHotkey
import fi.dy.masa.malilib.config.options.ConfigInteger
import fi.dy.masa.malilib.config.options.ConfigOptionList
import fi.dy.masa.malilib.config.options.ConfigString
import fi.dy.masa.malilib.hotkeys.IKeybind
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.drawTexQuad
import net.ccbluex.liquidbounce.render.drawTriangle
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.render.engine.font.HorizontalAnchor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.getBounds
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.render.textureSetup
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/**
 * Compact LiquidBounce-font settings page for the merged RikkaMusic window.
 *
 * Configuration keys remain the original malilib keys. Only this page's display
 * labels and controls are localized/styled.
 */
class CloudMusicSettingsScreen : Screen("RikkaMusic Settings".asPlainText()) {

    private companion object {
        const val DESIGN_WIDTH = 440f
        const val DESIGN_HEIGHT = 313f
        const val WIDTH_RATIO = 1320f / 2560f
        const val HEIGHT_RATIO = 940f / 1440f
        const val SIDEBAR_WIDTH = 84f
        const val HEADER_HEIGHT = 30f
        const val PLAYER_HEIGHT = 35f
        const val TAB_HEIGHT = 25f
        const val ROW_HEIGHT = 27f
    }

    private var windowLeft = 0f
    private var windowTop = 0f
    private var windowWidth = DESIGN_WIDTH
    private var windowHeight = DESIGN_HEIGHT

    private enum class SettingsTab(val label: String) {
        ALL("全部"),
        PLAY("播放"),
        GUI("界面"),
        COMMAND("命令"),
        LOGIN("登录"),
        HTTP("网络"),
        ENABLE("功能"),
        HOTKEY("按键"),
    }

    private data class EditingState(val config: IConfigBase, val buffer: String)

    private data class Quad(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
        fun contains(x: Float, y: Float): Boolean = x in x1..x2 && y in y1..y2
        fun width() = x2 - x1
        fun height() = y2 - y1
    }

    private var tab = SettingsTab.ALL
    private var scrollOffset = 0f
    private var editing: EditingState? = null
    private var capturingHotkey: IKeybind? = null
    private var draggingInteger: ConfigInteger? = null
    private var draggingDouble: ConfigDouble? = null

    private fun options(): List<IConfigBase> = when (tab) {
        SettingsTab.ALL -> Configs.ALL.OPTIONS
        SettingsTab.PLAY -> Configs.PLAY.OPTIONS
        SettingsTab.GUI -> Configs.GUI.OPTIONS
        SettingsTab.COMMAND -> Configs.COMMAND.OPTIONS
        SettingsTab.LOGIN -> Configs.LOGIN.OPTIONS
        SettingsTab.HTTP -> Configs.HTTP.OPTIONS
        SettingsTab.ENABLE -> Configs.ENABLE.HOTKEY_LIST.map { it as IConfigBase }
        SettingsTab.HOTKEY -> Configs.HOTKEY.HOTKEY_LIST.map { it as IConfigBase }
    }

    /**
     * Malilib stores names with the cloudmusic.config prefix (and hotkeys add hotkey).
     * Normalizing here keeps the visual mapping independent of that namespace.
     */
    private fun configKey(config: IConfigBase): String =
        config.name.removePrefix("cloudmusic.config.").removePrefix("hotkey.")

    private fun displayName(config: IConfigBase): String {
        val key = configKey(config)
        val explicit = mapOf(
            "gui.draggable.window" to "允许拖动窗口",
            "gui.window.x" to "窗口横坐标",
            "gui.window.y" to "窗口纵坐标",
            "gui.theme" to "界面主题",
            "volume" to "音量",
            "play.url" to "在线播放",
            "play.loop" to "循环播放",
            "play.auto.random" to "自动随机播放",
            "play.quality" to "播放音质",
            "dj.radio.play.asc" to "电台按时间升序播放",
            "play.not.game.music" to "暂停游戏背景音乐",
            "exit.game.stop.music" to "退出游戏时停止音乐",
            "stop.play.show.ui" to "停止播放时关闭界面",
            "cache.path" to "缓存路径",
            "cache.max.mb" to "最大缓存空间",
            "cache.delete.mb" to "缓存清理空间",
            "page.limit" to "每页歌曲数量",
            "music.info" to "显示播放信息",
            "music.info.x" to "播放信息横坐标",
            "music.info.y" to "播放信息纵坐标",
            "music.info.effect.offset" to "药水效果时调整信息位置",
            "music.info.effect.offset.x" to "信息横向偏移",
            "music.info.effect.offset.y" to "信息纵向偏移",
            "music.info.color" to "信息背景颜色",
            "music.progress.bar.color" to "进度条颜色",
            "music.player.progress.bar.color" to "已播放进度条颜色",
            "music.progress.font.color" to "进度文字颜色",
            "music.info.title.font.color" to "歌曲标题颜色",
            "music.info.font.color" to "歌曲副标题颜色",
            "lyric" to "显示歌词",
            "lyric.style" to "歌词显示样式",
            "lyric.color" to "歌词颜色",
            "lyric.scale" to "歌词缩放比例",
            "lyric.x" to "歌词横坐标",
            "lyric.y" to "歌词纵坐标",
            "click.run.command" to "点击聊天选项执行命令",
            "login.cookie" to "网易云登录 Cookie",
            "login.country.code" to "手机国家码",
            "login.qr.check.num" to "二维码轮查次数",
            "login.qr.check.time" to "二维码轮查间隔",
            "http.max.retry" to "请求重试次数",
            "http.time.out" to "请求超时时间",
            "http.proxy" to "使用 HTTP 代理",
            "http.proxy.ip" to "代理服务器地址",
            "http.proxy.port" to "代理服务器端口",
            "nearby.monster.decrease.volume.radius" to "附近生物距离",
            "nearby.monster.decrease.volume.value" to "附近生物音量降低",
            "enable.nearby.monster.decrease.volume" to "靠近生物时降低音量",
            "nearby.monster.is.survival" to "仅对生存模式生物生效",
            "open.config.gui" to "打开配置界面",
            "switch.play.music" to "暂停 / 继续播放",
            "play.music" to "继续播放",
            "next.music" to "下一首歌曲",
            "prev.music" to "上一首歌曲",
            "stop.music" to "暂停播放",
            "exit.play" to "退出播放",
            "play.volume.add" to "增加音量",
            "play.volume.down" to "降低音量",
            "delete.play.music" to "删除当前歌曲",
            "trash.add.play.music" to "将当前歌曲移入垃圾桶",
            "like.music" to "喜欢当前歌曲",
            "playlist.add.music" to "添加到歌单",
            "playlist.del.music" to "从歌单删除",
            "playlist.random" to "随机播放队列",
        )[key]
        if (explicit != null) return explicit

        val translated = Component.translatable(config.name).getString()
        if (translated != config.name && translated.isNotBlank()) {
            return translated.substringBefore('|').trim()
        }
        return "其他设置"
    }

    private fun optionDisplay(config: ConfigOptionList): String {
        val key = configKey(config)
        val value = config.getOptionListValue().getStringValue()
        return when (key) {
            "play.quality" -> when (value) {
                "standard" -> "标准"
                "higher" -> "较高"
                "exhigh" -> "极高"
                "lossless" -> "无损"
                "hires" -> "Hi-Res"
                else -> config.getOptionListValue().getDisplayName()
            }

            "lyric.style" -> when (value) {
                "default" -> "游戏内面板"
                "actionbar" -> "动作栏"
                "off" -> "关闭"
                else -> config.getOptionListValue().getDisplayName()
            }

            else -> config.getOptionListValue().getDisplayName()
        }
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private fun contentX() = SIDEBAR_WIDTH + 17f
    private fun contentRight() = DESIGN_WIDTH - 16f
    private fun contentWidth() = contentRight() - contentX()
    private fun tabBarY() = 47f
    private fun listTop() = tabBarY() + TAB_HEIGHT + 7f
    private fun playerTop() = DESIGN_HEIGHT - PLAYER_HEIGHT
    private fun listBottom() = playerTop() - 7f

    private fun maxScroll(): Float {
        val contentHeight = options().size * ROW_HEIGHT
        return (contentHeight - (listBottom() - listTop())).coerceAtLeast(0f)
    }

    private fun rowRect(index: Int): Quad {
        val y = listTop() + index * ROW_HEIGHT - scrollOffset
        return Quad(contentX(), y, contentRight(), y + ROW_HEIGHT - 1f)
    }

    private fun rowIndexAt(x: Float, y: Float): Int {
        if (x < contentX() || x > contentRight() || y < listTop() || y > listBottom()) return -1
        return options().indices.firstOrNull { rowRect(it).contains(x, y) } ?: -1
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mouseX = localX(click.x().toFloat())
        val mouseY = localY(click.y().toFloat())
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(click, doubled)
        }

        if (backButtonRect().contains(mouseX, mouseY)) {
            close()
            return true
        }

        if (themeSelectorRect().contains(mouseX, mouseY)) {
            val midpoint = (themeSelectorRect().x1 + themeSelectorRect().x2) / 2f
            setTheme(mouseX >= midpoint)
            return true
        }

        for (candidate in SettingsTab.entries) {
            if (tabRect(candidate).contains(mouseX, mouseY)) {
                tab = candidate
                scrollOffset = 0f
                editing = null
                capturingHotkey = null
                return true
            }
        }

        if (playerTop() <= mouseY) {
            return clickPlayer(mouseX, mouseY)
        }

        val index = rowIndexAt(mouseX, mouseY)
        if (index < 0) return true
        handleRowClick(options()[index], index, mouseX)
        return true
    }

    private fun setTheme(light: Boolean) {
        Configs.GUI.GUI_THEME.setStringValue(if (light) "Light" else "LiquidBounce")
        Configs.INSTANCE.save()
    }

    private fun handleRowClick(config: IConfigBase, index: Int, mouseX: Float) {
        when (config) {
            is ConfigBoolean -> {
                config.setBooleanValue(!config.getBooleanValue())
                if (configKey(config) == "music.info") {
                    CloudMusicHudComponent.enabled = config.getBooleanValue()
                }
            }

            is ConfigOptionList -> config.setOptionListValue(config.getOptionListValue().cycle(true))
            is ConfigColor -> editing = EditingState(config, String.format("#%08X", config.getIntegerValue()))
            is ConfigDouble -> {
                val slider = sliderRect(index)
                if (slider != null) {
                    draggingDouble = config
                    updateDouble(config, mouseX, slider)
                } else {
                    editing = EditingState(config, config.getDoubleValue().toString())
                }
            }

            is ConfigInteger -> {
                val slider = sliderRect(index)
                if (slider != null) {
                    draggingInteger = config
                    updateInteger(config, mouseX, slider)
                } else {
                    editing = EditingState(config, config.getIntegerValue().toString())
                }
            }

            is ConfigString -> editing = EditingState(config, config.getStringValue())
            is ConfigHotkey -> capturingHotkey = config.getKeybind()
        }
        applyIntegerSideEffects(config)
        Configs.INSTANCE.save()
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
        draggingInteger?.let { config ->
            sliderRect(options().indexOf(config))?.let {
                updateInteger(config, localX(click.x().toFloat()), it)
            }
            return true
        }
        draggingDouble?.let { config ->
            sliderRect(options().indexOf(config))?.let {
                updateDouble(config, localX(click.x().toFloat()), it)
            }
            return true
        }
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        draggingInteger = null
        draggingDouble = null
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        val localMouseY = localY(mouseY.toFloat())
        if (localMouseY in listTop()..listBottom()) {
            scrollOffset = (scrollOffset - verticalAmount.toFloat() * ROW_HEIGHT * .75f).coerceIn(0f, maxScroll())
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val capture = capturingHotkey
        if (capture != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                capturingHotkey = null
            } else {
                capture.clearKeys()
                capture.addKey(input.key())
                capturingHotkey = null
                Configs.INSTANCE.save()
            }
            return true
        }

        val edit = editing
        if (edit != null) {
            when (input.key()) {
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commitEdit()
                GLFW.GLFW_KEY_ESCAPE -> editing = null
                GLFW.GLFW_KEY_BACKSPACE -> editing = edit.copy(buffer = edit.buffer.dropLast(1))
            }
            return true
        }
        return super.keyPressed(input)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (editing != null) {
            editing = editing?.copy(buffer = editing!!.buffer + event.codepointAsString())
            return true
        }
        return super.charTyped(event)
    }

    private fun commitEdit() {
        val edit = editing ?: return
        when (val config = edit.config) {
            is ConfigString -> config.setStringValue(edit.buffer)
            is ConfigDouble -> edit.buffer.toDoubleOrNull()?.let(config::setDoubleValue)
            is ConfigColor -> edit.buffer.removePrefix("#").toLongOrNull(16)?.let { config.setIntegerValue(it.toInt()) }
            is ConfigInteger -> edit.buffer.toIntOrNull()?.let(config::setIntegerValue)
            else -> Unit
        }
        applyIntegerSideEffects(edit.config)
        Configs.INSTANCE.save()
        editing = null
    }

    private fun updateInteger(config: ConfigInteger, mouseX: Float, slider: Quad) {
        val min = config.getMinIntegerValue()
        val max = config.getMaxIntegerValue()
        val fraction = ((mouseX - slider.x1) / slider.width()).coerceIn(0f, 1f)
        config.setIntegerValue((min + (max - min) * fraction).roundToInt().coerceIn(min, max))
        applyIntegerSideEffects(config)
    }

    private fun updateDouble(config: ConfigDouble, mouseX: Float, slider: Quad) {
        val min = config.getMinDoubleValue()
        val max = config.getMaxDoubleValue()
        val fraction = ((mouseX - slider.x1) / slider.width()).coerceIn(0f, 1f)
        config.setDoubleValue(min + (max - min) * fraction)
    }

    private fun applyIntegerSideEffects(config: IConfigBase) {
        if (configKey(config) == "volume" && config is ConfigInteger) {
            MusicCommand.getPlayer().volumeSet(config.getIntegerValue())
        }
    }

    override fun removed() {
        Configs.INSTANCE.save()
        MusicCommand.getPlayer().volumeSet(Configs.PLAY.VOLUME.getIntegerValue())
        super.removed()
    }

    private fun close() {
        mc.gui.setScreen(CloudMusicScreen())
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        updateWindowBounds()
        with(context) {
            drawRoundedRect(
                windowLeft,
                windowTop,
                windowLeft + windowWidth,
                windowTop + windowHeight,
                8f,
                CloudMusicGui.BACKGROUND,
                outlineColor = CloudMusicGui.BORDER,
            )

            val localMouseX = localX(mouseX.toFloat())
            val localMouseY = localY(mouseY.toFloat())
            pose().pushMatrix()
            pose().translate(windowLeft, windowTop)
            pose().scale(windowWidth / DESIGN_WIDTH, windowHeight / DESIGN_HEIGHT)
            drawQuad(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, CloudMusicGui.BACKGROUND)
            drawHeader(localMouseX, localMouseY)

            scissorStack.withPush(getBounds(contentX(), listTop(), contentRight(), listBottom())) {
                for (index in options().indices) {
                    val rect = rowRect(index)
                    if (rect.y2 >= listTop() && rect.y1 <= listBottom()) {
                        drawRow(options()[index], index, rect, localMouseX, localMouseY)
                    }
                }
            }
            drawScrollBar()
            drawPlayer(localMouseX, localMouseY)
            pose().popMatrix()
        }
    }

    override fun extractTransparentBackground(graphics: GuiGraphicsExtractor) = Unit
    override fun isPauseScreen() = false

    private fun updateWindowBounds() {
        val scale = minOf(width * WIDTH_RATIO / DESIGN_WIDTH, height * HEIGHT_RATIO / DESIGN_HEIGHT)
        windowWidth = DESIGN_WIDTH * scale
        windowHeight = DESIGN_HEIGHT * scale
        windowLeft = (width - windowWidth) / 2f
        windowTop = (height - windowHeight) / 2f
    }

    private fun localX(x: Float) = ((x - windowLeft) / windowWidth * DESIGN_WIDTH).coerceIn(0f, DESIGN_WIDTH)
    private fun localY(y: Float) = ((y - windowTop) / windowHeight * DESIGN_HEIGHT).coerceIn(0f, DESIGN_HEIGHT)

    private fun GuiGraphicsExtractor.drawHeader(mouseX: Float, mouseY: Float) {
        // Keep settings inside the same application shell as the library.
        drawQuad(0f, 0f, SIDEBAR_WIDTH, playerTop(), CloudMusicGui.SIDEBAR)
        drawCloudMusicText("RikkaMusic", 14f, 11f, CloudMusicGui.headerScale, CloudMusicGui.TEXT, shadow = false)
        drawCloudMusicText("我的音乐", 14f, 47f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM, shadow = false)
        drawCloudMusicText("收藏的音乐", 28f, 67f, CloudMusicGui.smallScale, CloudMusicGui.TEXT, shadow = false)
        drawCloudMusicText("我的音乐云盘", 28f, 91f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM, shadow = false)
        drawCloudMusicText("我的歌单", 14f, 119f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM, shadow = false)
        drawQuad(0f, 139f, SIDEBAR_WIDTH, 140f, CloudMusicGui.BORDER)
        drawCloudMusicText("设置", contentX(), 34f, CloudMusicGui.headerScale, CloudMusicGui.TEXT, shadow = false)

        drawBackChevron(contentX() + 8f, 17f, if (backButtonRect().contains(mouseX, mouseY)) CloudMusicGui.ACCENT else CloudMusicGui.TEXT_DIM)
        drawCloudMusicText("搜索歌曲、歌手或专辑", contentX() + 25f, 10f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT, shadow = false)
        drawCloudMusicText("Sangatsu_P", 268f, 10f, CloudMusicGui.smallScale, CloudMusicGui.TEXT, shadow = false)
        drawCloudMusicText("主题", 330f, 10f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM, shadow = false)
        val theme = themeSelectorRect()
        val themeHover = theme.contains(mouseX, mouseY)
        drawRoundedRect(
            theme.x1, theme.y1, theme.x2, theme.y2, 6f,
            CloudMusicGui.ACTIVE,
            outlineColor = if (themeHover) CloudMusicGui.ACCENT else CloudMusicGui.BORDER,
        )
        val midpoint = (theme.x1 + theme.x2) / 2f
        val light = CloudMusicGui.isLightPalette
        drawRoundedRect(
            if (light) midpoint else theme.x1,
            theme.y1 + 2f,
            if (light) theme.x2 - 2f else midpoint - 1f,
            theme.y2 - 2f,
            4f,
            CloudMusicGui.ACCENT_SUBTLE,
        )
        drawCloudMusicText("LB", (theme.x1 + midpoint) / 2f, theme.y1 + 7f, CloudMusicGui.smallScale, if (!light) CloudMusicGui.ACCENT else CloudMusicGui.TEXT_DIM, shadow = false, horizontalAnchor = HorizontalAnchor.CENTER)
        drawCloudMusicText("浅色", (midpoint + theme.x2) / 2f, theme.y1 + 7f, CloudMusicGui.smallScale, if (light) CloudMusicGui.ACCENT else CloudMusicGui.TEXT_DIM, shadow = false, horizontalAnchor = HorizontalAnchor.CENTER)

        drawTabs(mouseX, mouseY)
    }

    private fun GuiGraphicsExtractor.drawTabs(mouseX: Float, mouseY: Float) {
        var x = contentX()
        for (candidate in SettingsTab.entries) {
            val width = CloudMusicGui.textWidth(candidate.label, CloudMusicGui.smallScale) + 14f
            val rect = Quad(x, tabBarY(), x + width, tabBarY() + TAB_HEIGHT)
            val selected = candidate == tab
            val hovered = rect.contains(mouseX, mouseY)
            drawCloudMusicText(
                candidate.label,
                rect.x1 + width / 2f,
                rect.y1 + 7f,
                CloudMusicGui.smallScale,
                if (selected) CloudMusicGui.ACCENT else if (hovered) CloudMusicGui.TEXT else CloudMusicGui.TEXT_DIM,
                shadow = false,
                horizontalAnchor = HorizontalAnchor.CENTER,
            )
            if (selected) drawQuad(rect.x1 + 4f, rect.y2 - 2f, rect.x2 - 4f, rect.y2, CloudMusicGui.ACCENT)
            x += width + 3f
        }
    }

    private fun GuiGraphicsExtractor.drawRow(
        config: IConfigBase,
        index: Int,
        rect: Quad,
        mouseX: Float,
        mouseY: Float,
    ) {
        val hovered = rect.contains(mouseX, mouseY)
        if (hovered) {
            drawRoundedRect(rect.x1, rect.y1, rect.x2, rect.y2, 4f, CloudMusicGui.HOVER)
        }
        drawQuad(rect.x1 + 3f, rect.y2, rect.x2 - 3f, rect.y2 + .5f, CloudMusicGui.BORDER)

        val label = CloudMusicGui.truncate(displayName(config), contentWidth() * .46f, CloudMusicGui.bodyScale)
        drawCloudMusicText(label, rect.x1 + 7f, rect.y1 + 8f, CloudMusicGui.bodyScale, CloudMusicGui.TEXT, shadow = false)

        when (config) {
            is ConfigBoolean -> drawBooleanControl(config, rect)
            is ConfigOptionList -> drawOptionControl(config, rect)
            is ConfigColor -> drawColorControl(config, rect)
            is ConfigDouble -> drawDoubleControl(config, index, rect)
            is ConfigInteger -> drawIntegerControl(config, index, rect)
            is ConfigString -> drawStringControl(config, rect)
            is ConfigHotkey -> drawHotkeyControl(config, rect)
        }
    }

    private fun GuiGraphicsExtractor.drawBooleanControl(config: ConfigBoolean, rect: Quad) {
        val size = 14f
        val x = contentRight() - 23f
        val y = rect.y1 + (rect.height() - size) / 2f
        val enabled = config.getBooleanValue()
        drawRoundedRect(
            x, y, x + size, y + size, 3f,
            if (enabled) CloudMusicGui.ACCENT else CloudMusicGui.ACTIVE,
            outlineColor = if (enabled) CloudMusicGui.ACCENT else CloudMusicGui.BORDER,
        )
        if (enabled) {
            drawCloudMusicText("✓", x + size / 2f, y + 1.5f, CloudMusicGui.smallScale, Color4b.WHITE, shadow = false, horizontalAnchor = HorizontalAnchor.CENTER)
        }
        if (config is ConfigBooleanHotkeyed) {
            val key = config.getKeybind().getKeysDisplayString()
            if (key.isNotEmpty()) {
                drawCloudMusicText("快捷键 $key", contentRight() - 34f, rect.y1 + 9f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT, shadow = false, horizontalAnchor = HorizontalAnchor.END)
            }
        }
    }

    private fun GuiGraphicsExtractor.drawOptionControl(config: ConfigOptionList, rect: Quad) {
        val box = Quad(contentRight() - 142f, rect.y1 + 5f, contentRight() - 6f, rect.y2 - 5f)
        drawRoundedRect(box.x1, box.y1, box.x2, box.y2, 5f, CloudMusicGui.ACTIVE, outlineColor = CloudMusicGui.BORDER)
        drawCloudMusicText(CloudMusicGui.truncate(optionDisplay(config), 112f, CloudMusicGui.smallScale), box.x1 + 9f, box.y1 + 4f, CloudMusicGui.smallScale, CloudMusicGui.ACCENT, shadow = false)
        drawCloudMusicText("⌄", box.x2 - 9f, box.y1 + 3f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM, shadow = false, horizontalAnchor = HorizontalAnchor.CENTER)
    }

    private fun GuiGraphicsExtractor.drawColorControl(config: ConfigColor, rect: Quad) {
        val swatch = Quad(contentRight() - 130f, rect.y1 + 7f, contentRight() - 112f, rect.y1 + 25f)
        drawRoundedRect(swatch.x1, swatch.y1, swatch.x2, swatch.y2, 4f, Color4b(config.getIntegerValue()), outlineColor = CloudMusicGui.BORDER)
        val text = editing?.takeIf { it.config === config }?.buffer ?: String.format("#%08X", config.getIntegerValue())
        drawCloudMusicText(text, contentRight() - 7f, rect.y1 + 8f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM, shadow = false, horizontalAnchor = HorizontalAnchor.END)
    }

    private fun GuiGraphicsExtractor.drawIntegerControl(config: ConfigInteger, index: Int, rect: Quad) {
        val slider = sliderRect(index)
        if (slider == null) {
            drawValueField(config.getIntegerValue().toString(), rect)
            return
        }
        drawSlider(slider, config.getIntegerValue().toFloat(), config.getMinIntegerValue().toFloat(), config.getMaxIntegerValue().toFloat(), rect)
    }

    private fun GuiGraphicsExtractor.drawDoubleControl(config: ConfigDouble, index: Int, rect: Quad) {
        val slider = sliderRect(index)
        if (slider == null) {
            drawValueField(config.getDoubleValue().toString(), rect)
            return
        }
        drawSlider(slider, config.getDoubleValue().toFloat(), config.getMinDoubleValue().toFloat(), config.getMaxDoubleValue().toFloat(), rect)
    }

    private fun GuiGraphicsExtractor.drawSlider(slider: Quad, value: Float, min: Float, max: Float, rect: Quad) {
        val fraction = ((value - min) / (max - min).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
        drawRoundedRect(slider.x1, slider.y1, slider.x2, slider.y2, 2f, CloudMusicGui.PROGRESS_BG)
        drawRoundedRect(slider.x1, slider.y1, slider.x1 + slider.width() * fraction, slider.y2, 2f, CloudMusicGui.ACCENT)
        drawRoundedRect(slider.x1 + slider.width() * fraction - 3f, slider.y1 - 2f, slider.x1 + slider.width() * fraction + 3f, slider.y2 + 2f, 3f, CloudMusicGui.ACCENT)
        drawCloudMusicText(if (value == value.toInt().toFloat()) value.toInt().toString() else "%.2f".format(value), slider.x2 + 7f, rect.y1 + 8f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM, shadow = false)
    }

    private fun GuiGraphicsExtractor.drawValueField(value: String, rect: Quad) {
        val box = Quad(contentRight() - 138f, rect.y1 + 5f, contentRight() - 6f, rect.y2 - 5f)
        drawRoundedRect(box.x1, box.y1, box.x2, box.y2, 5f, CloudMusicGui.ACTIVE, outlineColor = CloudMusicGui.BORDER)
        drawCloudMusicText(value, box.x2 - 9f, box.y1 + 4f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM, shadow = false, horizontalAnchor = HorizontalAnchor.END)
    }

    private fun GuiGraphicsExtractor.drawStringControl(config: ConfigString, rect: Quad) {
        val editingThis = editing?.takeIf { it.config === config }
        val raw = editingThis?.buffer ?: config.getStringValue()
        val value = if (configKey(config) == "login.cookie" && editingThis == null && raw.length > 12) raw.take(5) + "••••" + raw.takeLast(3) else raw
        val box = Quad(contentRight() - 188f, rect.y1 + 5f, contentRight() - 6f, rect.y2 - 5f)
        drawRoundedRect(box.x1, box.y1, box.x2, box.y2, 5f, if (editingThis != null) CloudMusicGui.ACCENT_SUBTLE else CloudMusicGui.ACTIVE, outlineColor = if (editingThis != null) CloudMusicGui.ACCENT else CloudMusicGui.BORDER)
        drawCloudMusicText(CloudMusicGui.truncate(value.ifBlank { "点击输入" }, 160f, CloudMusicGui.smallScale), box.x2 - 9f, box.y1 + 4f, CloudMusicGui.smallScale, if (editingThis != null) CloudMusicGui.TEXT else CloudMusicGui.TEXT_DIM, shadow = false, horizontalAnchor = HorizontalAnchor.END)
    }

    private fun GuiGraphicsExtractor.drawHotkeyControl(config: ConfigHotkey, rect: Quad) {
        val capturing = capturingHotkey === config.getKeybind()
        val value = when {
            capturing -> "按下按键（Esc 取消）"
            else -> config.getKeybind().getKeysDisplayString().ifEmpty { "未设置" }
        }
        val box = Quad(contentRight() - 188f, rect.y1 + 5f, contentRight() - 6f, rect.y2 - 5f)
        drawRoundedRect(box.x1, box.y1, box.x2, box.y2, 5f, if (capturing) CloudMusicGui.ACCENT_SUBTLE else CloudMusicGui.ACTIVE, outlineColor = if (capturing) CloudMusicGui.ACCENT else CloudMusicGui.BORDER)
        drawCloudMusicText(CloudMusicGui.truncate(value, 164f, CloudMusicGui.smallScale), box.x2 - 9f, box.y1 + 4f, CloudMusicGui.smallScale, if (capturing) CloudMusicGui.ACCENT else CloudMusicGui.TEXT_DIM, shadow = false, horizontalAnchor = HorizontalAnchor.END)
    }

    private fun GuiGraphicsExtractor.drawScrollBar() {
        val max = maxScroll()
        if (max <= 0f) return
        val trackTop = listTop()
        val trackBottom = listBottom()
        val trackHeight = trackBottom - trackTop
        val thumbHeight = (trackHeight * trackHeight / (options().size * ROW_HEIGHT)).coerceIn(18f, trackHeight)
        val thumbTop = trackTop + (trackHeight - thumbHeight) * (scrollOffset / max)
        drawRoundedRect(contentRight() - 3f, trackTop, contentRight() + 1f, trackBottom, 2f, CloudMusicGui.PROGRESS_BG)
        drawRoundedRect(contentRight() - 3f, thumbTop, contentRight() + 1f, thumbTop + thumbHeight, 2f, CloudMusicGui.ACCENT)
    }

    // ------------------------------------------------------------------
    // Persistent player bar
    // ------------------------------------------------------------------

    private fun GuiGraphicsExtractor.drawPlayer(mouseX: Float, mouseY: Float) {
        val top = playerTop()
        drawQuad(0f, top, DESIGN_WIDTH, DESIGN_HEIGHT, CloudMusicGui.PLAYER_BG)
        drawQuad(0f, top, DESIGN_WIDTH, top + 1f, CloudMusicGui.BORDER)

        val player = MusicCommand.getPlayer()
        val music = player.getPlayingMusic()
        if (music == null) {
            drawCloudMusicText("暂无播放", 14f, top + 15f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM, shadow = false)
        } else {
            drawPlayerCover(top)
            drawCloudMusicText(CloudMusicGui.truncate(music.getName(), 65f, CloudMusicGui.smallScale), 44f, top + 6f, CloudMusicGui.smallScale, CloudMusicGui.TEXT, shadow = false)
            drawCloudMusicText(CloudMusicGui.truncate(playerArtist(music), 65f, CloudMusicGui.smallScale), 44f, top + 20f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_DIM, shadow = false)
        }

        val previous = previousButtonRect()
        val play = playButtonRect()
        val next = nextButtonRect()
        val hoveredPrevious = previous.contains(mouseX, mouseY)
        val hoveredPlay = play.contains(mouseX, mouseY)
        val hoveredNext = next.contains(mouseX, mouseY)
        val iconColor = CloudMusicGui.TEXT_DIM
        drawPreviousIcon(previous, if (hoveredPrevious) CloudMusicGui.ACCENT else iconColor)
        drawPlayIcon(play, player.isPlaying(), if (hoveredPlay) CloudMusicGui.ACCENT else CloudMusicGui.TEXT)
        drawNextIcon(next, if (hoveredNext) CloudMusicGui.ACCENT else iconColor)

        val progress = progressRect()
        val musicDuration = music?.getDurationSecond()?.coerceAtLeast(1) ?: 1
        val fraction = if (music == null) 0f else (player.getPlayingProgress().toFloat() / (musicDuration * 1000f)).coerceIn(0f, 1f)
        drawQuad(progress.x1, progress.y1, progress.x2, progress.y2, CloudMusicGui.PROGRESS_BG)
        drawQuad(progress.x1, progress.y1, progress.x1 + progress.width() * fraction, progress.y2, CloudMusicGui.ACCENT)
        if (progress.contains(mouseX, mouseY)) drawRoundedRect(progress.x1 + progress.width() * fraction - 2f, progress.y1 - 2f, progress.x1 + progress.width() * fraction + 2f, progress.y2 + 2f, 3f, CloudMusicGui.ACCENT)

        val volume = volumeRect()
        val volumeFraction = (player.getVolumePercentage() / 100f).coerceIn(0f, 1f)
        drawCloudMusicText("音量", volume.x1 - 8f, top + 17f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT, shadow = false, horizontalAnchor = HorizontalAnchor.END)
        drawRoundedRect(volume.x1, volume.y1, volume.x2, volume.y2, 2f, CloudMusicGui.PROGRESS_BG)
        drawRoundedRect(volume.x1, volume.y1, volume.x1 + volume.width() * volumeFraction, volume.y2, 2f, CloudMusicGui.ACCENT)
        drawCloudMusicText("${(volumeFraction * 100f).roundToInt()}%", volume.x2 + 6f, top + 17f, CloudMusicGui.smallScale, CloudMusicGui.TEXT_FAINT, shadow = false)
    }

    private fun GuiGraphicsExtractor.drawPlayerCover(top: Float) {
        val rect = Quad(12f, top + 5f, 37f, top + 30f)
        drawRoundedRect(rect.x1, rect.y1, rect.x2, rect.y2, 4f, CloudMusicGui.ACTIVE, outlineColor = CloudMusicGui.BORDER)
        if (MusicIconTexture.canUseIcon()) {
            mc.textureManager.getTexture(MusicIconTexture.MUSIC_ICON_ID)?.let { texture ->
                drawTexQuad(texture.textureSetup, rect.x1, rect.y1, rect.x2, rect.y2)
            }
        }
    }

    private fun playerArtist(music: IMusic): String = when (music) {
        is Music -> runCatching { Music.getArtistsName(music.artists) }.getOrDefault("网易云音乐")
        is DjMusic -> music.dj.get("nickname")?.getAsString() ?: "播客"
        else -> "网易云音乐"
    }

    private fun GuiGraphicsExtractor.drawPreviousIcon(rect: Quad, color: Color4b) {
        drawQuad(rect.x1 + 6f, rect.y1 + 7f, rect.x1 + 8f, rect.y2 - 7f, color)
        drawTriangle(rect.x1 + 17f, rect.y1 + 6f, rect.x1 + 17f, rect.y2 - 6f, rect.x1 + 8f, (rect.y1 + rect.y2) / 2f, color)
    }

    private fun GuiGraphicsExtractor.drawBackChevron(x: Float, y: Float, color: Color4b) {
        drawQuad(x - 5f, y - 1f, x + 5f, y + 1f, color)
        drawTriangle(x - 6f, y, x, y - 5f, x, y + 5f, color)
    }

    private fun GuiGraphicsExtractor.drawNextIcon(rect: Quad, color: Color4b) {
        drawTriangle(rect.x1 + 6f, rect.y1 + 6f, rect.x1 + 6f, rect.y2 - 6f, rect.x1 + 15f, (rect.y1 + rect.y2) / 2f, color)
        drawQuad(rect.x1 + 17f, rect.y1 + 7f, rect.x1 + 19f, rect.y2 - 7f, color)
    }

    private fun GuiGraphicsExtractor.drawPlayIcon(rect: Quad, playing: Boolean, color: Color4b) {
        drawRoundedRect(rect.x1, rect.y1, rect.x2, rect.y2, 12f, if (playing) CloudMusicGui.ACCENT else CloudMusicGui.ACTIVE, outlineColor = if (playing) CloudMusicGui.ACCENT else color)
        if (playing) {
            drawQuad(rect.x1 + 9f, rect.y1 + 7f, rect.x1 + 12f, rect.y2 - 7f, Color4b.WHITE)
            drawQuad(rect.x1 + 15f, rect.y1 + 7f, rect.x1 + 18f, rect.y2 - 7f, Color4b.WHITE)
        } else {
            drawTriangle(rect.x1 + 10f, rect.y1 + 7f, rect.x1 + 10f, rect.y2 - 7f, rect.x2 - 8f, (rect.y1 + rect.y2) / 2f, Color4b.WHITE)
        }
    }

    private fun clickPlayer(x: Float, y: Float): Boolean {
        val player = MusicCommand.getPlayer()
        when {
            previousButtonRect().contains(x, y) -> player.prev()
            playButtonRect().contains(x, y) -> player.switchPlay()
            nextButtonRect().contains(x, y) -> player.next()
            progressRect().contains(x, y) -> {
                val music = player.getPlayingMusic() ?: return true
                val fraction = ((x - progressRect().x1) / progressRect().width()).coerceIn(0f, 1f)
                player.seek((music.getDurationSecond() * 1000L * fraction).toLong())
            }

            volumeRect().contains(x, y) -> {
                val fraction = ((x - volumeRect().x1) / volumeRect().width()).coerceIn(0f, 1f)
                player.volumeSet((fraction * 100f).roundToInt())
            }
        }
        return true
    }

    // ------------------------------------------------------------------
    // Rect helpers
    // ------------------------------------------------------------------

    private fun backButtonRect() = Quad(contentX(), 7f, contentX() + 20f, 27f)
    private fun themeSelectorRect() = Quad(350f, 6f, 422f, 27f)

    private fun tabRect(tab: SettingsTab): Quad {
        var x = contentX()
        for (candidate in SettingsTab.entries) {
            val width = CloudMusicGui.textWidth(candidate.label, CloudMusicGui.smallScale) + 14f
            if (candidate == tab) return Quad(x, tabBarY(), x + width, tabBarY() + TAB_HEIGHT)
            x += width + 3f
        }
        return Quad(0f, 0f, 0f, 0f)
    }

    private fun sliderKey(config: IConfigBase): Boolean {
        return when (configKey(config)) {
            "volume", "page.limit", "cache.max.mb", "cache.delete.mb",
            "login.qr.check.num", "login.qr.check.time", "http.max.retry", "http.time.out" -> true
            else -> false
        }
    }

    private fun sliderRect(index: Int): Quad? {
        val config = options().getOrNull(index) ?: return null
        if (!sliderKey(config)) return null
        val y = rowRect(index).y1 + (ROW_HEIGHT - 4f) / 2f
        return Quad(contentRight() - 154f, y, contentRight() - 39f, y + 4f)
    }

    private fun previousButtonRect() = Quad(186f, playerTop() + 7f, 210f, playerTop() + 30f)
    private fun playButtonRect() = Quad(216f, playerTop() + 4f, 244f, playerTop() + 32f)
    private fun nextButtonRect() = Quad(250f, playerTop() + 7f, 274f, playerTop() + 30f)
    private fun progressRect() = Quad(0f, playerTop(), DESIGN_WIDTH, 3f)
    private fun volumeRect() = Quad(356f, playerTop() + 18f, 407f, playerTop() + 21f)
}
