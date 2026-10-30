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
package net.ccbluex.liquidbounce

import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import kotlinx.coroutines.future.await
import kotlinx.coroutines.internal.isMissing
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.ccbluex.liquidbounce.api.core.ApiConfig
import net.ccbluex.liquidbounce.api.core.httpException
import net.ccbluex.liquidbounce.api.core.ioScope
import net.ccbluex.liquidbounce.api.models.auth.ClientAccount
import net.ccbluex.liquidbounce.api.services.auth.isInvalidGrant
import net.ccbluex.liquidbounce.api.services.client.ClientUpdate
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.Config
import net.ccbluex.liquidbounce.deeplearn.DeepLearningEngine
import net.ccbluex.liquidbounce.deeplearn.ModelManager
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.features.addon.AddonInstaller
import net.ccbluex.liquidbounce.features.addon.AddonManager
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.events.ClientStartEvent
import net.ccbluex.liquidbounce.event.events.ScreenEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.account.AccountManager
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.utils.pathing.PathingEngine
import net.ccbluex.liquidbounce.features.cosmetic.ClientAccountManager
import net.ccbluex.liquidbounce.features.cosmetic.CosmeticService
import net.ccbluex.liquidbounce.features.creativetab.tabs.HeadsCreativeModeTab
import net.ccbluex.liquidbounce.features.global.GlobalManager
import net.ccbluex.liquidbounce.features.marketplace.MarketplaceItems
import net.ccbluex.liquidbounce.features.marketplace.MarketplaceManager
import net.ccbluex.liquidbounce.features.marketplace.SubscribedItem
import net.ccbluex.liquidbounce.features.marketplace.autoconfig.ConfigTracker
import net.ccbluex.liquidbounce.features.marketplace.autoconfig.MarketplaceConfigs
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.misc.proxy.ProxyManager
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.spoofer.SpooferManager
import net.ccbluex.liquidbounce.integration.backend.BrowserBackendManager
import net.ccbluex.liquidbounce.integration.backend.BrowserSelectionScreen
import net.ccbluex.liquidbounce.integration.interop.ClientInteropServer
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.ActiveServerList
import net.ccbluex.liquidbounce.integration.screen.ScreenManager
import net.ccbluex.liquidbounce.integration.task.TaskManager
import net.ccbluex.liquidbounce.integration.task.TaskProgressScreen
import net.ccbluex.liquidbounce.integration.task.InitializationChoiceScreen
import net.ccbluex.liquidbounce.integration.task.InitializationNetwork
import net.ccbluex.liquidbounce.integration.task.DownloadProxy
import net.ccbluex.liquidbounce.integration.task.DownloadProxyScreen
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.lang.LanguageManager
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.HAS_AMD_VEGA_APU
import net.ccbluex.liquidbounce.render.atlas.ItemImageAtlas
import net.ccbluex.liquidbounce.render.engine.BlurEffectRenderer
import net.ccbluex.liquidbounce.render.gui.ItemStackListRenderer
import net.ccbluex.liquidbounce.render.gui.indicator.IndicatorRenderer
import net.ccbluex.liquidbounce.utils.aiming.PostRotationExecutor
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.ccbluex.liquidbounce.utils.client.GitInfo
import net.ccbluex.liquidbounce.utils.client.InteractionTracker
import net.ccbluex.liquidbounce.utils.client.ServerObserver
import net.ccbluex.liquidbounce.utils.client.clientIdentifier
import net.ccbluex.liquidbounce.utils.client.error.ErrorHandler
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.ccbluex.liquidbounce.utils.entity.RenderedEntities
import net.ccbluex.liquidbounce.utils.input.InputTracker
import net.ccbluex.liquidbounce.utils.inventory.EnderChestInventoryTracker
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.io.readText
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.ccbluex.liquidbounce.utils.network.LocalPlayerFallDamageTracker
import net.ccbluex.liquidbounce.utils.network.PositionPacketSeparator
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ReloadableResourceManager
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * LiquidBounce
 *
 * A free mixin-based injection hacked-client for Minecraft using FabricMC.
 *
 * @author kawaiinekololis (@team CCBlueX)
 */
object LiquidBounce : EventListener {

    /**
     * CLIENT INFORMATION
     *
     * WARNING: Please read the GNU General Public License
     */
    const val CLIENT_NAME = "LiquidBounce"
    const val CLIENT_AUTHOR = "CCBlueX"

