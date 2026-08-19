/*
 * This file is part of LiquidBounce.
 */
package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fengliu.cloudmusic.CloudMusicClient
import fengliu.cloudmusic.command.MusicCommand
import fengliu.cloudmusic.config.Configs
import fengliu.cloudmusic.music163.IMusic
import fengliu.cloudmusic.music163.data.Music
import fengliu.cloudmusic.music163.data.My
import fengliu.cloudmusic.music163.data.PlayList
import fi.dy.masa.malilib.config.IConfigBase
import fi.dy.masa.malilib.config.options.ConfigBoolean
import fi.dy.masa.malilib.config.options.ConfigColor
import fi.dy.masa.malilib.config.options.ConfigDouble
import fi.dy.masa.malilib.config.options.ConfigHotkey
import fi.dy.masa.malilib.config.options.ConfigInteger
import fi.dy.masa.malilib.config.options.ConfigOptionList
import fi.dy.masa.malilib.config.options.ConfigString
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.integration.interop.badRequest
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.Locale

private object RikkaMusicLogin {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "RikkaMusic QR Login").apply { isDaemon = true }
    }

    @Volatile private var status = "idle"
    @Volatile private var message = "未开始登录"
    @Volatile private var running: CompletableFuture<*>? = null

    fun start(): JsonObject = synchronized(this) {
        if (running?.isDone == false) return json()

        status = "loading"
        message = "正在获取二维码"
        running = CompletableFuture.runAsync({
            try {
                val login = MusicCommand.getLoginMusic163()
                val key = login.qrKey()
                login.getQRCodeTexture(key)
                status = "waiting"
                message = "请使用网易云音乐 App 扫码登录"

                MusicCommand.setCookie(login.qrLogin(key))
                status = "success"
                message = "登录成功，正在刷新音乐库"
            } catch (error: Throwable) {
                status = "error"
                message = error.message ?: "二维码登录失败，请重新尝试"
            }
        }, executor)
        return json()
    }

    fun json(): JsonObject = JsonObject().apply {
        addProperty("status", status)
        addProperty("message", message)
    }

    fun qrCodeFile(): File = CloudMusicClient.MC_PATH.resolve("cloud_music_qrcode.png").toFile()
}

private object RikkaMusicLocal {
    private val directory = AtomicReference<File?>(null)

    fun setDirectory(path: String?) {
        directory.set(path?.let(::File)?.takeIf { it.isDirectory })
    }

    fun path(): String = directory.get()?.absolutePath ?: ""

    fun songs(): List<fengliu.cloudmusic.music163.data.LocalMusic> = directory.get()
        ?.walkTopDown()
        ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in setOf("mp3", "wav", "ogg", "flac", "m4a") }
        ?.map { fengliu.cloudmusic.music163.data.LocalMusic(it) }
        ?.toList()
        ?: emptyList()
}

private fun musicJson(music: IMusic): JsonObject {
    val result = JsonObject()
    result.addProperty("id", music.id)
    result.addProperty("name", music.name)
    result.addProperty("cover", music.picUrl ?: "")
    result.addProperty("duration", music.duration)
    result.addProperty("artist", if (music is Music && music.artists != null) {
        runCatching { Music.getArtistsName(music.artists) }.getOrDefault("")
    } else "")
    result.addProperty("album", if (music is Music && music.album?.has("name") == true
        && !music.album.get("name").isJsonNull) {
        music.album.get("name").asString
    } else "")
    return result
}

private fun playlistJson(playlist: PlayList, includeSongs: Boolean): JsonObject {
    val result = JsonObject()
    result.addProperty("id", playlist.id)
    result.addProperty("name", playlist.name)
    result.addProperty("cover", playlist.cover)
    result.addProperty("count", playlist.count)
    if (includeSongs) {
        result.add("songs", JsonArray().apply { playlist.musics.forEach { add(musicJson(it)) } })
    }
    return result
}

private fun libraryJson(my: My): JsonObject {
    val liked = runCatching { my.likeMusicPlayList() }.getOrNull()
    val playlists = runCatching { my.playLists(0, 100) }.getOrDefault(emptyList())
    val result = JsonObject()
    result.addProperty("authenticated", true)
    result.addProperty("username", my.name)
    result.add("liked", liked?.let { playlistJson(it, true) })
    result.add("playlists", JsonArray().apply {
        playlists.drop(if (liked == null) 0 else 1).forEach { add(playlistJson(it, false)) }
    })
    return result
}

private fun hasMusicCookie(): Boolean = Configs.LOGIN.COOKIE.getStringValue().isNotBlank() ||
    MusicCommand.getMusic163().httpClient.cookies.isNotBlank()

