package net.ccbluex.liquidbounce.integration.task;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public final class InitializationChoiceScreen extends Screen {
    public enum Mode { DEFAULT, OPTIMIZED, SKIP }
    public static volatile CompletableFuture<Mode> pending;
    public static volatile String country;
    private InitializationProxy.Mode proxyMode = InitializationProxy.Mode.DIRECT;
    private EditBox proxyAddress;
    private String error = "";

    public InitializationChoiceScreen() { super(Component.literal("初始化网络选择 / Initialization")); }

    @Override protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 45;
        proxyAddress = new EditBox(font, x, y + 25, 200, 20, Component.literal("HTTP proxy host:port"));
        proxyAddress.setHint(Component.literal("HTTP proxy: host:port"));
        proxyAddress.setMaxLength(256);
        addRenderableWidget(Button.builder(proxyLabel(), button -> {
            proxyMode = switch (proxyMode) {
                case DIRECT -> InitializationProxy.Mode.SYSTEM;
                case SYSTEM -> InitializationProxy.Mode.CUSTOM;
                case CUSTOM -> InitializationProxy.Mode.DIRECT;
            };
            button.setMessage(proxyLabel());
            proxyAddress.setVisible(proxyMode == InitializationProxy.Mode.CUSTOM);
            error = "";
        }).bounds(x, y, 200, 20).build());
        proxyAddress.setVisible(false);
        addRenderableWidget(proxyAddress);
        addChoice("默认初始化", Mode.DEFAULT, y + 55);
        addChoice("优化初始化", Mode.OPTIMIZED, y + 80);
        addChoice("这次跳过", Mode.SKIP, y + 105);
    }

    private Component proxyLabel() {
        return Component.literal(switch (proxyMode) {
            case DIRECT -> "代理：关闭";
            case SYSTEM -> "代理：系统设置";
            case CUSTOM -> "代理：自定义 HTTP";
        });
    }

    private void addChoice(String label, Mode mode, int y) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
            if (mode != Mode.SKIP) {
                try {
                    InitializationProxy.configure(proxyMode, proxyAddress.getValue());
                } catch (IOException exception) {
                    error = exception.getMessage();
                    return;
                }
            }
            var selection = pending;
            if (selection != null && selection.complete(mode)) {
                InitializationNetwork.report("选择初始化方式: " + label);
                minecraft.gui.setScreen(null);
            }
        }).bounds(width / 2 - 100, y, 200, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int y = height / 2 - 45;
        graphics.centeredText(font, title, width / 2, y - 40, 0xFFFFCC66);
        graphics.centeredText(font, Component.literal("初始化下载代理（仅 HTTP，无账号密码）"), width / 2, y - 24, -1);
        if (!error.isEmpty()) graphics.centeredText(font, Component.literal(error), width / 2, y + 130, 0xFFFF5555);
        graphics.centeredText(font, Component.literal("优化：优先镜像；跳过：浏览器和 AI 不可用"), width / 2, y + 145, 0xFFAAAAAA);
    }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
