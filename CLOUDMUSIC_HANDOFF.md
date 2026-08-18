# CloudMusic 合并交接文档

> 本文档供下一个会话（修 BUG）使用。CloudMusic 已作为 LiquidBounce 内嵌模块维护；本次命令、HUD、配置迁移和持久化修复与本文档一起提交。
> 参考仓库（`需要合并/malilib`、`需要合并/CloudMusic-Mod`、`需要合并/VapeV4.21`）**只读**，任何修复只改 LiquidBounce 代码。

## 0. 任务结论（一句话）

malilib 26.2 与 CloudMusic-Mod 已并入 LB 客户端（非独立 MOD）；`/cloudmusic` 内部树由 `.rikkamusic` 调用，`.music` 是别名；新增 LB 风格 GUI（侧边栏歌单 + 搜索 + 封面列表 + 播放条 + 设置页 + 扫码登录）和由 HUD Editor 管理的播放/歌词 HUD；ActionBar 歌词等不融合的功能已删除。

## 1. 文件布局

### 合并进来的库/模块
- malilib 库：`src/main/java/fi/`（545 个文件）、`src/main/resources/assets/malilib/`、`src/main/resources/malilib.mixins.json`
- CloudMusic 业务代码（原样移植 + 少量改动）：`src/main/java/fengliu/cloudmusic/`（49 个文件）
- CloudMusic 资源：`src/main/resources/assets/cloudmusic/`（icon.png、lang/zh_cn.json、texture/music_icon.png）
- CloudMusic mixins：`src/main/resources/cloudmusic.mixins.json`

### 新写的 LB 集成代码（Kotlin）
- `src/main/kotlin/fengliu/cloudmusic/gui/CloudMusicGui.kt` — 主题色板 + 文本工具（drawCloudMusicText）
- `src/main/kotlin/fengliu/cloudmusic/gui/CloudMusicScreen.kt` — 主界面（侧边栏/搜索/列表/播放条）
- `src/main/kotlin/fengliu/cloudmusic/gui/CloudMusicLoginScreen.kt` — 扫码登录
- `src/main/kotlin/fengliu/cloudmusic/gui/CloudMusicSettingsScreen.kt` — LB 风格设置页（重绘原 malilib 配置）
- `src/main/kotlin/fengliu/cloudmusic/gui/CloudMusicAsync.kt`、`CloudMusicCoverCache.kt` — 异步/封面缓存
- `src/main/kotlin/fengliu/cloudmusic/hud/CloudMusicHudComponent.kt` — HUD 组件
- `src/main/kotlin/fengliu/cloudmusic/command/CloudMusicCommands.kt`、`LbClientCommandSource.kt` — `.cloudmusic` 命令桥

### 集成点（改 BUG 前先看这里）
- `src/main/java/net/ccbluex/liquidbounce/integration/MergedModulesInitializer.java` — ClientModInitializer：`new MaLiLib().onInitialize()` → `CloudMusicClient.init()`
- `src/main/resources/fabric.mod.json` — entrypoint + `malilib.mixins.json`/`cloudmusic.mixins.json`
- `src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/misc/ModuleCloudMusic.kt` — 模块（MISC，disableActivation，origin=XUAN_RIKKA），`onRegistration` 里注册 `.cloudmusic`
- `src/main/kotlin/net/ccbluex/liquidbounce/features/module/ModuleManager.kt` — Misc 列表
- `src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/ModuleHud.kt` — `tree(CloudMusicHudComponent)`
- `src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/component/HudComponentManager.kt` — nativeComponents
- `src/main/resources/liquidbounce.accesswidener` — 末尾追加 26 条 malilib AW（v1 格式）
- `src/main/resources/resources/liquidbounce/lang/zh_cn.json` + `en_us.json` — `liquidbounce.module.cloudMusic.description`
- `build.gradle.kts` — 新增依赖：`com.google.zxing:core:3.5.1`、`javazoom:jlayer:1.0.1`、`com.googlecode.soundlibs:mp3spi:1.9.5.4`、`com.googlecode.soundlibs:tritonus-share:0.3.7.4`、`org.jflac:jflac-codec:1.5.2`（均 `implementation` + `jij`）

## 2. 对参考代码的改动（千万别“修复”回原样）