private fun unavailableLibrary(error: String? = null, authenticated: Boolean = hasMusicCookie()): JsonObject = JsonObject().apply {
    addProperty("authenticated", authenticated)
    addProperty("username", if (authenticated) "已登录" else "未登录")
    add("liked", null)
    add("playlists", JsonArray())
    error?.let { addProperty("error", it) }
}

private suspend fun loadPlaylist(id: Long): PlayList = withContext(Dispatchers.IO) {
    MusicCommand.getMusic163().playlist(id)
}

private fun Route.getMusicLibrary() = get("/library") {
    if (!hasMusicCookie()) {
        call.respond(unavailableLibrary())
        return@get
    }

    val result = runCatching {
        withContext(Dispatchers.IO) { libraryJson(MusicCommand.getMy(false)) }
    }.getOrElse { error ->
        call.respond(unavailableLibrary(error.message ?: "无法读取网易云账号信息", authenticated = true))
        return@get
    }
    call.respond(result)
}

private fun configKey(config: IConfigBase): String = config.name.removePrefix("cloudmusic.config.")

private fun settingJson(config: IConfigBase): JsonObject? {
    val key = configKey(config)
    if (key == "login.cookie") return null

    return JsonObject().apply {
        addProperty("key", key)
        when (config) {
            is ConfigBoolean -> {
                addProperty("type", "boolean")
                addProperty("value", config.booleanValue)
                if (config is fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed) {
                    addProperty("hotkey", config.keybind.keysDisplayString)
                }
            }
            is ConfigInteger -> {
                addProperty("type", "integer")
                addProperty("value", config.integerValue)
                addProperty("min", config.minIntegerValue)
                addProperty("max", config.maxIntegerValue)
            }
            is ConfigDouble -> {
                addProperty("type", "double")
                addProperty("value", config.doubleValue)
                addProperty("min", config.minDoubleValue)
                addProperty("max", config.maxDoubleValue)
            }
            is ConfigColor -> {
                addProperty("type", "color")
                addProperty("value", "#%08X".format(config.integerValue))
            }
            is ConfigString -> {
                addProperty("type", "string")
                addProperty("value", config.stringValue)
            }
            is ConfigOptionList -> {
                addProperty("type", "option")
                addProperty("value", config.optionListValue.stringValue)
                add("options", JsonArray().apply {
                    val values = linkedSetOf<String>()
                    var option = config.optionListValue
                    do {
                        values += option.stringValue
                        option = option.cycle(true)
                    } while (option.stringValue !in values && values.size < 32)
                    values.forEach(::add)
                })
            }
            is ConfigHotkey -> {
                addProperty("type", "hotkey")
                addProperty("value", config.keybind.keysDisplayString)
            }
            else -> return null
        }
    }
}

private fun settingsFor(category: String): List<IConfigBase> = when (category) {
    "playback" -> Configs.PLAY.OPTIONS
    "interface" -> Configs.GUI.OPTIONS
    "command" -> Configs.COMMAND.OPTIONS
    "account" -> Configs.LOGIN.OPTIONS
    "network" -> Configs.HTTP.OPTIONS
    "hotkeys" -> Configs.HOTKEY.HOTKEY_LIST
    else -> Configs.ALL.OPTIONS
}

private fun settingsJson(category: String): JsonObject = JsonObject().apply {
    addProperty("category", category)
    add("settings", JsonArray().apply {
        settingsFor(category).forEach { settingJson(it)?.let(::add) }
    })
}

private data class MusicSettingRequest(val key: String, val value: String? = null)

private fun Route.getMusicSettings() = get("/settings") {
    val category = call.request.queryParameters["category"]?.lowercase(Locale.ROOT) ?: "account"
    call.respond(settingsJson(category))
}

private fun Route.updateMusicSettings() = post("/settings") {
    val request = call.receive<MusicSettingRequest>()
    val config = Configs.ALL.OPTIONS.firstOrNull { configKey(it) == request.key } ?: call.badRequest("Unknown music setting")
    if (request.key == "login.cookie") call.badRequest("Use QR login to update the account")

    val value = request.value ?: ""
    when (config) {
        is ConfigBoolean -> config.setBooleanValue(value.toBooleanStrictOrNull() ?: call.badRequest("Invalid boolean value"))
        is ConfigInteger -> config.setIntegerValue(value.toIntOrNull() ?: call.badRequest("Invalid integer value"))
        is ConfigDouble -> config.setDoubleValue(value.toDoubleOrNull() ?: call.badRequest("Invalid decimal value"))
        is ConfigColor -> config.setIntegerValue(value.removePrefix("#").toLongOrNull(16)?.toInt() ?: call.badRequest("Invalid color value"))
        is ConfigString -> config.setStringValue(value)
        is ConfigOptionList -> {
            var option = config.optionListValue
            if (option.stringValue == value) {
                option = option.cycle(true)
            } else {
                for (ignored in 0 until 32) {
                    if (option.stringValue == value) break
                    option = option.cycle(true)
                }
            }
            config.setOptionListValue(option)
        }
        is ConfigHotkey -> config.setValueFromString(value)
        else -> call.badRequest("Unsupported music setting")
    }

    if (request.key == "volume") {
        withContext(Dispatchers.Minecraft) { MusicCommand.getPlayer().volumeSet(Configs.PLAY.VOLUME.integerValue) }
    }
    Configs.INSTANCE.save()
    call.respond(settingsJson("all"))
}

