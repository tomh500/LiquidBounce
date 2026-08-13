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
package fengliu.cloudmusic.gui

import com.mojang.blaze3d.platform.NativeImage
import fengliu.cloudmusic.music163.IMusic
import fengliu.cloudmusic.util.HttpClient
import fengliu.cloudmusic.util.PNGConverter
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.render.asTexture
import net.minecraft.resources.Identifier
import java.util.LinkedHashMap

/**
 * Downloads and caches album covers for the CloudMusic GUI. The playing cover
 * is still handled by the original [fengliu.cloudmusic.render.MusicIconTexture].
 */
object CloudMusicCoverCache {

    private const val MAX_CACHE_SIZE = 160

    private val cache = object : LinkedHashMap<Long, Identifier>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Identifier>?): Boolean =
            size > MAX_CACHE_SIZE
    }

    private val loading = HashSet<Long>()

    fun get(id: Long): Identifier? = cache[id]

    fun load(music: IMusic) = load(music.getId(), music.getPicUrl())

    fun load(id: Long, url: String) {
        if (cache.containsKey(id) || !loading.add(id) || url.isEmpty()) {
            return
        }

        val thread = Thread {
            try {
                val stream = HttpClient.downloadStream(url + "?param=128y128")
                val image = NativeImage.read(PNGConverter.convertJPEGtoPNG(stream))
                mc.execute {
                    val texture = image.asTexture { "CloudMusic Cover $id" }
                    val textureId = Identifier.fromNamespaceAndPath("cloudmusic", "cover/$id")
                    mc.textureManager.register(textureId, texture)
                    cache[id] = textureId
                    loading.remove(id)
                }
            } catch (exception: Exception) {
                loading.remove(id)
            }
        }
        thread.isDaemon = true
        thread.name = "CloudMusic Cover Thread"
        thread.start()
    }

    fun releaseAll() {
        loading.clear()
        cache.clear()
    }

}
