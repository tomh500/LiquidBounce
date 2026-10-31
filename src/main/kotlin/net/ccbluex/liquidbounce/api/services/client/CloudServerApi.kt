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
package net.ccbluex.liquidbounce.api.services.client

import net.ccbluex.liquidbounce.api.core.HttpClient
import net.ccbluex.liquidbounce.api.core.HttpMethod
import net.ccbluex.liquidbounce.api.core.bodyAsJson
import com.google.gson.annotations.SerializedName

/**
 * Static configuration source hosted by the CloudServer GitHub Pages site.
 * Its list and document format intentionally matches LiquidBounce's API.
 */
object CloudServerApi {

    const val BASE_URL = "https://luotiany1.top/LiquidBounce"

    data class Entry(@SerializedName("setting_id") val id: String, val name: String)

    @Volatile
    var cachedEntries: List<Entry> = emptyList()
        private set

    suspend fun list(): List<Entry> =
        HttpClient.request("$BASE_URL/config/list", HttpMethod.GET).bodyAsJson<List<Entry>>()
            .also { cachedEntries = it }

    suspend fun requestSettingsScript(settingId: String): String =
        HttpClient.request("$BASE_URL/config/$settingId.json", HttpMethod.GET).use { it.body.string() }

}