private fun Route.startMusicLogin() = post("/login/start") { call.respond(RikkaMusicLogin.start()) }

private fun Route.getMusicLoginStatus() = get("/login/status") { call.respond(RikkaMusicLogin.json()) }

private fun Route.getMusicLoginQrCode() = get("/login/qr") {
    val file = RikkaMusicLogin.qrCodeFile()
    if (!file.isFile) call.badRequest("QR code is still being generated")
    call.respondFile(file)
}

private fun Route.getMusicPlaylist() = get("/playlist/{id}") {
    val id = call.parameters["id"]?.toLongOrNull() ?: call.badRequest("Invalid playlist id")
    call.respond(playlistJson(loadPlaylist(id), true))
}

private fun Route.getMusicCloud() = get("/cloud") {
    if (!hasMusicCookie()) {
        call.respond(JsonArray())
        return@get
    }
    val songs = runCatching {
        withContext(Dispatchers.IO) { MusicCommand.getMusic163().cloudMusic() }
    }.getOrElse {
        call.respond(JsonArray())
        return@get
    }
    call.respond(JsonArray().apply { songs.forEach { add(musicJson(it)) } })
}

private fun Route.getMusicLocal() = get("/local") {
    call.respond(JsonObject().apply {
        addProperty("path", RikkaMusicLocal.path())
        add("songs", JsonArray().apply { RikkaMusicLocal.songs().forEach { add(musicJson(it)) } })
    })
}

private data class LocalMountRequest(val path: String? = null)

private fun Route.mountMusicLocal() = post("/local/mount") {
    val request = call.receive<LocalMountRequest>()
    RikkaMusicLocal.setDirectory(request.path)
    call.respond(getLocalJson())
}

private fun getLocalJson(): JsonObject = JsonObject().apply {
    addProperty("path", RikkaMusicLocal.path())
    add("songs", JsonArray().apply { RikkaMusicLocal.songs().forEach { add(musicJson(it)) } })
}

private fun Route.searchMusic() = get("/search") {
    val query = call.request.queryParameters["q"]?.trim().orEmpty()
    if (query.isBlank()) call.badRequest("Missing search query")
    val type = call.request.queryParameters["type"]?.lowercase(Locale.ROOT)?.takeIf { it in setOf("song", "playlist", "artist") } ?: "song"
    val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val limit = 8
    val apiType = when (type) { "playlist" -> 1000; "artist" -> 100; else -> 1 }
    val result = withContext(Dispatchers.IO) { MusicCommand.getMusic163().search(query, apiType, page, limit) }
    val resultObject = result.getAsJsonObject("result") ?: JsonObject()
    val key = when (type) { "playlist" -> "playlists"; "artist" -> "artists"; else -> "songs" }
    val items = resultObject.getAsJsonArray(key) ?: JsonArray()
    val countKey = when (type) { "playlist" -> "playlistCount"; "artist" -> "artistCount"; else -> "songCount" }
    val total = resultObject.get(countKey)?.asInt ?: items.size()
    call.respond(JsonObject().apply {
        addProperty("type", type)
        addProperty("page", page)
        addProperty("pageCount", (total + limit - 1) / limit)
        addProperty("total", total)
        add("items", JsonArray().apply {
            items.forEach { element ->
                val item = element.asJsonObject
                if (type == "song") add(musicJson(Music(MusicCommand.getMusic163().httpClient, item, null)))
                else add(JsonObject().apply {
                    addProperty("id", item.get("id").asLong)
                    addProperty("name", item.get("name").asString)
                    addProperty("cover", item.stringOrEmpty("coverImgUrl").ifEmpty { item.stringOrEmpty("picUrl") })
                    addProperty("count", item.get("trackCount")?.asInt ?: 0)
                    addProperty("artist", item.getAsJsonObject("creator")?.stringOrEmpty("nickname") ?: "")
                })
            }
        })
    })
}