- `MusicCommand.java`：`DISPATCHER` 改 `public static final`；新增 `executeCommand(String, FabricClientCommandSource)`；新增公开 API：`getMusic163()`、`playMusics(List)`、`playMusicsFrom(List,int)`、`playMusic(IMusic)`、`getLoginMusic163()`、`setCookie(String)`、`searchMusics(String)`；`runCommand` 改公开。`registerAll()` 只构建 DISPATCHER 树（不注册到 Fabric 命令系统）。
- `MusicPlayer.java`：新增 `startFrom(int)`（从指定下标开始播放）。
- `Page.java`：新增 `getCurrentPageData()`。
- `HotkeysCallback.java`：`ConfigGui` → `CloudMusicScreen`（原配置 GUI 已删）。
- `ChatHudMixin.java`：删掉全部调试日志（tick 注入、LOGGER、未用 shadow），保留分享消息样式逻辑。
- **已删除文件**：`fengliu/cloudmusic/render/MusicHudRenderer.java`、`fengliu/cloudmusic/compat/ModMenu.java`、`fengliu/cloudmusic/config/ConfigGui.java`、`fi/dy/masa/malilib/compat/iris/IrisCompat.java`（+ 空目录）。
- `fi/dy/masa/malilib/render/uniform/ChunkFixUniform.java`：`hasShadersOn = 0`（去 Iris）。
- `fi/dy/masa/malilib/mixin/render/MixinRenderPipelines.java`：删除 `IrisCompat.registerPipelines()` 及 import。
- `fi/dy/masa/malilib/compat/mixin/MaLiLibMixinConfigPlugin.java`：替换为 no-op 插件（注意 `preApply`/`postApply` 第二参数是 `ClassNode`）。

## 3. 架构与数据流

- **启动**：`MergedModulesInitializer.onInitializeClient` → malilib 初始化 → `CloudMusicClient.init()`（`Configs.load()`、`HotkeysCallback.init()`、注册 `InputHandler` 到 malilib 输入系统、`MusicCommand.registerAll()`）。
- **命令**：`.cloudmusic ...` → `CloudMusicCommands`（LB CommandManager）→ `MusicCommand.executeCommand(args, LbClientCommandSource)` → `DISPATCHER.execute`。`LbClientCommandSource` 把 chat 反馈接到 LB 的 `chat()`/`markAsError()`。
- **GUI**：模块开关（inGame 时）或 `.cloudmusic` 打开 `CloudMusicScreen`；登录/设置分别切到 `CloudMusicLoginScreen`/`CloudMusicSettingsScreen`，均用 `mc.gui.setScreen(...)`（MC 26.2 API）。
- **数据**：`MusicCommand` 静态方法是对 CloudMusic 的唯一入口；`MusicPlayer` 线程内解码播放；封面：播放中封面走原 `MusicIconTexture`，列表封面走 `CloudMusicCoverCache`（DynamicTexture 缓存）。
- **配置**：malilib `Configs` 保存到 `LiquidBounce/cloudmusic.json`（登录 Cookie 也存这里，不进源码）；首次启动会从旧的 `config/cloudmusic.json` 迁移。

## 4. 关键 API 备忘（修 BUG 直接用）

- MC 26.2 Screen：`mc.gui.setScreen`；输入回调 `mouseClicked(MouseButtonEvent, Boolean)`、`mouseScrolled(x,y,h,v)`、`keyPressed(KeyEvent)`、`charTyped(CharacterEvent)`；渲染 `extractRenderState(GuiGraphicsExtractor, ...)`。
- LB 渲染：`drawQuad`、`drawRoundedRect`、`drawTriangle`、`drawTexQuad(texture.textureSetup, ...)`、`scissorStack.withPush(getBounds(...))`、`textureSetup` 扩展。
- LB 字体：`FontManager.FONT_RENDERER`；`process(text, color)` → `draw(processed) { x; y; scale; shadow; horizontalAnchor; verticalAnchor }`；`getStringWidth`、`scaleToVanillaFont`；有 CJK 回退（Windows=Microsoft YaHei），中文可正常渲染。GUI 文本统一走 `CloudMusicGui.drawCloudMusicText`。
- malilib 配置对象：`ConfigInteger`（get/setIntegerValue、min/max）、`ConfigBoolean(Hotkeyed)`、`ConfigString`、`ConfigColor`（int 色值）、`ConfigOptionList`（`getOptionListValue().cycle(true)`）、`ConfigHotkey`（`getKeybind()`）。`Configs.INSTANCE.save()`。
- 数据对象：`IMusic`（id/name/picUrl/playUrl/duration）、`Music`（artists/album，`Music.getArtistsName`）、`DjMusic`、`PlayList`（count/creator/cover）、`My`（`likeMusicPlayList()`、`playLists(0,100)`）、`Page/ApiPage.getCurrentPageData()`。
- `MusicPlayer`：`start/stop/continues/switchPlay/next/prev/to(index)/seek(ms)/volumeSet/getVolumePercentage/getPlayingProgress/getPlayingProgressToString/getLyric/getPlayingMusic`（返回 String[5] 歌词行）。

