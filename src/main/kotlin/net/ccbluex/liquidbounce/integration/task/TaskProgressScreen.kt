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

import net.ccbluex.liquidbounce.integration.backend.BrowserBackendManager
import net.ccbluex.liquidbounce.integration.backend.isBrowserDisabled
import net.ccbluex.liquidbounce.integration.task.type.ResourceTask
import net.ccbluex.liquidbounce.integration.task.type.Task
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.collection.Pools
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.formatAsCapacity
import net.ccbluex.liquidbounce.utils.text.withFormat
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.components.Button
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import java.text.DecimalFormat

/**
 * Screen that displays TaskManager progress
 */
class TaskProgressScreen(
    title: String,
    private val taskManager: TaskManager
) : Screen(title.asPlainText()) {

    private val percentFormat = DecimalFormat("0.0")
    private var historyOffset = 0

    override fun init() {
        addRenderableWidget(Button.builder("较早记录".asPlainText()) { historyOffset += 5 }
            .bounds(16, height - 24, 90, 20).build())
        addRenderableWidget(Button.builder("最新记录".asPlainText()) { historyOffset = 0 }
            .bounds(width - 106, height - 24, 90, 20).build())
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractMenuBackground(context)
        super.extractRenderState(context, mouseX, mouseY, partialTick)

        val cx = width / 2.0

        val progressBarWidth = width / 1.5

        val poseStack = context.pose()

        // Progress
        val progress = taskManager.progress
        val textLines = getTaskLines(progress)

        // Draw text
        var yOffset = 12

        // Draw title
        context.text(
            font,
            title.string.withFormat(ChatFormatting.GOLD),
            (cx - font.width(title.string) / 2).toInt(),
            yOffset,
            -1,
            true
        )

        yOffset += font.lineHeight + 10

        // Draw task information
        for (line in textLines) {
            context.text(
                font,
                line,
                16,
                yOffset,
                -1,
                false
            )
            yOffset += font.lineHeight + 2
        }

        val progressBarHeight = 14

        // Draw progress bar
        poseStack.pushMatrix()
        poseStack.translate(cx.toFloat(), yOffset.toFloat() + 18.0f)
        poseStack.translate(progressBarWidth.toFloat() * -0.5f, progressBarHeight.toFloat() * -0.5f)

        // Bar border
        context.fill(
            0, 0,
            progressBarWidth.toInt(), progressBarHeight.toInt(),
            -1
        )

        // Bar background
        context.fill(
            2, 2,
            (progressBarWidth - 2).toInt(), (progressBarHeight - 2).toInt(),
            ARGB.color(255, 24, 26, 27)
        )

        context.fill(
            4, 4,
            ((progressBarWidth - 4) * progress).toInt(), (progressBarHeight - 4).toInt(),
            -1
        )
        poseStack.popMatrix()
    }

    private fun getTaskLines(progress: Float): List<FormattedCharSequence> {
        val activeTasks = taskManager.getActiveTasks()
        val speed = formatTotalSpeed(activeTasks)

        // Prepare text to display
        val textLines = mutableListOf<FormattedCharSequence>()
        textLines.add("Total: ${percentFormat.format(progress * 100)}%$speed".withFormat())
        textLines.add(FormattedCharSequence.EMPTY)

        activeTasks.take(3).forEach { task ->
            textLines.add(Pools.buildStringPooled {
                append(task.name)
                append(": ")
                append(percentFormat.format(task.progress * 100))
                append('%')
                append(formatTotalSpeed(listOf(task)))
            }.withFormat(ChatFormatting.GRAY))
            textLines.addAll(font.split("  ${task.status}".asPlainText(), (width - 32).coerceAtLeast(80)))
            task.subTasks.values.filterIsInstance<ResourceTask>().filter { !it.isCompleted }.take(2).forEach { resource ->
                val total = if (resource.contentLength > 0) resource.contentLength.formatAsCapacity() else "未知大小"
                textLines.addAll(font.split(
                    "下载 ${resource.bytesRead.formatAsCapacity()} / $total ${resource.speed.formatAsCapacity()}/s".asPlainText(),
                    (width - 32).coerceAtLeast(80)
                ))
            }
        }

        if (activeTasks.size > 3) {
            textLines.add("... and ${activeTasks.size - 3} more tasks".withFormat(ChatFormatting.GRAY))
        }
        textLines.addAll(font.split(InitializationNetwork.phase.asPlainText(), (width - 32).coerceAtLeast(80)))
        val count = InitializationNetwork.activeCount()
        textLines.add((if (count > 0) "请求正在进行：$count（连接 / 等待响应 / 接收）" else
            "当前没有进行中的请求；正在执行本地任务或等待选择").withFormat(ChatFormatting.AQUA))
        val remaining = ((height - 90) / (font.lineHeight + 2) - textLines.size).coerceAtLeast(0)
        val details = InitializationNetwork.history().flatMap { line ->
            font.split(line.asPlainText(), (width - 32).coerceAtLeast(80))
        }
        historyOffset = historyOffset.coerceAtMost((details.size - remaining).coerceAtLeast(0))
        textLines.addAll(details.dropLast(historyOffset).takeLast(remaining))
        return textLines
    }

    private fun formatTotalSpeed(tasks: List<Task>): String {
        val total = calculateTotalSpeed(tasks)

        return if (total > 0) {
            " (${total.formatAsCapacity()}/s)"
        } else {
            ""
        }
    }

    private fun calculateTotalSpeed(tasks: List<Task>): Long {
        return tasks.filter { task ->
            !task.isCompleted
        }.sumOf { task ->
            ((task as? ResourceTask)?.speed ?: 0L) + calculateTotalSpeed(task.subTasks.values.toList())
        }
    }

    override fun tick() {
        if (InitializationChoiceScreen.pending?.isDone == false) {
            mc.gui.setScreen(InitializationChoiceScreen())
            return
        }
        if (taskManager.isCompleted && (BrowserBackendManager.backend?.isInitialized == true || isBrowserDisabled)) {
            InitializationNetwork.finish()
            mc.gui.setScreen(TitleScreen())
        }
    }

    override fun shouldCloseOnEsc() = false

    override fun isPauseScreen() = false

}