    private object Client : Config("Client") {
        val version = text("Version", GitInfo.version())
            .immutable()
        val commit = text("Commit", GitInfo.get("git.commit.id.abbrev")?.let { "git-$it" } ?: "unknown")
            .immutable()
        val branch = text("Branch", GitInfo.branch())
            .immutable()

        init {
            ConfigSystem.root(this)

            version.onChange { previousVersion ->
                runCatching {
                    ConfigSystem.backup("automatic_${previousVersion}-${version.inner}")
                }.onFailure {
                    logger.error("Unable to create backup", it)
                }

                previousVersion
            }
        }
    }

    val clientVersion by Client.version
    val clientCommit by Client.commit
    val clientBranch by Client.branch

    /**
     * Defines if the client is in development mode.
     * This will enable update checking on commit time instead of semantic versioning.
     *
     * TODO: Replace this approach with full semantic versioning.
     */
    const val IN_DEVELOPMENT = true

    /**
     * Client logger to print out console messages
     */
    val logger get() = net.ccbluex.liquidbounce.utils.client.logger

    var taskManager: TaskManager? = null

    var isInitialized = false
        private set

    private val initializationStarted = AtomicBoolean()

    /**
     * Creates an [net.minecraft.resources.Identifier] starts with [CLIENT_NAME].
     *
     * Warning: Use [clientIdentifier] to prevent silent `<clinit>` invocation
     */
    @JvmStatic
    fun identifier(path: String): Identifier = clientIdentifier(path)

    /**
     * Gets client resource.
     *
     * @param path prefix `/resources/liquidbounce/`
     * @throws IllegalArgumentException if the resource is not found
     */
    @JvmStatic
    fun resource(path: String): InputStream =
        LiquidBounce::class.java.getResourceAsStream("/resources/liquidbounce/$path")
            ?: throw IllegalArgumentException("Resource $path not found")

    /**
     * Gets client resource as string.
     *
     * @param path prefix `/resources/liquidbounce/`
     * @throws IllegalArgumentException if the resource is not found
     */
    @JvmStatic
    fun resourceToString(path: String): String =
        resource(path).use { it.readText() }

    /**
     * Initializes the client, called when
     * we reached the last stage of the splash screen.
     *
     * The thread should be the main render thread.
     */
    @OptIn(InternalCoroutinesApi::class)
    private fun initializeClient(
        workerDispatcher: CoroutineDispatcher,
        renderThreadDispatcher: CoroutineDispatcher,
    ): CompletableFuture<Void?> = CoroutineScope(
        renderThreadDispatcher + CoroutineName("$CLIENT_NAME Initializer")
    ).future<Void?> {
        if (!initializationStarted.compareAndSet(false, true)) {
            return@future null
        }

        // Ensure we are on the render thread
        RenderSystem.assertOnRenderThread()
        check(!Dispatchers.Main.isMissing())

        // The loading overlay hides screens until the resource reload has completed.
        while (mc.gui.overlay() != null) delay(50)
        val manager = TaskManager(ioScope)
        taskManager = manager
        val startupTask = manager.createTask("Online initialization")
        val selection = CompletableDeferred<DownloadProxy.Choice>()
        DownloadProxy.pending = selection
        mc.gui.setScreen(DownloadProxyScreen(selection))
        DownloadProxy.choice = selection.await()
        DownloadProxy.pending = null
        DownloadProxy.onlineInitialization = true
        mc.gui.setScreen(TaskProgressScreen("Loading Required Libraries", manager))

        // Initialize managers and features
        Client
        initializeManagers(renderThreadDispatcher)
        initializeFeatures()
        initializeResources(workerDispatcher)
        prepareGuiStage(renderThreadDispatcher)
        manager.complete(startupTask.name)

        // Register shutdown hook in case [ClientShutdownEvent] is not called
        Runtime.getRuntime().addShutdownHook(Thread(::shutdownClient))

        // Check for AMD Vega iGPU
        if (HAS_AMD_VEGA_APU) {
            logger.info("AMD Vega iGPU detected, enabling different line smooth handling. " +
                "If you believe this is a mistake, please create an issue at " +
                "https://github.com/CCBlueX/LiquidBounce/issues.")
        }

        // Do backup before loading configs
        if (!ConfigSystem.isFirstLaunch && !Client.jsonFile.exists()) {
            runCatching {
                ConfigSystem.backup("automatic_${Client.version.inner}")
            }.onFailure {
                logger.error("Unable to create backup", it)
            }
        }

        // Load all configurations
        ConfigSystem.loadAll()
        AddonManager.notifyStarted()

        if (System.getProperty("liquidbounce.dev.autoLoginMicrosoft").toBoolean()) {
            AccountManager.loginPreferredDevelopmentAccount()
        }

        isInitialized = true
        logger.info("$CLIENT_NAME has been successfully initialized.")
        null
    }.exceptionally { throwable ->
        DownloadProxy.onlineInitialization = false
        ErrorHandler.fatal(throwable, additionalMessage = "$CLIENT_NAME initializer")
    }

