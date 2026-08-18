<script lang="ts">
    import {onMount} from "svelte";
    import type {ConfigurableSetting as ConfigurableSettingData} from "../../../integration/types";
    import {getGlobalSettings, setGlobalSettings} from "../../../integration/rest";
    import ConfigurableSetting from "../setting/ConfigurableSetting.svelte";
    import WindowPanel from "./WindowPanel.svelte";
    import ScaledClickGuiContent from "../ScaledClickGuiContent.svelte";
    import {clientLanguage, settingsText} from "../settings_i18n";

    let globalSettings = $state<ConfigurableSettingData | null>(null);

    const settingEntries = $derived(
        globalSettings?.value
            .map((setting, index) => ({setting, index}))
            .filter(({setting}) => setting.valueType === "CONFIGURABLE" || setting.valueType === "TOGGLEABLE") ?? []
    );

    async function fetchGlobalSettings() {
        globalSettings = await getGlobalSettings();
    }

    async function updateGlobalSettings() {
        if (!globalSettings) return;

        await setGlobalSettings($state.snapshot(globalSettings));
        await fetchGlobalSettings();
    }

    onMount(() => {
        fetchGlobalSettings();
    });
</script>

<ScaledClickGuiContent>
    <WindowPanel title={settingsText("Global Settings", $clientLanguage)} icon="client">
        <div
                class="settings-grid"
                style="--setting-rows: {Math.max(1, Math.ceil(settingEntries.length / 2))}"
        >
            {#if globalSettings}
                {#each settingEntries as entry (entry.setting.name)}
                    <div class="setting-item">
                        <ConfigurableSetting
                                path="clickgui.global"
                                bind:setting={globalSettings.value[entry.index]}
                                hideExpandControl={true}
                                localizeSettings={true}
                                on:change={updateGlobalSettings}
                        />
                    </div>
                {/each}
            {/if}
        </div>
    </WindowPanel>
</ScaledClickGuiContent>

<style lang="scss">
  .settings-grid {
    position: relative;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    grid-template-rows: repeat(var(--setting-rows), max-content);
    grid-auto-flow: column;
    column-gap: 25px;
    overflow: visible;
    padding-bottom: 32px;

    &::before {
      content: "";
      position: absolute;
      top: 0;
      bottom: 32px;
      left: 50%;
      width: 1px;
      background-color: var(--clickgui-global-settings-divider-color);
      pointer-events: none;
    }
  }

  @media (max-width: 900px) {
    .settings-grid {
      grid-template-columns: minmax(0, 1fr);
      grid-template-rows: none;
      grid-auto-flow: row;

      &::before {
        display: none;
      }
    }
  }

  .setting-item {
    break-inside: avoid;
    display: inline-block;
    width: 100%;
    margin-bottom: 15px;
  }
</style>
