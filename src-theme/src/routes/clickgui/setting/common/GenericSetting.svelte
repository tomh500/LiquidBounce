<script lang="ts">
    import type {ModuleSetting} from "../../../../integration/types";
    import BooleanSetting from "../BooleanSetting.svelte";
    import ChoiceSetting from "../ChoiceSetting.svelte";
    import ChooseSetting from "../ChooseSetting.svelte";
    import ConfigurableSetting from "../ConfigurableSetting.svelte";
    import FloatRangeSetting from "../FloatRangeSetting.svelte";
    import FloatSetting from "../FloatSetting.svelte";
    import IntRangeSetting from "../IntRangeSetting.svelte";
    import IntSetting from "../IntSetting.svelte";
    import TogglableSetting from "../TogglableSetting.svelte";
    import ColorSetting from "../ColorSetting.svelte";
    import TextSetting from "../TextSetting.svelte";
    import {slide} from "svelte/transition";
    import BindSetting from "../bind/BindSetting.svelte";
    import VectorSetting from "../VectorSetting.svelte";
    import KeySetting from "../KeySetting.svelte";
    import MultiChooseSetting from "../MultiChooseSetting.svelte";
    import FileSetting from "../FileSetting.svelte";
    import MutableListSetting from "../list/MutableListSetting.svelte";
    import ItemListSetting from "../list/ItemListSetting.svelte";
    import RegistryListSetting from "../list/RegistryListSetting.svelte";
    import CurveSetting from "../CurveSetting.svelte";
    import RegistryMutableListSetting from "../list/RegistryMutableListSetting.svelte";
    import {createEventDispatcher} from "svelte";
    import {clientLanguage, settingsText} from "../../settings_i18n";

    export let setting: ModuleSetting;
    export let path: string;
    export let localizeSettings: boolean = false;

    const dispatch = createEventDispatcher();
    let displaySetting = setting;
    $: displaySetting = localizeSettings
        ? {...setting, name: settingsText(setting.name, $clientLanguage)}
        : setting;

    function handleChange() {
        setting = localizeSettings
            ? {...displaySetting, name: setting.name} as ModuleSetting
            : displaySetting;
        dispatch("change");
    }
</script>


<div in:slide|global={{duration: 200, axis: "y"}} out:slide|global={{duration: 200, axis: "y"}}>
    {#if setting.valueType === "BOOLEAN"}
        <BooleanSetting bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "CHOICE"}
        <ChoiceSetting {path} {localizeSettings} bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "FILE"}
        <FileSetting bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "CHOOSE"}
        <ChooseSetting bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "MULTI_CHOOSE"}
        <MultiChooseSetting {path} bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "TOGGLEABLE"}
        <TogglableSetting {path} {localizeSettings} bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "INT"}
        <IntSetting bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "INT_RANGE"}
        <IntRangeSetting bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "FLOAT"}
        <FloatSetting bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "FLOAT_RANGE"}
        <FloatRangeSetting bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "CONFIGURABLE"}
        <ConfigurableSetting {path} {localizeSettings} bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "COLOR"}
        <ColorSetting bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "TEXT"}
        <TextSetting bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "MUTABLE_LIST" }
        <MutableListSetting bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "ITEM_LIST" }
        <ItemListSetting {path} bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "REGISTRY_LIST" }
        <RegistryListSetting {path} bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "REGISTRY_MUTABLE_LIST" }
        <RegistryMutableListSetting {path} bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "BIND"}
        <BindSetting bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "VECTOR3_I" }
        <VectorSetting vecAxes={["x", "y", "z"]} step={1} bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "VECTOR3_D" }
        <VectorSetting vecAxes={["x", "y", "z"]} step={0.01} bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "VECTOR2_F" }
        <VectorSetting vecAxes={["x", "y"]} step={0.01} bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "KEY"}
        <KeySetting bind:setting={displaySetting} on:change={handleChange}/>
    {:else if setting.valueType === "CURVE"}
        <CurveSetting {path} bind:setting={displaySetting} on:change={handleChange}/>
    {:else}
        <div style="color: var(--clickgui-text-color)">Unsupported setting {setting.valueType}</div>
    {/if}
</div>