    /**
     * Initializes managers for Event Listener registration.
     */
    private suspend fun initializeManagers(
        renderThreadDispatcher: CoroutineDispatcher,
    ) = withContext(renderThreadDispatcher) {
        // Config
        ConfigSystem

        // Utility
        RenderedEntities
        ChunkScanner
        InputTracker

        // Feature managers
        ModuleManager
        CommandManager
        ProxyManager
        AccountManager

        // Utility managers
        RotationManager
        BlinkManager
        LocalPlayerFallDamageTracker
        PositionPacketSeparator
        InteractionTracker
        CombatManager
        FriendManager
        InventoryManager
        EnderChestInventoryTracker
        ActiveServerList
        ConfigSystem.root(ClientAccountManager)
        ConfigSystem.root(SpooferManager)
        ConfigSystem.root(GlobalManager)
        ConfigSystem.root(MarketplaceManager)
        ConfigSystem.root(ConfigTracker)
        PostRotationExecutor
        ServerObserver
        ItemImageAtlas
        ItemStackListRenderer
        IndicatorRenderer

        AddonManager.discover()
    }

    /**
     * Initializes in-built and add-on features.
     */
    private fun initializeFeatures() {
        // Register commands and modules
        PathingEngine.installOutputBridge()
        CommandManager.registerInbuilt()
        ModuleManager.registerInbuilt()

        AddonManager.registerCategories()
        AddonManager.initializeAddons()
    }

    /**
     * Loads local translations and account configuration before presenting initialization choices.
     */
    private suspend fun initializeResources(
        dispatcher: CoroutineDispatcher,
    ) = withContext(dispatcher) {
        LanguageManager.loadDefault()
        ConfigSystem.load(ClientAccountManager)
        if (ClientAccount.ENV_ACCOUNT != null) {
            ClientAccountManager.clientAccount = ClientAccount.ENV_ACCOUNT
        }
    }

    /** Online preloads run only after the initialization choice, so a slow endpoint cannot hide it. */
    private suspend fun initializeOnlineResources() {
        logger.info("Initializing API...")
        // Lookup API config
        ApiConfig.config

        supervisorScope {
            launch {
                val update = withTimeoutOrNull(8.seconds) { ClientUpdate.update.await() } ?: return@launch
                logger.info("[Update] Update available: $clientVersion -> ${update.lbVersion}")
            }
            launch {
                // Load cosmetics
                CosmeticService.refreshCarriers(force = true) {
                    logger.info("Successfully loaded ${CosmeticService.carriers.size} cosmetics carriers.")
                }
            }
            launch {
                // Download player heads
                HeadsCreativeModeTab.heads.getFinalState()
            }
            launch {
                MarketplaceConfigs.refresh()
            }
            launch {
                MarketplaceItems.refresh()
            }
            launch {
                MarketplaceManager.fillAuthors()
            }
            launch {
                if (ClientAccountManager.clientAccount != ClientAccount.EMPTY_ACCOUNT) {
                    runCatching {
                        ClientAccountManager.clientAccount.renew()
                    }.onFailure {
                        logger.error("Failed to renew client account token.", it)
                        if (it.httpException?.isInvalidGrant == true) {
                            ClientAccountManager.clientAccount = ClientAccount.EMPTY_ACCOUNT
                            ConfigSystem.store(ClientAccountManager)
                        }
                    }.onSuccess {
                        logger.info("Successfully renewed client account token.")
                    }
                }
            }
        }

        logger.info("API initialization done.")
    }

