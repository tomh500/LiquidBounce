<script lang="ts">
    import {onMount} from "svelte";
    import {
        getRikkaMusicCollection, submitRikkaMusicLogin, sendRikkaMusicCaptcha, cancelRikkaMusicLogin, logoutRikkaMusic,
        type RikkaMusicCollection, controlRikkaMusic, deleteScreen, getRikkaMusicCloud, getRikkaMusicLibrary, getRikkaMusicLoginStatus,
        getRikkaMusicPlaylist, getRikkaMusicQrCodeUrl, getRikkaMusicSettings, getRikkaMusicState,
        getRikkaMusicLocal, mountRikkaMusicLocal, openFileDialog, searchRikkaMusic, startRikkaMusicLogin, updateRikkaMusicSetting,
        type RikkaMusicLibrary, type RikkaMusicLoginStatus, type RikkaMusicPlaylist,
        type RikkaMusicSearchResult, type RikkaMusicSetting, type RikkaMusicSong, type RikkaMusicState
    } from "../../integration/rest";

    type View = "liked" | "cloud" | "local" | "search" | "settings" | "collection" | "playlists";
    type SettingsCategory = "account" | "playback" | "interface" | "command" | "network" | "hotkeys";

    const categoryLabels: Record<SettingsCategory, string> = {account: "账号", playback: "播放", interface: "界面", command: "命令", network: "网络", hotkeys: "按键"};
    const settingLabels: Record<string, string> = {
        "gui.draggable.window": "允许拖动窗口", "gui.window.x": "窗口横坐标", "gui.window.y": "窗口纵坐标", "gui.theme": "界面主题", "volume": "音量", "play.url": "在线播放", "play.loop": "循环播放", "play.auto.random": "自动随机播放", "play.quality": "播放音质", "dj.radio.play.asc": "电台按时间升序播放", "play.not.game.music": "暂停游戏背景音乐", "play.block.note.block": "屏蔽音符盒", "play.block.jukebox": "屏蔽唱片机", "exit.game.stop.music": "离开世界时暂停音乐", "stop.play.show.ui": "停止播放时关闭界面", "cache.path": "缓存路径", "cache.max.mb": "最大缓存空间", "cache.delete.mb": "缓存清理空间", "page.limit": "每页歌曲数量", "music.info": "显示播放信息", "music.info.x": "播放信息横坐标", "music.info.y": "播放信息纵坐标", "music.info.effect.offset": "药水效果时调整信息位置", "music.info.effect.offset.x": "信息横向偏移", "music.info.effect.offset.y": "信息纵向偏移", "music.info.color": "信息背景颜色", "music.progress.bar.color": "进度条颜色", "music.player.progress.bar.color": "已播放进度条颜色", "music.progress.font.color": "进度文字颜色", "music.info.title.font.color": "歌曲标题颜色", "music.info.font.color": "歌曲副标题颜色", "lyric.style": "歌词显示样式", "lyric.color": "歌词颜色", "lyric.scale": "歌词缩放比例", "lyric.x": "歌词横坐标", "lyric.y": "歌词纵坐标", "click.run.command": "点击聊天选项执行命令", "login.country.code": "手机国家码", "login.qr.check.num": "二维码轮查次数", "login.qr.check.time": "二维码轮查间隔", "http.max.retry": "请求重试次数", "http.time.out": "请求超时时间", "http.proxy": "使用 HTTP 代理", "http.proxy.ip": "代理服务器地址", "http.proxy.port": "代理服务器端口", "open.config.gui": "打开配置界面", "switch.play.music": "暂停 / 继续播放", "play.music": "继续播放", "next.music": "下一首歌曲", "prev.music": "上一首歌曲", "stop.music": "暂停播放", "exit.play": "退出播放", "play.volume.add": "增加音量", "play.volume.down": "降低音量", "delete.play.music": "删除当前歌曲", "trash.add.play.music": "将当前歌曲移入垃圾桶", "like.music": "喜欢当前歌曲", "playlist.add.music": "添加到歌单", "playlist.del.music": "从歌单删除", "playlist.random": "随机播放队列", "enable.nearby.monster.decrease.volume": "靠近生物时降低音量", "nearby.monster.decrease.volume.value": "附近生物音量降低", "nearby.monster.decrease.volume.radius": "附近生物距离", "nearby.monster.is.survival": "仅对生存模式生物生效"
    };

    let library: RikkaMusicLibrary | null = null;
    let selected: RikkaMusicPlaylist | null = null;
    let state: RikkaMusicState = {playing: false, progress: 0, volume: 70, song: null, theme: "LiquidBounce", quality: "exhigh"};
    let view: View = "liked";
    let loginPolling = false;
    let search = "";
    let searchResults: RikkaMusicSong[] = [];
    let searchPlaylists: RikkaMusicPlaylist[] = [];
    let searchArtists: RikkaMusicPlaylist[] = [];
    let searchPage = 1;
    let searchPageCount = 1;
    let searchTotal = 0;
    let loading = true;
    let error = "";
    let settingsCategory: SettingsCategory = "account";
    let settings: RikkaMusicSetting[] = [];
    let settingsLoading = false;
    let showLogin = false;
    let login: RikkaMusicLoginStatus = {status: "idle", message: ""};
    let qrCodeUrl = "";
    let cloudSongs: RikkaMusicSong[] = [];
    let cloudLoading = false;
    let searchTab: "综合" | "单曲" | "歌单" | "歌手" = "综合";
    let localPath = "";
    let localSongs: RikkaMusicSong[] = [];
    let viewportScale = 1;
    let expandedSettingKey = "";
    let collection: RikkaMusicCollection | null = null;
    let collectionId = 0;
    let collectionLoading = false;
    let loginMethod: "password" | "captcha" | "qr" = "password";
    let loginAccount = "";
    let loginSecret = "";
    let countryCode = 86;
    let accountBusy = false;
    let captchaUntil = 0;
    let captchaRemaining = 0;
    let windowX = -1;
    let windowY = -1;
    let dragStart: {x: number; y: number; left: number; top: number} | null = null;
    let recordingKey = "";
    let recordedKeys: string[] = [];
    let showLyrics = false;

    $: songs = view === "collection" ? collection?.songs ?? [] : view === "search" ? searchResults : view === "cloud" ? cloudSongs : view === "local" ? localSongs : selected?.songs ?? [];
    $: title = view === "search" ? `搜索 “${search}”` : selected?.name ?? "我喜欢的音乐";
    $: progressRatio = state.song && state.song.duration > 0 ? Math.min(1, state.progress / state.song.duration) : 0;

    onMount(() => {
        void initialize();
        const stateTimer = window.setInterval(() => { void refreshState(); captchaRemaining = Math.max(0, Math.ceil((captchaUntil - Date.now()) / 1000)); }, 750);
        const loginTimer = window.setInterval(() => void refreshLogin(), 1200);
        updateScale();
        window.addEventListener("resize", updateScale);
        return () => { if (loginPolling) void cancelRikkaMusicLogin(); window.clearInterval(stateTimer); window.clearInterval(loginTimer); window.removeEventListener("resize", updateScale); };
    });

    function updateScale() {
        viewportScale = Math.min(1, window.innerWidth / 2560, window.innerHeight / 1440);
        windowX = Math.max(0, Math.min(windowX < 0 ? (window.innerWidth - 1320 * viewportScale) / 2 : windowX, window.innerWidth - 1320 * viewportScale));
        windowY = Math.max(0, Math.min(windowY < 0 ? (window.innerHeight - 960 * viewportScale) / 2 : windowY, window.innerHeight - 960 * viewportScale));
    }
    function startDrag(event: PointerEvent) {
        if (event.button !== 0) return;
        dragStart = {x: event.clientX, y: event.clientY, left: windowX, top: windowY};
        event.preventDefault();
    }
    function moveDrag(event: PointerEvent) {
        if (!dragStart) return;
        windowX = dragStart.left + event.clientX - dragStart.x;
        windowY = dragStart.top + event.clientY - dragStart.y;
        updateScale();
    }
    async function finishDrag() {
        if (!dragStart) return;
        dragStart = null;
        await saveWindowPosition();
    }
    async function saveWindowPosition() {
        try { await updateRikkaMusicSetting("gui.window.x", Math.round(windowX)); await updateRikkaMusicSetting("gui.window.y", Math.round(windowY)); }
        catch { error = "窗口位置保存失败"; }
    }
    async function centerWindow() { windowX = -1; windowY = -1; updateScale(); await saveWindowPosition(); }
    async function initialize() {
        await Promise.all([refreshLibrary(), refreshState(), refreshLocal()]);
        try {
            const all = (await getRikkaMusicSettings("all")).settings;
            windowX = Number(all.find(s => s.key === "gui.window.x")?.value ?? -1);
            windowY = Number(all.find(s => s.key === "gui.window.y")?.value ?? -1);
            updateScale();
        } catch { /* Keep the default centered position. */ }
    }
    async function refreshLibrary() {
        loading = true;
        try {
            library = await getRikkaMusicLibrary();
            if (library.authenticated && library.liked) selected = library.liked.songs ? library.liked : await getRikkaMusicPlaylist(library.liked.id);
            else selected = null;
            error = library.error ?? "";
        } catch (e) { error = e instanceof Error ? e.message : "无法加载音乐库"; }
        finally { loading = false; }
    }
    async function refreshState() { try { state = await getRikkaMusicState(); } catch { /* The theme browser can outlive the REST server during shutdown. */ } }
    async function refreshLogin() {
        if (!showLogin || library?.authenticated || !loginPolling) return;
        try {
            login = await getRikkaMusicLoginStatus();
            if (login.status === "waiting" && !qrCodeUrl) qrCodeUrl = getRikkaMusicQrCodeUrl();
            if (login.status === "success") { loginPolling = false; loginSecret = ""; await refreshLibrary(); }
        } catch { /* Keep the last known status while the QR file is written. */ }
    }
    async function openLogin() {
        showLogin = true;
        loginMethod = "password";
        loginPolling = false;
        loginSecret = "";
        qrCodeUrl = "";
        login = {status: "idle", message: library?.authenticated ? "" : "请选择登录方式"};
    }
    async function closeLogin() { showLogin = false; loginPolling = false; loginSecret = ""; await cancelRikkaMusicLogin(); }
    async function chooseLoginMethod(method: typeof loginMethod) {
        loginPolling = false;
        await cancelRikkaMusicLogin();
        loginMethod = method;
        loginSecret = "";
        qrCodeUrl = "";
        login = {status: "idle", message: ""};
        if (method === "qr") {
            try { login = await startRikkaMusicLogin(); loginPolling = true; }
            catch { login = {status: "error", message: "二维码获取失败，请重试"}; }
        }
    }
    async function submitLogin() {
        accountBusy = true;
        try { login = await submitRikkaMusicLogin(loginMethod, loginAccount.trim(), loginSecret, countryCode); loginPolling = true; }
        catch (e) { login = {status: "error", message: e instanceof Error ? e.message : "登录失败"}; }
        finally { loginSecret = ""; accountBusy = false; }
    }
    async function sendCaptcha() {
        accountBusy = true;
        try { const result = await sendRikkaMusicCaptcha(loginAccount.trim(), countryCode); login.message = result.message; captchaUntil = Date.now() + 60000; captchaRemaining = 60; }
        catch (e) { login = {status: "error", message: e instanceof Error ? e.message : "验证码发送失败"}; }
        finally { accountBusy = false; }
    }
    async function logout() {
        accountBusy = true;
        try { await logoutRikkaMusic(); library = null; selected = null; collection = null; view = "liked"; login = {status: "idle", message: "已退出账号"}; await refreshLibrary(); }
        catch { login = {status: "error", message: "退出账号失败"}; }
        finally { accountBusy = false; }
    }
    async function openCollection(kind: string, id = 0, page = 1) {
        view = "collection";
        collectionLoading = true;
        try { collection = await getRikkaMusicCollection(kind, id, page); collectionId = id; error = ""; }
        catch (e) { collection = null; error = e instanceof Error ? e.message : "无法读取列表"; }
        finally { collectionLoading = false; }
    }
    async function openProfile() { await closeLogin(); if (library?.userId) await openCollection("profile", library.userId); }
    async function openArtist(id: number) { showLogin = false; await openCollection("artist", id); }
    function keyLabel(keys: string) {
        const labels: Record<string, string> = {LEFT_CONTROL: "左 Ctrl", RIGHT_CONTROL: "右 Ctrl", LEFT_SHIFT: "左 Shift", RIGHT_SHIFT: "右 Shift", LEFT_ALT: "左 Alt", RIGHT_ALT: "右 Alt", LEFT_SUPER: "左 Win", RIGHT_SUPER: "右 Win", SPACE: "空格", ENTER: "回车", TAB: "Tab", BACKSPACE: "退格", DELETE: "删除", UP: "↑", DOWN: "↓", LEFT: "←", RIGHT: "→", ESCAPE: "Esc", MOUSE_LEFT: "鼠标左键", MOUSE_RIGHT: "鼠标右键", MOUSE_MIDDLE: "鼠标中键", BUTTON_1: "鼠标左键", BUTTON_2: "鼠标右键", BUTTON_3: "鼠标中键", BUTTON_4: "鼠标后退", BUTTON_5: "鼠标前进", RETURN: "回车", CAPS_LOCK: "大写锁定", NUM_LOCK: "数字锁定", PAGE_UP: "上一页", PAGE_DOWN: "下一页", PRINT_SCREEN: "截屏", SCROLL_LOCK: "滚动锁定", KP_ENTER: "小键盘回车"};
        return keys.split(/[,+]/).map(key => labels[key.trim()] ?? key.trim()).filter(Boolean).join(" + ") || "未绑定";
    }
    function captureKey(event: KeyboardEvent) {
        if (!recordingKey) return;
        event.preventDefault(); event.stopPropagation();
        if (event.code === "Escape") { recordingKey = ""; return; }
        const names: Record<string, string> = {ControlLeft: "LEFT_CONTROL", ControlRight: "RIGHT_CONTROL", ShiftLeft: "LEFT_SHIFT", ShiftRight: "RIGHT_SHIFT", AltLeft: "LEFT_ALT", AltRight: "RIGHT_ALT", MetaLeft: "LEFT_SUPER", MetaRight: "RIGHT_SUPER", ArrowUp: "UP", ArrowDown: "DOWN", ArrowLeft: "LEFT", ArrowRight: "RIGHT", CapsLock: "CAPS_LOCK", NumLock: "NUM_LOCK", ScrollLock: "SCROLL_LOCK", PageUp: "PAGE_UP", PageDown: "PAGE_DOWN", PrintScreen: "PRINT_SCREEN", BracketLeft: "LEFT_BRACKET", BracketRight: "RIGHT_BRACKET", Backquote: "GRAVE_ACCENT", Equal: "EQUALS", Quote: "APOSTROPHE", NumpadAdd: "KP_PLUS", NumpadSubtract: "KP_MINUS", NumpadMultiply: "KP_MULTIPLY", NumpadDivide: "KP_DIVIDE", NumpadEnter: "KP_ENTER", NumpadDecimal: "KP_PERIOD", ContextMenu: "APPLICATION"};
        const key = names[event.code] ?? event.code.replace(/^Key|^Digit/, "").replace(/^Numpad/, "KP_").toUpperCase();
        if (!recordedKeys.includes(key)) recordedKeys = [...recordedKeys, key];
    }
    async function finishKey() {
        if (!recordingKey || !recordedKeys.length) return;
        const setting = settings.find(s => s.key === recordingKey);
        recordingKey = "";
        if (setting) await saveSetting(setting, recordedKeys.join(","));
    }
    async function openPlaylist(playlist: RikkaMusicPlaylist) { view = "liked"; selected = playlist.songs ? playlist : await getRikkaMusicPlaylist(playlist.id); }
    function searchType(): "song" | "playlist" | "artist" { return searchTab === "歌单" ? "playlist" : searchTab === "歌手" ? "artist" : "song"; }
    async function runSearch(page = 1) {
        if (!search.trim()) return;
        try {
            view = "search";
            const result = await searchRikkaMusic(search.trim(), searchType(), page);
            searchPage = result.page; searchPageCount = result.pageCount; searchTotal = result.total;
            if (searchType() === "playlist") { searchPlaylists = result.items as RikkaMusicPlaylist[]; searchArtists = []; searchResults = []; }
            else if (searchType() === "artist") { searchArtists = result.items as RikkaMusicPlaylist[]; searchPlaylists = []; searchResults = []; }
            else searchResults = result.items as RikkaMusicSong[];
            error = "";
        }
        catch (e) { error = e instanceof Error ? e.message : "搜索失败"; }
    }
    async function selectSearchTab(tab: typeof searchTab) { searchTab = tab; if (search.trim()) await runSearch(1); }
    async function openLocal() {
        try {
            const result = await openFileDialog({mode: "OPEN_DIRECTORY"} as never);
            if (result.file) { const mounted = await mountRikkaMusicLocal(result.file); localPath = mounted.path; localSongs = mounted.songs; }
        } catch (e) { error = e instanceof Error ? e.message : "无法选择本地文件夹"; }
    }
    async function refreshLocal() { const local = await getRikkaMusicLocal(); localPath = local.path; localSongs = local.songs; }
    async function openCloud() {
        view = "cloud";
        cloudLoading = true;
        try { cloudSongs = await getRikkaMusicCloud(); error = ""; }
        catch (e) { error = e instanceof Error ? e.message : "无法读取音乐云盘"; }
        finally { cloudLoading = false; }
    }
    async function playPlaylist(index: number) {
        try {
            if (view === "collection" && collection) await controlRikkaMusic(collection.kind === "fm" ? "play-fm" : "play-browse", undefined, undefined, index, undefined, undefined, collection.queueToken);
            else if (view === "search") await controlRikkaMusic("play-search", undefined, undefined, index, search, searchPage);
            else if (view === "cloud") await controlRikkaMusic("play-cloud", undefined, undefined, index);
            else if (view === "local") await controlRikkaMusic("play-local", undefined, undefined, index);
            else if (selected) await controlRikkaMusic("play-playlist", undefined, selected.id, index);
            await refreshState();
        } catch (e) { error = e instanceof Error ? e.message : "无法开始播放"; }
    }
    async function control(action: string, value?: number) {
        try { await controlRikkaMusic(action, value); await refreshState(); }
        catch (e) { error = e instanceof Error ? e.message : "播放器操作失败"; }
    }
    async function cycleQuality() { await control("quality", 1); }
    function qualityLabel(value: string) { return ({standard: "标准", higher: "较高", exhigh: "极高", lossless: "无损", hires: "Hi-Res"} as Record<string, string>)[value] ?? value; }
    async function toggleTheme() { await control("theme", state.theme === "Light" ? 0 : 1); }
    async function openSettings() { view = "settings"; await loadSettings(); }
    async function loadSettings() {
        settingsLoading = true;
        try { settings = (await getRikkaMusicSettings(settingsCategory)).settings; }
        catch (e) { error = e instanceof Error ? e.message : "无法读取音乐设置"; }
        finally { settingsLoading = false; }
    }
    async function selectSettingsCategory(category: SettingsCategory) { settingsCategory = category; await loadSettings(); }
    async function saveSetting(setting: RikkaMusicSetting, value?: string | number | boolean) {
        try {
            await updateRikkaMusicSetting(setting.key, value ?? setting.value);
            expandedSettingKey = "";
            await loadSettings();
            await refreshState();
        }
        catch (e) { error = e instanceof Error ? e.message : "无法保存音乐设置"; }
    }
    function formatTime(ms: number) { const seconds = Math.max(0, Math.floor(ms / 1000)); return `${Math.floor(seconds / 60).toString().padStart(2, "0")}:${(seconds % 60).toString().padStart(2, "0")}`; }
    function labelFor(setting: RikkaMusicSetting) { return settingLabels[setting.key] ?? setting.key; }
