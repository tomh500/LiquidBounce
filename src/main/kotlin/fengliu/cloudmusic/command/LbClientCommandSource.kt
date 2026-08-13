package fengliu.cloudmusic.command

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.markAsError
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.commands.SharedSuggestionProvider.ElementSuggestionType
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.level.Level
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

/**
 * Bridges the ported Brigadier command tree into the LiquidBounce chat, so the
 * original `/cloudmusic` commands are executed with the client's `.` prefix.
 */
class LbClientCommandSource : FabricClientCommandSource {

    override fun sendFeedback(message: Component) {
        chat(message)
    }

    override fun sendError(message: Component) {
        chat(markAsError(message.copy()))
    }

    override fun getClient(): Minecraft = Minecraft.getInstance()

    override fun getPlayer(): LocalPlayer = checkNotNull(Minecraft.getInstance().player) {
        "Command executed without a player"
    }

    override fun getLevel(): ClientLevel = checkNotNull(Minecraft.getInstance().level) {
        "Command executed without a world"
    }

    override fun attended(): Boolean = true

    override fun permissions(): PermissionSet = PermissionSet.ALL_PERMISSIONS

    override fun getOnlinePlayerNames(): Collection<String> =
        Minecraft.getInstance().connection?.onlinePlayers?.map { it.profile.name } ?: emptyList()

    override fun getSelectedEntities(): Collection<String> = emptyList()

    override fun getAllTeams(): Collection<String> = emptyList()

    override fun getAvailableSounds(): Stream<Identifier> =
        Minecraft.getInstance().soundManager.availableSounds.stream()

    override fun customSuggestion(context: CommandContext<*>): CompletableFuture<Suggestions> =
        Suggestions.empty()

    override fun levels(): Set<ResourceKey<Level>> = emptySet()

    override fun registryAccess(): RegistryAccess = RegistryAccess.EMPTY

    override fun enabledFeatures(): FeatureFlagSet = FeatureFlags.DEFAULT_FLAGS

    override fun suggestRegistryElements(
        registryRef: ResourceKey<out Registry<*>>,
        elementType: ElementSuggestionType,
        builder: SuggestionsBuilder,
        context: CommandContext<*>,
    ): CompletableFuture<Suggestions> = Suggestions.empty()
}