private fun JsonObject.stringOrEmpty(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString ?: ""

private fun searchSongs(query: String, page: Int): List<IMusic> {
    val result = MusicCommand.getMusic163().search(query, 1, page, 8)
    val songs = result.getAsJsonObject("result")?.getAsJsonArray("songs") ?: JsonArray()
    return songs.mapNotNull { item -> runCatching { Music(MusicCommand.getMusic163().httpClient, item.asJsonObject, null) }.getOrNull() }
}

private fun Route.getMusicState() = get("/state") {
    val state = withContext(Dispatchers.Minecraft) {
        val player = MusicCommand.getPlayer()
        val song = player.playingMusic
        JsonObject().apply {
            addProperty("playing", player.isPlaying)
            addProperty("progress", player.playingProgress)
            addProperty("volume", player.volumePercentage)
            addProperty("quality", Configs.PLAY.PLAY_QUALITY.optionListValue.stringValue)
            addProperty("theme", Configs.GUI.GUI_THEME.getStringValue())
            add("song", song?.let(::musicJson))
        }
    }
    call.respond(state)
}

private data class MusicControlRequest(
    val action: String,
    val value: Long? = null,
    val playlistId: Long? = null,
    val index: Int? = null,
    val query: String? = null,
    val page: Int? = null,
)

private fun Route.controlMusic() = post("/control") {
    val request = call.receive<MusicControlRequest>()
    when (request.action.lowercase(Locale.ROOT)) {
        "play-playlist" -> {
            val playlistId = request.playlistId ?: call.badRequest("Missing playlist id")
            val playlist = loadPlaylist(playlistId)
            val songs = playlist.musics
            val index = request.index ?: 0
            if (index !in songs.indices) call.badRequest("Invalid song index")
            withContext(Dispatchers.Minecraft) { MusicCommand.playMusicsFrom(songs, index) }
        }
        "play-search" -> {
            val query = request.query?.trim().orEmpty()
            if (query.isBlank()) call.badRequest("Missing search query")
            val songs = withContext(Dispatchers.IO) { searchSongs(query, request.page?.coerceAtLeast(1) ?: 1) }
            val index = request.index ?: 0
            if (index !in songs.indices) call.badRequest("Invalid song index")
            withContext(Dispatchers.Minecraft) { MusicCommand.playMusicsFrom(songs, index) }
        }
        "play-cloud" -> {
            val songs: List<IMusic> = withContext(Dispatchers.IO) { MusicCommand.getMusic163().cloudMusic().map { it } }
            val index = request.index ?: 0
            if (index !in songs.indices) call.badRequest("Invalid cloud song index")
            withContext(Dispatchers.Minecraft) { MusicCommand.playMusicsFrom(songs, index) }
        }
        "play-local" -> {
            val songs: List<IMusic> = RikkaMusicLocal.songs()
            val index = request.index ?: 0
            if (index !in songs.indices) call.badRequest("Invalid local song index")
            withContext(Dispatchers.Minecraft) { MusicCommand.playMusicsFrom(songs, index) }
        }
        "toggle" -> withContext(Dispatchers.Minecraft) { MusicCommand.getPlayer().switchPlay() }
        "next" -> withContext(Dispatchers.Minecraft) { MusicCommand.getPlayer().next() }
        "previous" -> withContext(Dispatchers.Minecraft) { MusicCommand.getPlayer().prev() }
        "seek" -> withContext(Dispatchers.Minecraft) { MusicCommand.getPlayer().seek(request.value ?: 0L) }
        "volume" -> withContext(Dispatchers.Minecraft) { MusicCommand.getPlayer().volumeSet((request.value ?: 0L).toInt()) }
        "quality" -> withContext(Dispatchers.Minecraft) {
            var option = Configs.PLAY.PLAY_QUALITY.optionListValue
            repeat((request.value ?: 1L).toInt().coerceAtLeast(1)) { option = option.cycle(true) }
            Configs.PLAY.PLAY_QUALITY.setOptionListValue(option)
            Configs.INSTANCE.save()
        }
        "theme" -> withContext(Dispatchers.Minecraft) {
            Configs.GUI.GUI_THEME.setStringValue(if (request.value == 1L) "Light" else "LiquidBounce")
            Configs.INSTANCE.save()
        }
        else -> call.badRequest("Unknown music action")
    }
    call.respond(io.ktor.http.HttpStatusCode.NoContent)
}

internal fun Route.rikkaMusicRoutes() = route("/music") {
    getMusicLibrary()
    getMusicPlaylist()
    getMusicCloud()
    getMusicLocal()
    mountMusicLocal()
    searchMusic()
    getMusicState()
    controlMusic()
    getMusicSettings()
    updateMusicSettings()
    startMusicLogin()
    getMusicLoginStatus()
    getMusicLoginQrCode()
}