</script>

<svelte:window on:pointermove={moveDrag} on:pointerup={finishDrag} on:blur={finishDrag} />
<div class="rikkamusic-stage">
    <div class="rikkamusic-scale" style={`position: absolute; left: ${windowX}px; top: ${windowY}px; width: ${1320 * viewportScale}px; height: ${960 * viewportScale}px;`}>
    <div class:light={state.theme === "Light"} class="rikkamusic-shell" style={`transform: scale(${viewportScale});`}>
        <header class="top-header">
            <button class="logo-section drag-handle" on:pointerdown={startDrag} on:dblclick={centerWindow} title="拖动窗口，双击居中"><span class="logo-icon">♪</span><span class="logo-title">RikkaMusic</span></button>
            <div class="search-section"><button class="nav-btn" on:click={() => view = "liked"} aria-label="返回音乐库">‹</button><div class="search-bar"><span>⌕</span><input lang="zh-CN" inputmode="text" autocomplete="off" spellcheck="false" bind:value={search} on:keydown={(event) => event.key === "Enter" && runSearch()} placeholder="搜索歌手、歌曲、歌单" aria-label="搜索歌曲、歌手、歌单" />{#if search}<button on:click={() => { search = ""; view = "liked"; }} aria-label="清除搜索">×</button>{/if}</div></div>
            <div class="user-section"><button class="account-button" on:click={openLogin}><span class="avatar">{#if library?.avatar}<img src={library.avatar} alt="用户头像" referrerpolicy="no-referrer" />{:else}♙{/if}</span><span>{library?.authenticated ? library.username : "登录"}</span>{#if library?.vip}<span class="badge">VIP</span>{/if}</button><div class="window-controls"><button on:click={openSettings} title="设置" aria-label="设置">⚙</button><button on:click={toggleTheme} title="切换主题" aria-label="切换主题">◐</button><button on:click={deleteScreen} title="关闭" aria-label="关闭">×</button></div></div>
        </header>

        <div class="main-body">
            <aside class="sidebar"><div class="menu-group"><div class="menu-title">发现</div><button class="menu-item" on:click={() => openCollection("daily")}>每日推荐</button><button class="menu-item" on:click={() => openCollection("fm")}>个人 FM</button><button class="menu-item" on:click={() => openCollection("recommend")}>推荐歌单</button><div class="menu-title">我的</div><button class="menu-item" on:click={() => openCollection("playlists", library?.userId ?? 0)}>全部歌单</button><button class="menu-item" on:click={() => openCollection("podcasts")}>收藏的播客</button><button class:active={view === "liked" && selected?.id === library?.liked?.id} class="menu-item" on:click={() => library?.liked && openPlaylist(library.liked)}><span>♥</span>我喜欢的音乐</button><button class:active={view === "cloud"} class="menu-item" on:click={openCloud}><span>☁</span>我的音乐云盘</button><button class:active={view === "local"} class="menu-item" on:click={() => { view = "local"; void refreshLocal(); }}><span>▣</span>本地挂载歌单</button></div>{#if (library?.playlists?.length ?? 0) > 0}<div class="menu-group playlist-group"><div class="menu-title">我的歌单与收藏</div>{#each library?.playlists ?? [] as playlist (playlist.id)}<button class:active={selected?.id === playlist.id && view === "liked"} class="menu-item playlist-item" on:click={() => openPlaylist(playlist)}><span>♫</span>{playlist.name}</button>{/each}</div>{/if}</aside>
            <main class="content-area">
                {#if error}<div class="notice"><span>{error}</span><button on:click={() => error = ""} aria-label="关闭提示">×</button></div>{/if}
                {#if loading}<div class="placeholder loading"><span class="spinner"></span>正在加载音乐库</div>
                {:else if !library?.authenticated && view !== "settings" && view !== "local" && view !== "search"}<section class="login-empty"><div class="cover-art">♪</div><h2>登录网易云音乐</h2><p>登录后即可同步我喜欢的音乐和已创建的歌单。</p><button class="btn btn-primary" on:click={openLogin}>登录账号</button></section>
                {:else if view === "playlists"}<section class="view-page"><h2>我的歌单</h2><div class="playlist-grid">{#each [library?.liked, ...(library?.playlists ?? [])].filter(Boolean) as playlist}{#if playlist}<button class="playlist-card" on:click={() => openPlaylist(playlist)}><img src={playlist.cover} alt="" /><strong>{playlist.name}</strong><small>{playlist.owned === false ? "收藏" : "创建"} · {playlist.count} 首</small></button>{/if}{:else}<p>暂无歌单</p>{/each}</div></section>
                {:else if view === "collection"}<section class="view-page">{#if collectionLoading}<div class="placeholder"><span class="spinner"></span>正在加载</div>{:else if collection}<div class="collection-heading">{#if collection.cover}<img src={collection.cover} alt="" />{/if}<div><h2>{collection.name}</h2><p>{collection.description ?? ""}</p></div></div><div class="collection-actions">{#if collection.songs.length}<button class="btn btn-primary" on:click={() => playPlaylist(0)}>播放全部</button>{/if}{#if collection.kind === "fm"}<button class="btn btn-secondary" on:click={() => openCollection("fm")}>换一批</button>{/if}{#if collection.artistId}<button class="btn btn-secondary" on:click={() => collection?.artistId && openArtist(collection.artistId)}>音乐人主页</button>{/if}</div><div class="playlist-grid">{#each collection.items as item (item.id)}<button class="playlist-card" on:click={() => collection?.kind === "podcasts" ? openCollection("podcast", item.id) : openPlaylist(item)}><img src={item.cover} alt="" /><strong>{item.name}</strong><small>{item.owned === false ? "收藏 · " : item.owned === true ? "创建 · " : ""}{item.count} {collection.kind === "podcasts" ? "期节目" : "首歌曲"}</small></button>{/each}</div><div class="song-table">{#each collection.songs as song, index}<button class="song-row" class:playing={state.song?.id === song.id} on:click={() => playPlaylist(index)}><span>{index + 1}</span><span class="song-title">{song.name}</span><span>{song.artist}</span><span>{song.album}</span><span>{formatTime(song.duration)}</span></button>{/each}</div>{#if !collection.items.length && !collection.songs.length}<p class="empty-cell">暂无内容</p>{/if}<div class="search-pages">{#if collection.page > 1}<button on:click={() => collection && openCollection(collection.kind, collectionId, collection.page - 1)}>上一页</button>{/if}{#if collection.hasMore}<button on:click={() => collection && openCollection(collection.kind, collectionId, collection.page + 1)}>下一页</button>{/if}</div>{/if}</section>
                {:else if view === "cloud"}<section class="view-page"><h2>我的音乐云盘</h2><div class="cloud-bar"><span>{cloudLoading ? "正在读取云盘歌曲" : `${cloudSongs.length} 首歌曲`}</span></div>{#if cloudLoading}<div class="placeholder loading"><span class="spinner"></span>正在加载云盘</div>{:else}<div class="song-table"><div class="song-row song-head"><span>#</span><span>标题</span><span>歌手</span><span>专辑</span><span>时长</span></div>{#each songs as song, index (song.id)}<button class:playing={state.song?.id === song.id} class="song-row" on:click={() => playPlaylist(index)}><span>{state.song?.id === song.id ? "▶" : index + 1}</span><span class="song-title">{song.name}</span><span>{song.artist || "未知歌手"}</span><span>{song.album || "未知专辑"}</span><span>{formatTime(song.duration)}</span></button>{:else}<div class="empty-cell">云盘暂无歌曲</div>{/each}</div>{/if}</section>
                {:else if view === "local"}<section class="view-page"><h2>挂载本地音乐歌单</h2><div class="mount-panel"><div class="mount-options"><span>本地挂载目录</span><input aria-label="本地挂载目录" value={localPath || "未配置"} readonly /><button class="btn btn-secondary" on:click={openLocal}>选择本地文件夹</button></div></div><div class="mount-result"><div class="result-header"><h3>本地解析出的歌曲列表 ({localSongs.length})</h3><button class="btn btn-secondary" disabled={!localSongs.length} on:click={() => playPlaylist(0)}>播放本地全部</button></div><div class="song-table">{#each localSongs as song, index (song.id)}<button class="song-row" on:click={() => playPlaylist(index)}><span>{index + 1}</span><span class="song-title">{song.name}</span><span>{song.artist || "本地文件"}</span><span>{song.album || ""}</span><span>{formatTime(song.duration)}</span></button>{:else}<div class="empty-cell">未挂载文件夹或未读取到音频文件</div>{/each}</div></div></section>
                {:else if view === "settings"}<section class="settings-page"><h2>设置</h2><div class="setting-tabs">{#each Object.entries(categoryLabels) as [category, label]}<button class:active={settingsCategory === category} on:click={() => selectSettingsCategory(category as SettingsCategory)}>{label}</button>{/each}</div>{#if settingsLoading}<div class="placeholder loading"><span class="spinner"></span>正在读取设置</div>{:else}<div class="setting-group"><h3>{categoryLabels[settingsCategory]}设置</h3>{#if settingsCategory === "account"}<p class="settings-note">账号 Cookie 不会显示在网页中。请通过右上角的账号入口登录或退出。密码和验证码不会保存。</p>{/if}{#each settings as setting (setting.key)}<div class="setting-item"><span class="setting-label">{labelFor(setting)}{#if setting.hotkey}<small>快捷键 {keyLabel(setting.hotkey)}</small>{/if}</span>{#if setting.type === "hotkey"}<div class="hotkey-control"><button class="btn btn-secondary" on:click={() => { recordingKey = setting.key; recordedKeys = []; }} on:keydown={captureKey} on:keyup={finishKey}>{recordingKey === setting.key ? (recordedKeys.length ? keyLabel(recordedKeys.join(",")) : "按下组合键，松开保存") : keyLabel(String(setting.value))}</button><button class="btn btn-secondary" on:click={() => saveSetting(setting, "")}>清除</button></div>{:else if setting.type === "boolean"}<input aria-label={labelFor(setting)} class="switch" type="checkbox" checked={Boolean(setting.value)} on:change={(event) => saveSetting(setting, event.currentTarget.checked)} />{:else if setting.type === "option"}<div class="setting-select" class:expanded={expandedSettingKey === setting.key}><button type="button" class="select-like" aria-haspopup="listbox" aria-expanded={expandedSettingKey === setting.key} on:click={() => expandedSettingKey = expandedSettingKey === setting.key ? "" : setting.key}>{String(setting.value)}<span class="select-arrow">⌄</span></button>{#if expandedSettingKey === setting.key}<div class="select-options" role="listbox">{#each setting.options ?? [String(setting.value)] as option}<button type="button" role="option" aria-selected={option === String(setting.value)} class:active={option === String(setting.value)} on:click={() => saveSetting(setting, option)}>{option}</button>{/each}</div>{/if}</div>{:else if (setting.type === "integer" || setting.type === "double") && setting.min !== undefined && setting.max !== undefined}<div class="range-control"><input aria-label={labelFor(setting)} type="range" min={setting.min} max={setting.max} step={setting.type === "double" ? "0.01" : "1"} value={setting.value} on:change={(event) => saveSetting(setting, Number(event.currentTarget.value))}/><output>{setting.value}</output></div>{:else if setting.type === "color"}<div class="color-control"><input aria-label={labelFor(setting)} type="color" value={String(setting.value).slice(0, 7)} on:change={(event) => saveSetting(setting, event.currentTarget.value.replace("#", "#FF"))}/><input aria-label={`${labelFor(setting)} 色值`} value={setting.value} on:change={(event) => saveSetting(setting, event.currentTarget.value)} /></div>{:else}<input aria-label={labelFor(setting)} class="value-input" value={setting.value} on:change={(event) => saveSetting(setting, event.currentTarget.value)} />{/if}</div>{:else}<p class="placeholder-text">此分类没有可配置项</p>{/each}</div>{/if}</section>
                {:else}<section class="view-page"><div class="playlist-header"><div class="cover-art"><img src={selected?.cover || ""} alt="" /><span>♥</span></div><div class="playlist-info"><h2>{title}</h2><p class="meta">{selected?.artist || library?.username || "网易云音乐"} · {songs.length} 首歌曲</p><button class="btn btn-primary" disabled={!songs.length} on:click={() => playPlaylist(0)}>▶ 播放全部</button></div></div>{#if view === "search"}<h2 class="search-title">搜索 “{search}”</h2><div class="sub-tabs">{#each ["综合", "单曲", "歌单", "歌手"] as tab}<button class:active={searchTab === tab} class="tab" on:click={() => selectSearchTab(tab as typeof searchTab)}>{tab}</button>{/each}</div><div class="search-pages"><button disabled={searchPage <= 1} on:click={() => runSearch(searchPage - 1)}>上一页</button><span>{searchPage} / {searchPageCount} · {searchTotal}</span><button disabled={searchPage >= searchPageCount} on:click={() => runSearch(searchPage + 1)}>下一页</button></div>{/if}<div class="song-table"><div class="song-row song-head"><span>#</span><span>标题</span><span>歌手</span><span>专辑</span><span>时长</span></div>{#if view === "search" && searchTab === "歌单"}{#each searchPlaylists as playlist, index (playlist.id)}<button class="song-row" on:click={() => openPlaylist(playlist)}><span>{index + 1}</span><span class="song-title">{playlist.name}</span><span>{playlist.artist || ""}</span><span>{playlist.count} 首歌曲</span><span>歌单</span></button>{/each}{:else if view === "search" && searchTab === "歌手"}{#each searchArtists as artist, index (artist.id)}<button class="song-row" on:click={() => openArtist(artist.id)}><span>{index + 1}</span><span class="song-title">{artist.name}</span><span>歌手</span><span>{artist.count ? `${artist.count} 首歌曲` : ""}</span><span>主页 ›</span></button>{:else}<div class="empty-cell">暂无歌曲</div>{/each}{:else}{#each songs as song, index (song.id)}<button class:playing={state.song?.id === song.id} class="song-row" on:click={() => playPlaylist(index)}><span>{state.song?.id === song.id ? "▶" : index + 1}</span><span class="song-title">{song.name}</span><span>{song.artist || "未知歌手"}</span><span>{song.album || "未知专辑"}</span><span>{formatTime(song.duration)}</span></button>{:else}<div class="empty-cell">暂无歌曲</div>{/each}{/if}</div></section>{/if}
            </main>
        </div>

        {#if showLyrics}<section class="lyrics-overlay"><button on:click={() => showLyrics = false} aria-label="关闭歌词">×</button><h3>{state.song?.name || "未播放"}</h3><p>{state.lyric || "♪"}</p><small>{state.translation || ""}</small></section>{/if}
        <footer class="player-bar"><div class="track-info"><div class="small-cover"><img src={state.song?.cover || ""} alt="" /></div><div class="track-text"><strong>{state.song?.name ?? "未选择播放歌曲"}</strong><small>{state.song?.artist ?? "--"}</small></div></div><div class="player-center"><div class="player-controls"><button on:click={() => control("shuffle")} title="随机播放" aria-label="随机播放">⌘</button><button on:click={() => control("previous")} title="上一首" aria-label="上一首">◀◀</button><button class="play-btn" on:click={() => control("toggle")} aria-label="播放或暂停">{state.playing ? "Ⅱ" : "▶"}</button><button on:click={() => control("next")} title="下一首" aria-label="下一首">▶▶</button><button on:click={() => showLyrics = !showLyrics} title="歌词" aria-label="歌词">♬</button></div><div class="progress-container"><span>{formatTime(state.progress)}</span><input type="range" min="0" max={state.song?.duration ?? 1} value={state.progress} on:change={(event) => control("seek", Number(event.currentTarget.value))} /><span>{formatTime(state.song?.duration ?? 0)}</span></div></div><div class="extra-controls"><button class="quality-tag" on:click={cycleQuality} title="切换播放音质">{qualityLabel(state.quality)}</button><div class="volume-container"><span>🔊</span><input type="range" min="0" max="100" value={state.volume} on:change={(event) => control("volume", Number(event.currentTarget.value))} /></div></div></footer>
        {#if showLogin}<div class="dialog-backdrop"><section class="login-dialog"><button class="dialog-close" on:click={closeLogin} aria-label="关闭账号窗口">×</button>
            {#if library?.authenticated}<img class="profile-avatar" src={library.avatar || ""} alt="用户头像" /><h2>{library.username}</h2><p>已登录网易云音乐{library.vip ? " · VIP" : ""}</p><div class="account-actions"><button class="btn btn-primary" disabled={!library.userId} on:click={openProfile}>个人主页</button>{#if library.artistId}<button class="btn btn-secondary" on:click={() => library?.artistId && openArtist(library.artistId)}>音乐人主页</button>{/if}<button class="btn btn-secondary" disabled={accountBusy} on:click={logout}>退出账号</button></div>
            {:else}<div class="dialog-logo">♪</div><h2>登录网易云音乐</h2><div class="login-tabs"><button class:active={loginMethod === "password"} on:click={() => chooseLoginMethod("password")}>账号密码</button><button class:active={loginMethod === "captcha"} on:click={() => chooseLoginMethod("captcha")}>验证码</button><button class:active={loginMethod === "qr"} on:click={() => chooseLoginMethod("qr")}>扫码</button></div>
            {#if loginMethod === "qr"}<div class="qr-wrap">{#if qrCodeUrl}<img src={qrCodeUrl} alt="网易云音乐登录二维码" />{:else}<span class="spinner"></span>{/if}</div><button class="btn btn-secondary" on:click={() => chooseLoginMethod("qr")}>刷新二维码</button>
            {:else}<form class="login-form" on:submit|preventDefault={submitLogin}><label>{loginMethod === "password" ? "手机号或邮箱" : "手机号"}<input autocomplete="username" bind:value={loginAccount} required /></label>{#if !loginAccount.includes("@")}<label>国家码<input type="number" min="1" max="999" bind:value={countryCode} required /></label>{/if}<label>{loginMethod === "password" ? "密码" : "验证码"}<input type={loginMethod === "password" ? "password" : "text"} autocomplete={loginMethod === "password" ? "current-password" : "one-time-code"} bind:value={loginSecret} required /></label>{#if loginMethod === "captcha"}<button type="button" class="btn btn-secondary" disabled={accountBusy || captchaRemaining > 0 || !loginAccount.trim()} on:click={sendCaptcha}>{captchaRemaining > 0 ? `${captchaRemaining} 秒后重试` : "发送验证码"}</button>{/if}<button class="btn btn-primary" disabled={accountBusy || login.status === "loading"} type="submit">登录</button></form>{/if}{/if}<p class:error-text={login.status === "error"}>{login.message}</p>
        </section></div>{/if}
    </div>
    </div>
</div>

<style lang="scss">
    .rikkamusic-stage { position: absolute; inset: 0; display: grid; place-items: center; background: rgba(11, 14, 20, .34); font-family: Arial, sans-serif; }
    .rikkamusic-shell { --bg: #191b20; --panel: #23262d; --sidebar: #202329; --line: #393d46; --text: #e9eaed; --dim: #aeb3bd; --muted: #777d89; --accent: #ec4141; --hover: #2d313a; width: min(1000px, calc(100vw - 36px)); height: min(680px, calc(100vh - 36px)); min-height: 500px; display: flex; flex-direction: column; overflow: hidden; color: var(--text); background: var(--bg); border: 1px solid rgba(255, 255, 255, .14); border-radius: 7px; box-shadow: 0 24px 68px rgba(0, 0, 0, .44); font-size: 12px; }
    .rikkamusic-shell.light { --bg: #fff; --panel: #f9f9f9; --sidebar: #f9f9f9; --line: #e5e5e5; --text: #333; --dim: #666; --muted: #999; --hover: #f3f3f3; box-shadow: 0 24px 68px rgba(0, 0, 0, .26); }
    button, input { font: inherit; } button { border: 0; color: inherit; cursor: pointer; } button:disabled { cursor: default; opacity: .52; }
    .top-header { height: 54px; flex: none; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; background: var(--panel); border-bottom: 1px solid var(--line); }.logo-section, .search-section, .user-section, .account-button, .window-controls, .search-bar, .track-info, .player-controls, .progress-container, .extra-controls, .volume-container { display: flex; align-items: center; }.logo-section { width: 160px; gap: 8px; }.logo-icon { width: 24px; height: 24px; display: grid; place-items: center; color: #fff; background: var(--accent); border-radius: 50%; font-size: 12px; }.logo-title { font-size: 14px; font-weight: 700; }.search-section { gap: 10px; }.nav-btn { width: 24px; height: 24px; border-radius: 50%; background: var(--hover); color: var(--dim); font-size: 20px; line-height: 20px; }.search-bar { width: 210px; gap: 8px; padding: 4px 11px; color: var(--muted); background: var(--hover); border-radius: 15px; }.search-bar input { width: 100%; min-width: 0; padding: 0; color: var(--text); background: transparent; border: 0; outline: 0; }.search-bar button { padding: 0; color: var(--muted); background: transparent; font-size: 15px; }.user-section { width: 240px; justify-content: flex-end; gap: 11px; color: var(--dim); }.account-button { gap: 7px; padding: 0; background: transparent; white-space: nowrap; }.account-button:hover { color: var(--text); }.avatar { width: 26px; height: 26px; display: grid; place-items: center; color: #fff; background: #bbb; border-radius: 50%; }.badge { padding: 1px 4px; color: #fff; background: #e6a23c; border-radius: 4px; font-size: 9px; }.window-controls { gap: 8px; }.window-controls button { padding: 3px; color: var(--muted); background: transparent; font-size: 15px; }.window-controls button:hover { color: var(--accent); }
    .main-body { flex: 1; display: flex; min-height: 0; overflow: hidden; }.sidebar { width: 190px; flex: none; padding: 12px 8px; overflow-y: auto; background: var(--sidebar); border-right: 1px solid var(--line); }.menu-title { padding: 4px 8px; color: var(--muted); font-size: 11px; }.menu-group { margin-bottom: 14px; }.playlist-group { margin-top: 16px; }.menu-item { width: 100%; display: flex; align-items: center; gap: 10px; padding: 8px 12px; margin: 2px 0; overflow: hidden; color: var(--dim); background: transparent; border-radius: 6px; text-align: left; white-space: nowrap; text-overflow: ellipsis; }.menu-item span { width: 13px; text-align: center; }.menu-item:hover { color: var(--text); background: var(--hover); }.menu-item.active { color: #fff; background: var(--accent); font-weight: 700; }
    .content-area { flex: 1; min-width: 0; padding: 20px 24px; overflow: auto; background: var(--bg); }.view-page, .settings-page { max-width: 744px; margin: 0 auto; }.view-page h2, .settings-page h2 { margin: 0 0 15px; font-size: 20px; }.playlist-header { display: flex; gap: 16px; margin-bottom: 20px; }.cover-art { position: relative; width: 110px; height: 110px; flex: none; display: grid; place-items: center; overflow: hidden; color: #fff; background: linear-gradient(135deg, #ff7575, var(--accent)); border-radius: 8px; font-size: 36px; }.cover-art img { width: 100%; height: 100%; object-fit: cover; }.cover-art span { position: absolute; }.playlist-info { padding-top: 5px; }.playlist-info h2 { margin: 0 0 6px; }.meta, .placeholder-text, .settings-note { margin: 0 0 14px; color: var(--muted); }.btn { display: inline-flex; align-items: center; justify-content: center; padding: 7px 16px; border-radius: 16px; }.btn-primary { color: #fff; background: var(--accent); }.btn-primary:hover:not(:disabled) { background: #d93737; }.btn-secondary { color: var(--text); background: var(--hover); }
    .song-table { width: 100%; }.song-row { width: 100%; display: grid; grid-template-columns: 42px minmax(140px, 2fr) minmax(100px, 1fr) minmax(100px, 1fr) 55px; gap: 8px; align-items: center; padding: 10px 8px; color: var(--dim); background: transparent; border-bottom: 1px solid var(--line); text-align: left; }.song-row:not(.song-head):hover { color: var(--text); background: var(--hover); }.song-row.playing { color: var(--accent); background: rgba(236, 65, 65, .08); }.song-head { color: var(--muted); }.song-title { overflow: hidden; color: var(--text); text-overflow: ellipsis; white-space: nowrap; }.playing .song-title { color: var(--accent); }.empty-cell { padding: 30px 8px; color: var(--muted); text-align: center; }.sub-tabs, .setting-tabs { display: flex; gap: 20px; margin: 0 0 16px; border-bottom: 1px solid var(--line); }.tab, .setting-tabs button { padding: 0 0 8px; color: var(--dim); background: transparent; }.tab.active, .setting-tabs button.active { color: var(--accent); border-bottom: 2px solid var(--accent); font-weight: 700; }.search-pages { display: flex; align-items: center; justify-content: center; gap: 12px; min-height: 32px; margin: -4px 0 16px; color: var(--muted); }.search-pages button { min-width: 64px; padding: 6px 10px; color: var(--dim); background: var(--hover); border: 1px solid var(--line); border-radius: 5px; line-height: 1; }.search-pages button:hover:not(:disabled) { color: var(--text); border-color: var(--accent); background: color-mix(in srgb, var(--hover) 70%, var(--accent)); }.search-pages button:disabled { color: var(--muted); background: transparent; }.search-pages span { min-width: 96px; text-align: center; white-space: nowrap; }.cloud-bar, .mount-options, .result-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 15px; color: var(--dim); }.mount-panel { padding: 16px; margin-bottom: 15px; border: 1px dashed var(--line); border-radius: 8px; }.mount-options input { min-width: 0; padding: 5px 8px; color: var(--dim); background: var(--hover); border: 1px solid var(--line); border-radius: 4px; }.result-header h3 { margin: 0; font-size: 14px; }.notice { display: flex; justify-content: space-between; gap: 16px; padding: 8px 10px; margin: 0 auto 14px; color: #d62e2e; background: rgba(236, 65, 65, .12); border: 1px solid rgba(236, 65, 65, .36); border-radius: 4px; }.notice button { color: inherit; background: transparent; }.placeholder, .login-empty { min-height: 260px; display: grid; place-content: center; justify-items: center; gap: 10px; color: var(--muted); text-align: center; }.login-empty h2 { margin: 0; color: var(--text); }.login-empty p { max-width: 320px; margin: 0 0 8px; }.spinner { width: 20px; height: 20px; display: inline-block; border: 2px solid var(--line); border-top-color: var(--accent); border-radius: 50%; animation: spin .7s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
    .settings-page { max-width: 690px; }.setting-group h3 { padding-left: 8px; margin: 0 0 8px; border-left: 3px solid var(--accent); font-size: 14px; }.settings-note { font-size: 11px; }.setting-item { display: flex; align-items: center; justify-content: space-between; gap: 20px; min-height: 38px; padding: 7px 0; border-bottom: 1px solid var(--line); }.setting-label { color: var(--text); }.setting-item small { display: block; margin-top: 3px; color: var(--muted); font-size: 10px; }.switch { width: 16px; height: 16px; accent-color: var(--accent); }.select-like, .value-input { min-width: 142px; padding: 5px 9px; color: var(--dim); background: var(--hover); border: 1px solid var(--line); border-radius: 5px; text-align: right; }.select-like { display: flex; justify-content: space-between; gap: 14px; }.range-control { display: flex; align-items: center; gap: 8px; width: 210px; }.range-control input { flex: 1; accent-color: var(--accent); }.range-control output { width: 38px; color: var(--dim); text-align: right; }.color-control { display: flex; align-items: center; gap: 7px; }.color-control input[type="color"] { width: 24px; height: 24px; padding: 0; background: transparent; border: 0; }.color-control input:last-child { width: 92px; padding: 5px; color: var(--dim); background: var(--hover); border: 1px solid var(--line); border-radius: 4px; }
    .setting-select { position: relative; min-width: 142px; }.setting-select .select-like { width: 100%; border: 1px solid var(--line); cursor: pointer; }.select-arrow { margin-left: 10px; color: var(--muted); }.select-options { position: absolute; z-index: 20; top: calc(100% + 3px); right: 0; left: 0; max-height: 220px; overflow-y: auto; padding: 4px; background: var(--panel); border: 1px solid var(--line); border-radius: 5px; box-shadow: 0 8px 22px rgba(0, 0, 0, .35); }.select-options button { width: 100%; padding: 7px 9px; color: var(--dim); background: transparent; border-radius: 3px; text-align: right; cursor: pointer; }.select-options button:hover, .select-options button.active { color: var(--text); background: var(--hover); }
    .player-bar { height: 68px; flex: none; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; background: var(--panel); border-top: 1px solid var(--line); }.track-info { width: 200px; gap: 10px; min-width: 0; }.small-cover { width: 44px; height: 44px; flex: none; overflow: hidden; background: var(--hover); border-radius: 4px; }.small-cover img { width: 100%; height: 100%; object-fit: cover; }.track-text { min-width: 0; }.track-text strong, .track-text small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.track-text strong { color: var(--text); font-size: 13px; }.track-text small { margin-top: 2px; color: var(--muted); font-size: 11px; }.player-center { width: min(420px, 42vw); }.player-controls { justify-content: center; gap: 20px; margin-bottom: 6px; }.player-controls button { padding: 0; color: var(--text); background: transparent; }.play-btn { width: 32px; height: 32px; color: #fff !important; background: var(--accent) !important; border-radius: 50%; }.progress-container { width: 100%; gap: 8px; color: var(--muted); font-size: 11px; }.progress-container input, .volume-container input { flex: 1; accent-color: var(--accent); }.extra-controls { width: 180px; justify-content: flex-end; gap: 14px; color: var(--dim); }.quality-tag { padding: 0 2px; color: var(--accent); border: 1px solid var(--accent); border-radius: 2px; font-size: 9px; }.volume-container { gap: 6px; }.volume-container input { width: 80px; }
    .dialog-backdrop { position: absolute; inset: 0; display: grid; place-items: center; background: rgba(0, 0, 0, .48); }.login-dialog { position: relative; width: 300px; padding: 24px; color: var(--text); background: var(--panel); border: 1px solid var(--line); border-radius: 7px; text-align: center; }.dialog-close { position: absolute; top: 8px; right: 10px; padding: 3px; color: var(--muted); background: transparent; font-size: 17px; }.dialog-logo { width: 36px; height: 36px; display: grid; place-items: center; margin: 0 auto 8px; color: #fff; background: var(--accent); border-radius: 50%; }.login-dialog h2 { margin: 0 0 8px; font-size: 17px; }.login-dialog p { min-height: 18px; margin: 0 0 14px; color: var(--muted); }.qr-wrap { width: 176px; height: 176px; display: grid; place-items: center; margin: 0 auto 16px; background: #fff; border-radius: 4px; }.qr-wrap img { width: 160px; height: 160px; image-rendering: pixelated; }
    /* The RikkaMusic window follows the LiquidBounce blue UI palette without changing global theme controls. */
    .rikkamusic-shell { --bg: #11151c; --panel: #1a202b; --sidebar: #161c26; --line: #2c3544; --text: #f4f7ff; --dim: #aab7ca; --muted: #738198; --accent: #4677ff; --hover: #242e3d; width: min(1320px, calc(100vw - 48px)); height: min(960px, calc(100vh - 48px)); min-height: 620px; font-size: 14px; }
    .rikkamusic-shell.light { --bg: #f6f8ff; --panel: #fff; --sidebar: #f8faff; --line: #d9e1f2; --text: #1d2a43; --dim: #54627a; --muted: #8290a8; --accent: #4677ff; --hover: #edf2ff; }
    .cover-art { background: linear-gradient(135deg, #74a0ff, var(--accent)); }
    .btn-primary:hover:not(:disabled) { background: #365fdb; }
    .song-row.playing { background: rgba(70, 119, 255, .10); }
    .notice { color: #9dc0ff; background: rgba(70, 119, 255, .12); border-color: rgba(70, 119, 255, .36); }
    .rikkamusic-scale { position: relative; flex: none; }
    .rikkamusic-shell { width: 1320px; height: 960px; min-height: 0; transform-origin: top left; font-size: 16px; }
    .rikkamusic-shell { font-size: 17px; }
    .btn { min-height: 38px; padding: 8px 18px; font-size: 15px; }
    .search-pages { gap: 16px; min-height: 38px; margin: 0 0 18px; font-size: 15px; }
    .search-pages button { min-width: 78px; min-height: 34px; padding: 7px 12px; border-radius: 6px; font-size: 14px; }
    .search-pages span { min-width: 118px; }
    .quality-tag { min-width: 54px; min-height: 30px; padding: 5px 8px; color: #c8d8ff; border-color: #6f98ff; border-radius: 5px; background: rgba(70, 119, 255, .16); font-size: 13px; font-weight: 700; line-height: 1; }
    .quality-tag:hover { color: #fff; border-color: #9db8ff; background: rgba(70, 119, 255, .34); }
    .volume-container input { width: 96px; }
    .setting-item { min-height: 46px; }
    .setting-item small, .settings-note, .menu-title, .track-text small, .progress-container { font-size: 12px; }
    .tab { cursor: pointer; }
    .search-bar input { ime-mode: active; }
    .window-controls button { width: 32px; height: 32px; font-size: 20px; }
    .player-bar { height: 82px; padding: 0 20px; }
    .small-cover { width: 54px; height: 54px; }
    .track-info { width: 220px; gap: 12px; }
    .track-text strong { font-size: 15px; }
    .track-text small { font-size: 13px; }
    .player-center { width: min(470px, 44vw); }
    .player-controls { gap: 24px; margin-bottom: 8px; }
    .player-controls button { min-width: 28px; min-height: 28px; font-size: 18px; }
    .play-btn { width: 38px; height: 38px; }
    .progress-container { font-size: 13px; }
    .extra-controls { width: 210px; gap: 16px; }
    .quality-tag { min-width: 62px; min-height: 34px; padding: 6px 9px; border-radius: 5px; font-size: 13px; }
    .volume-container { gap: 8px; font-size: 17px; }
    .volume-container input { width: 100px; }

    .drag-handle { background: transparent; cursor: move; touch-action: none; }
    .avatar img { width: 100%; height: 100%; object-fit: cover; border-radius: 50%; }
    .playlist-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 18px; margin: 20px 0; }
    .playlist-card { display: flex; flex-direction: column; gap: 8px; padding: 12px; text-align: left; background: var(--hover); border-radius: 8px; }
    .playlist-card img { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 6px; }
    .playlist-card strong { line-height: 1.4; }.playlist-card small { color: var(--muted); }
    .collection-heading { display: flex; gap: 20px; align-items: center; }.collection-heading img { width: 100px; height: 100px; object-fit: cover; border-radius: 10px; }.collection-heading p { white-space: pre-wrap; color: var(--muted); }
    .collection-actions, .account-actions, .login-tabs, .hotkey-control { display: flex; gap: 10px; flex-wrap: wrap; }
    .login-dialog { width: 420px; padding: 32px; }.login-tabs { justify-content: center; margin: 20px 0; }.login-tabs button { padding: 10px 15px; background: var(--hover); border-radius: 6px; }.login-tabs button.active { background: var(--accent); color: white; }
    .login-form { display: grid; gap: 16px; text-align: left; }.login-form label { display: grid; gap: 7px; }.login-form input { width: 100%; box-sizing: border-box; padding: 12px; background: var(--hover); color: var(--text); border: 1px solid var(--line); border-radius: 6px; }.login-dialog p { margin-top: 18px; }.profile-avatar { width: 90px; height: 90px; object-fit: cover; border-radius: 50%; margin-bottom: 16px; }.account-actions { justify-content: center; }.error-text { color: #ff8585 !important; }
    .lyrics-overlay { position: absolute; left: 25%; right: 5%; bottom: 100px; padding: 36px; z-index: 25; color: var(--text); background: var(--panel); border: 1px solid var(--line); border-radius: 12px; text-align: center; box-shadow: 0 12px 40px #0006; }.lyrics-overlay button { float: right; background: transparent; }.lyrics-overlay p { font-size: 24px; }.lyrics-overlay small { color: var(--dim); }
</style>
