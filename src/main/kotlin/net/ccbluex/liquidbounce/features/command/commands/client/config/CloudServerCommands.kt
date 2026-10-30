package net.ccbluex.liquidbounce.features.command.commands.client.config

import kotlinx.coroutines.CancellationException
import net.ccbluex.liquidbounce.api.services.client.CloudServerApi
import net.ccbluex.liquidbounce.features.command.brigadier.CmdI18n
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.regular

/** CloudServer is a separate source from the upstream marketplace. */
internal suspend fun CmdI18n.printCloudConfigs(query: String? = null) {
    val items = try {
        CloudServerApi.list()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        chat(regular("CloudServer unavailable: ${e.message}"), metadata = plain)
        return
    }
    val matches = items.filter { query == null || it.name.contains(query, ignoreCase = true) ||
        it.id.contains(query, ignoreCase = true) }
    if (matches.isNotEmpty()) {
        chat(regular("CloudServer:"), metadata = plain)
        matches.forEach { chat(regular("- ${it.name}: .config load cloud:${it.id}"), metadata = plain) }
    }
}