## 5. 已知风险 / 待验证（下一会话重点）

1. **GUI 未在游戏内人工点验**。`runClient` 只验证了客户端初始化与 mixins 应用。重点手测：打开主界面、点击歌单/搜索、封面加载、滚轮滚动、播放条（上一首/播放/暂停/下一首、进度与音量拖动）、设置页各控件、扫码登录。
2. **搜索输入法**：`charTyped` 按码点处理，中文 IME 组合输入可能不完整，必要时换 LB 的文本框方案。
3. **SoundSystemMixin**：`tick(Z)` 用“上一次 play 的类别”判断是否取消 tick，实现粗糙（参考原样），观察是否误杀/漏杀游戏音乐；如与 LB 音乐处理冲突可考虑移除该 mixin（功能是“播放时暂停游戏 BGM”）。
4. **malilib 全局 mixins 与 LB 交互**：`client.MixinMinecraft`、`gui.MixinGui`、`input.MixinKeyboard/Mouse` 等已生效但未深度测试；若出现怪异输入/GUI 行为，从这里查，必要时从 `malilib.mixins.json` 移除（`test.*` mixins 是 DEBUG 门控，可留）。
5. **音频解码**：新依赖 `implementation`+`jij`；dev 环境已正常，出正式包后确认 jij 打包完整（mp3/flac 可播）。
6. **HUD 组件**：HUD Editor 里启用“CloudMusic”；位置/缩放/歌词两行截断需要实机看效果。
7. **CloudMusicScreen 与 ClickGUI 同时打开**时的层级/焦点/ESC 行为。
8. **Configs**：CloudMusic 配置位于 `LiquidBounce/cloudmusic.json`；迁移旧 `config/cloudmusic.json` 时要保留所有字段，尤其是 Cookie、代理与热键。
9. **聊天分享样式**（ChatHudMixin）：与 LB 自己的聊天混入共存，需观察是否有样式/点击冲突。
10. **`.cloudmusic` 命令**：Brigadier 树在 LB Command 里的错误提示、帮助页、分页（`page` 命令）显示是否正常。

## 6. 验证命令（AGENTS.md 强制流程）

