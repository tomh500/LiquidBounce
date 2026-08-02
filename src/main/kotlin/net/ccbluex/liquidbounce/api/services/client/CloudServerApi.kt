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
import net.ccbluex.liquidbounce.api.core.parse
import net.ccbluex.liquidbounce.api.models.client.AutoSettings

/**
 * Static configuration source hosted by the CloudServer GitHub Pages site.
 * Its list and document format intentionally matches LiquidBounce's API.
 */
object CloudServerApi {

    const val BASE_URL = "https://luotiany1.top/LiquidBounce"

    suspend fun requestSettingsList(): Array<AutoSettings> =
        HttpClient.request("$BASE_URL/config/list", HttpMethod.GET).parse()

    suspend fun requestSettingsScript(settingId: String): String =
        HttpClient.request("$BASE_URL/config/$settingId.json", HttpMethod.GET).parse()

}