    /**
     * Prepares the GUI stage of the client.
     * This will load [ThemeManager], as well as the [BrowserBackendManager] and [ClientInteropServer].
     */
    private suspend fun prepareGuiStage(
        dispatcher: CoroutineDispatcher
    ) = withContext(dispatcher) {
        RenderSystem.assertOnRenderThread()

        BrowserBackendManager.init()
        ClientInteropServer.start()

        // Preload marketplace items
        ConfigSystem.load(MarketplaceManager)
        MarketplaceManager.subscribedItems.forEach(SubscribedItem::restoreRetired)
        AddonInstaller.stageSubscribedAddons()
        MarketplaceManager.reloadHandlers()

        if (!ClientInteropServer.isSkipping) {
            ThemeManager.init()
            ConfigSystem.load(ThemeManager)
            ThemeManager.load()
        }

        BlurEffectRenderer
        ScreenManager

        // Holds the chosen browser backend
        ConfigSystem.load(GlobalManager)

        val manager = requireNotNull(taskManager)
        manager.apply {
            launch("Initialization / 初始化") {
                InitializationNetwork.phase = "检测国家/地区（最多 4 秒）：https://ipinfo.io/json"
                val country = InitializationNetwork.detectCountry()
                val mode = run {
                    InitializationChoiceScreen.country = country
                    val selection = java.util.concurrent.CompletableFuture<InitializationChoiceScreen.Mode>()
                    InitializationChoiceScreen.pending = selection
                    InitializationNetwork.phase = "等待选择初始化方式"
                    try {
                        withContext(Dispatchers.Main) { mc.gui.setScreen(InitializationChoiceScreen()) }
                        selection.await()
                    } finally {
                        InitializationChoiceScreen.pending = null
                    }
                }
                InitializationNetwork.optimized = mode == InitializationChoiceScreen.Mode.OPTIMIZED
                InitializationNetwork.phase = if (InitializationNetwork.optimized)
                    "优化初始化：AI 优先阿里云镜像；浏览器沿用内置多源回退" else
                    "默认初始化：使用官方源及本地缓存"
                if (mode == InitializationChoiceScreen.Mode.SKIP) {
                    DownloadProxy.onlineInitialization = false
                    net.ccbluex.liquidbounce.integration.backend.isBrowserDisabled = true
                    DeepLearningEngine.markUnavailable()
                    InitializationNetwork.phase = "本次已跳过依赖初始化"
                    InitializationNetwork.report(InitializationNetwork.phase)
                } else {
                    withContext(Dispatchers.Main) { startDependencyTasks(this@apply) }
                }
            }
        }

        mc.gui.setScreen(TaskProgressScreen("初始化依赖 / Loading Required Libraries", requireNotNull(taskManager)))

        ioScope.launch {
            while (taskManager?.isCompleted == false) delay(250)
            InitializationNetwork.finish()
        }

        // Prepare glyph manager
        val duration = measureTime {
            FontManager.createGlyphManager()
        }
        logger.info("Completed loading fonts in ${duration.inWholeMilliseconds} ms.")
        logger.info("Fonts: [ ${FontManager.fontFaces.keys.joinToString()} ]")
    }

    private fun startDependencyTasks(manager: TaskManager) = with(manager) {
        // Either immediately starts browser or spawns a task to request browser dependencies,
        // and then starts the browser through render thread.
        BrowserBackendManager.makeDependenciesAvailable(this)

        // Initialize deep learning engine as task, because we cannot know if DJL will request
        // resources from the internet.
        launch("Deep Learning") { task ->
            runCatching {
                task.status = "检查本地 PyTorch 缓存，加载 CPU 引擎"
                if (InitializationNetwork.optimized) {
                    DownloadProxy.withDownloadClient {
                        net.ccbluex.liquidbounce.integration.task.MirroredPytorch.prepare(
                            DeepLearningEngine.enginesCacheFolder.toPath(), task
                        )
                    }
                }
                DeepLearningEngine.init(task)
                task.status = "引擎就绪，正在加载本地战斗模型（无网络请求）"
                ModelManager.load()
                DeepLearningEngine.markInitialized()
                task.status = "AI 初始化完成"
            }.onFailure { exception ->
                task.status = "AI 初始化失败，继续启动：${exception.javaClass.simpleName}"
                InitializationNetwork.report(task.status)
                task.subTasks.clear()
                DeepLearningEngine.markUnavailable()

                // LiquidBounce can still run without deep learning,
                // and we don't want to crash the client if it fails.
                logger.info("Failed to initialize deep learning.", exception)
            }
        }

        launch("Marketplace") { task ->
            task.status = "初始化在线资源：更新信息、披风、玩家头颅、市场目录"
            runCatching {
                initializeOnlineResources()
                task.status = "检查并更新市场订阅内容"
                MarketplaceManager.updateAll(task)
            }.onFailure { exception ->
                task.status = "市场更新失败，继续启动：${exception.javaClass.simpleName}"
                InitializationNetwork.report(task.status)
                logger.error("Failed to update marketplace items.", exception)
            }.also {
                DownloadProxy.onlineInitialization = false
            }

            task.isCompleted = true
        }
    }

