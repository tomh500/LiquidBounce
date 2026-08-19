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

package net.ccbluex.liquidbounce.injection.mixins.minecraft.gui;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import net.ccbluex.liquidbounce.features.command.CommandManager;
import fengliu.cloudmusic.command.LbClientCommandSource;
import fengliu.cloudmusic.command.MusicCommand;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;
import java.util.List;

@Mixin(CommandSuggestions.class)
public abstract class MixinCommandSuggestions {
    @Shadow
    @Final
    private EditBox input;
    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow
    private ParseResults<SharedSuggestionProvider> currentParse;
    @Shadow
    public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Shadow @Nullable private CommandSuggestions.@Nullable SuggestionsList suggestions;
    @Shadow @Final private List<FormattedCharSequence> commandUsage;
    @Shadow private int commandUsagePosition;

    @Invoker("recomputeUsageBoxWidth")
    protected abstract void invokeRecomputeUsageBoxWidth();

    @Inject(method = "updateCommandInfo", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;canRead()Z", remap = false), cancellable = true)
    private void injectAutoCompletionB(CallbackInfo ci) {
        if (this.input.getValue().startsWith(CommandManager.GlobalSettings.INSTANCE.getPrefix())) {
            this.updateRikkaMusicUsage();
            this.pendingSuggestions = CommandManager.INSTANCE.autoComplete(this.input.getValue(), this.input.getCursorPosition());
            this.pendingSuggestions.thenRun(() -> {
                if (suggestions == null) {
                    this.showSuggestions(false);
                }
            });

            this.currentParse = null;

            ci.cancel();
        }
    }

    private void updateRikkaMusicUsage() {
        String value = this.input.getValue();
        String prefix = CommandManager.GlobalSettings.INSTANCE.getPrefix();
        String command = value.substring(prefix.length());
        String root;
        if (command.equalsIgnoreCase("rikkamusic") || command.regionMatches(true, 0, "rikkamusic ", 0, 11)) {
            root = "rikkamusic";
        } else if (command.equalsIgnoreCase("music") || command.regionMatches(true, 0, "music ", 0, 6)) {
            root = "music";
        } else {
            return;
        }

        String rawArgs = command.length() == root.length() ? "" : command.substring(root.length() + 1);
        this.commandUsage.clear();
        for (String usage : MusicCommand.usageHints(rawArgs, new LbClientCommandSource())) {
            this.commandUsage.add(FormattedCharSequence.forward(usage, CommandSuggestions.USAGE_FORMAT));
        }
        this.commandUsagePosition = 0;
        this.invokeRecomputeUsageBoxWidth();
    }

}
