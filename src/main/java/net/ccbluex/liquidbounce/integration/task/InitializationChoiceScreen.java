package net.ccbluex.liquidbounce.integration.task;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public final class InitializationChoiceScreen extends Screen {
    public enum Mode { DEFAULT, OPTIMIZED, SKIP }
    public static volatile CompletableFuture<Mode> pending;
    public static volatile String country;

    public InitializationChoiceScreen() { super(Component.literal("初始化网络选择 / Initialization")); }

    @Override protected void init() {
        int y = height / 2;
        addChoice("默认初始化", Mode.DEFAULT, y);
        addChoice("优化初始化", Mode.OPTIMIZED, y + 25);
        addChoice("这次跳过", Mode.SKIP, y + 50);
    }

    private void addChoice(String label, Mode mode, int y) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
            var selection = pending;
            if (selection != null && selection.complete(mode)) {
                InitializationNetwork.report("选择初始化方式: " + label);
                minecraft.gui.setScreen(null);
            }
        }).bounds(width / 2 - 100, y, 200, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int y = height / 2;
        graphics.centeredText(font, title, width / 2, y - 65, 0xFFFFCC66);
        String message = country == null ? "无法确认位置，您可以选择初始化方式"
            : "检测到您处于的位置（" + country + "）可能存在联网问题";
        graphics.centeredText(font, Component.literal(message), width / 2, y - 45, -1);
        graphics.centeredText(font, Component.literal("优化：优先镜像，失败回退官方源"), width / 2, y - 29, 0xFFAAAAAA);
        graphics.centeredText(font, Component.literal("跳过：本次不初始化 AI、浏览器和市场更新"), width / 2, y + 80, 0xFFAAAAAA);
        graphics.centeredText(font, Component.literal("部分界面与 AI 功能不可用；下次启动重新选择"), width / 2, y + 93, 0xFFAAAAAA);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