- `cmd /c gradlew compileKotlin compileJava`
- `cmd /c gradlew test --tests net.ccbluex.liquidbounce.config.types.ValueVisibilityTest --no-parallel`
- 在 `src-theme` 下：`cmd /c npm run check`
- 语言 JSON 用结构化解析器校验
- `git diff --check`
- Latest update (2026-08-19):

  - Command routing was corrected: `.rikkamusic`/`.music` and existing clickable `/rikkamusic`/`/music` links are intercepted before LiquidBounce's generic command tokenizer, then executed by the original CloudMusic Brigadier dispatcher. This preserves CJK text, quoted strings containing spaces, and greedy arguments. It is intentionally not a Fabric client-command registration, so unrelated LiquidBounce commands such as `.t killaura` continue through the normal command executor.
  - Completion remains registered in the LB command catalog. Literal branches are suggested from the original Brigadier tree; when the next token is a free string, Tab offers `""` as a valid quoted-input template.
  - Lyric HUD update: `CloudMusic` and `MusicLyrics` have `ShowTranslation`. CloudMusic keeps its fixed card size by moving original and translation to compact rows above the progress bar. `MusicLyrics` adds `LyricColor`, `TranslationColor`, `Background`, and `Font` (`LiquidBounce` or `Minecraft`). Long lyric lines use clipped marquee scrolling instead of ellipsis truncation.
  - Clickable search/detail/page actions use LB's `RunnableClickEvent` and invoke the CloudMusic dispatcher directly. Do not replace these with Minecraft `RunCommand`: that opens the server-command confirmation dialog and cannot execute a client-only command. `page to` deliberately suggests `.rikkamusic page to ` because it needs a page number before it can execute.
  - `MusicDynamicIsland` is lyrics-first: LiquidBounce logo on the left, circular album cover on the right, original plus an optional translation in the centre, with a configurable lyric progress bar. Its width is recomputed for every lyric pair; a lyric transition contracts the island first, then expands symmetrically from screen centre. `ShowTranslation` suppresses the second row and its width contribution.
  - Commands: direct client-command roots `/rikkamusic` and `/music` redirect to the complete original CloudMusic Brigadier tree. Existing `.rikkamusic` and `.music` remain compatibility bridges. Every generated clickable command now uses `/rikkamusic`, so pages, details, shares, and actions retain original argument parsing and completion behavior.
  - Native HUD catalog: `CloudMusic` (default-enabled for a newly created client config), `MusicLyrics`, `MusicActionbarLyrics`, and `MusicDynamicIsland`.
  - Normal `CloudMusic` uses the original first-version square, full-slot cover texture. `ShowLyrics` renders the current lyric in the fixed second text row; disabling it restores the artist/album subtitle. The component height never expands.
  - Dynamic Island uses a separate alpha-masked circular cover texture, the same semi-transparent LiquidBounce panel background as Normal, lyric lines at the right of the cover, playback-driven expand/collapse animation, and a chat-open `+` action which resets it to the calculated top-center position.
  - End-of-track seeking now avoids encoder-padding metadata tails, aligns PCM frame reads/writes, restarts the output line after seek, and stops safely on premature EOF.
  - Validation: `compileKotlin compileJava`, the specified `ValueVisibilityTest`, `src-theme` `npm run check`, `git diff --check`, and `runClient` have passed. The final client run initialized LiquidBounce successfully; CloudMusic playback and lyric loading were observed without a new exception.
  - The handoff document is part of the CloudMusic integration and should be committed with the implementation changes.
  - CloudMusic settings are stored as `LiquidBounce/cloudmusic.json`, alongside LiquidBounce's own settings. The first run moves the legacy `config/cloudmusic.json` into this client-owned location, preserving volume, proxy, cookie, quality, hotkeys, and every other original CloudMusic setting.
  - Volume persistence is handled before audio-line rendering: changing volume while stopped still writes `cloudmusic.config.volume`, and startup applies the loaded value to the initial player.
  - Deployment: after `cmd /c gradlew compileKotlin compileJava jar`, `build/libs/liquidbounce-0.39.1XuanRikka0813.jar` was copied to `C:\HMCL\.minecraft\versions\Friends-OWN\mods\liquidbounce-0.39.1XuanRikka0813.jar`; source and destination SHA-256 values matched. Restart Friends-OWN to load this jar.
- `cmd /c gradlew runClient`（最终验证：客户端初始化 + 无异常；dev 自动登录使用已保存账号）
- 全部通过后 `git status --short` 检查并 commit

## 7. 本机工具坑（重要）

- **`apply_patch` 不可用**（Access denied），改文件用 Python。
- **PowerShell 管道会把中文变 `?`**（`$OutputEncoding` 问题）：不要在 `@'...'@ | python -` 里传中文。正确姿势：用 `[System.IO.File]::WriteAllText(path, $content, UTF8)` 把 Python 脚本写到磁盘，再 `python -X utf8 script.py` 执行。
- Gradle 偶发 `problems-report FileAlreadyExistsException`：用 Python `shutil.rmtree("build/reports/problems")` 清掉重跑。
- 所有 Gradle/Node 命令必须 `cmd /c ...`。
- 文件必须 UTF-8。

## 8. 硬性规则

- 只读 `需要合并/`，不要改参考仓库任何文件。
- 不提交 cookie/token/账号文件。
- 保留用户工作区改动，不 reset/revert 无关内容。
- 新模块 origin 用 `XUAN_RIKKA`；改自 LB 的模块用 `LIQUID_BOUNCE_MODIFIED`。
- malilib（`fi/`）保持自包含，不要反向依赖 LB 内部 API。
