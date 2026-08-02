package dev.lambdaurora.lambdynlights.gui;

import dev.lambdaurora.spruceui.background.Background;
import dev.lambdaurora.spruceui.background.SimpleColorBackground;

/** Lightweight integrated replacement for the optional PrideLib decoration. */
public final class RandomPrideFlagBackground {
    private RandomPrideFlagBackground() {
    }

    public static Background random() {
        return new SimpleColorBackground(0xd0101010);
    }
}
