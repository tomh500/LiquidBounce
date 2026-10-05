<script lang="ts">
    import {onMount, type Component} from "svelte";
    import Tabs from "../tabs/Tabs.svelte";
    import {clientLanguage, settingsText} from "../settings_i18n";
    import {getClientInfo, getGameWindow, getModuleSettings} from "../../../integration/rest";
    import {listen} from "../../../integration/ws";
    import {scaleFactor} from "../clickgui_store";

    let {tabs, activeTab = $bindable(0)} = $props<{
        tabs: {title: string; content: Component}[];
        activeTab?: number;
    }>();
    let initialized = $state(false);

    // Keep translated labels out of the tab IDs persisted by upstream's TabbedClickGui.
    const label = (tab: {title: string}) => tab.title === "Settings"
        ? settingsText(tab.title, $clientLanguage) : tab.title;

    onMount(() => {
        let disposed = false;
        async function initialize() {
            try {
                const [client, window, settings] = await Promise.all([
                    getClientInfo(), getGameWindow(), getModuleSettings("ClickGUI")
                ]);
                if (disposed) return;
                $clientLanguage = client.language;
                const scale = settings.value.find(value => value.name === "Scale")?.value;
                // Avoid painting the first frame at a provisional scale. Later scale updates
                // remain owned by upstream's window/settings event handlers.
                $scaleFactor = window.scaleFactor * (typeof scale === "number" ? scale : 1);
            } catch (error) {
                console.error("Could not initialize ClickGUI display extensions", error);
            } finally {
                if (!disposed) initialized = true;
            }
        }
        void initialize();
        return () => { disposed = true; };
    });

    listen("clientLanguageChanged", async () => {
        $clientLanguage = (await getClientInfo()).language;
    });
</script>

{#if initialized}
    <Tabs {tabs} {label} bind:activeTab/>
{/if}