    /**
     * Shuts down the client. This will save all configurations and stop all running tasks.
     */
    private fun shutdownClient() {
        if (!isInitialized) {
            return
        }
        isInitialized = false
        logger.info("Shutting down client...")

        // Unregister all event listener and stop all running tasks
        ChunkScanner.stopThread()
        FontManager.closeGlyphManager()
        EventManager.unregisterAll()

        // Shutdown HTTP server
        ioScope.launch {
            ClientInteropServer.stop()
        }

        AddonManager.notifyStopping()

        // Save all configurations
        ConfigSystem.storeAll()

        // Shutdown browser
        BrowserBackendManager.stop()
    }

    /**
     * Should be executed to start the client.
     */
    @Suppress("unused")
    private val startHandler = handler<ClientStartEvent> {
        runCatching {
            logger.info("Launching $CLIENT_NAME v$clientVersion by $CLIENT_AUTHOR")
            // Print client information
            logger.info("Client Version: $clientVersion ($clientCommit)")
            logger.info("Client Branch: $clientBranch")
            logger.info("Operating System: ${System.getProperty("os.name")} (${System.getProperty("os.version")})")
            logger.info("Java Version: ${System.getProperty("java.version")}")
            logger.info("Screen Resolution: ${mc.window.screenWidth}x${mc.window.screenHeight}")
            logger.info("Refresh Rate: ${mc.window.activeVideoMode?.refreshRate} Hz")

            // Initialize event manager
            EventManager

            // Register resource reloader
            val resourceManager = mc.resourceManager
            if (resourceManager is ReloadableResourceManager) {
                resourceManager.registerReloadListener(ClientResourceReloader)
                resourceManager.registerReloadListener(ThemeManager.reloader)
            } else {
                logger.warn("Failed to register resource reloader!")

                // Run resource reloader directly as fallback
                initializeClient(
                    workerDispatcher = Dispatchers.Default,
                    renderThreadDispatcher = Dispatchers.Main,
                ).thenCompose {
                    ThemeManager.reloader.reload()
                }
            }
        }.onFailure {
            ErrorHandler.fatal(it, additionalMessage = "Client start")
        }
    }

    @Suppress("unused")
    private val screenHandler = handler<ScreenEvent>(priority = FIRST_PRIORITY) { event ->
        val taskManager = taskManager ?: return@handler

        val proxySelection = DownloadProxy.pending
        if (proxySelection != null && !proxySelection.isCompleted) {
            if (event.screen !is DownloadProxyScreen) {
                event.cancelEvent()
                mc.gui.setScreen(DownloadProxyScreen(proxySelection))
            }
            return@handler
        }

        if (InitializationChoiceScreen.pending?.isDone == false) {
            if (event.screen !is InitializationChoiceScreen) {
                event.cancelEvent()
                mc.gui.setScreen(InitializationChoiceScreen())
            }
            return@handler
        }

        val selection = BrowserBackendManager.pendingSelection
        if (selection != null && !selection.isCompleted) {
            if (event.screen !is BrowserSelectionScreen) {
                event.cancelEvent()
                mc.gui.setScreen(BrowserSelectionScreen(BrowserBackendManager.selectableBackends, selection))
            }
            return@handler
        }

        if (!taskManager.isCompleted && event.screen !is TaskProgressScreen) {
            event.cancelEvent()
            mc.gui.setScreen(TaskProgressScreen("Loading Required Libraries", taskManager))
        }
    }

    /**
     * Resource reloader which is executed on client start and reload.
     * This is used to run async tasks without blocking the main thread.
     *
     * For now this is only used to check for updates and request additional information from the internet.
     *
     * @see net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener
     * @see PreparableReloadListener
     */
    private object ClientResourceReloader : PreparableReloadListener {
        override fun reload(
            store: PreparableReloadListener.SharedState,
            prepareExecutor: Executor,
            synchronizer: PreparableReloadListener.PreparationBarrier,
            applyExecutor: Executor
        ): CompletableFuture<Void> {
            return synchronizer.wait(net.minecraft.util.Unit.INSTANCE)
                .thenApply {
                    val prepareDispatcher = prepareExecutor.asCoroutineDispatcher()
                    val applyDispatcher = applyExecutor.asCoroutineDispatcher()
                    initializeClient(
                        workerDispatcher = prepareDispatcher,
                        renderThreadDispatcher = applyDispatcher,
                    ).thenCompose { ThemeManager.reloader.reload() }
                    null
                }
        }

        override fun getName() = CLIENT_NAME
    }

    /**
     * Should be executed to stop the client.
     */
    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        shutdownClient()
    }

}
