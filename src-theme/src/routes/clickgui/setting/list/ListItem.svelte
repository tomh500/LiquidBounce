<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import {whenItemTexturesReady} from "../../../../integration/item_texture_ready";

    const dispatch = createEventDispatcher<{
        toggle: { value: string, enabled: boolean }
    }>();

    export let value: string;
    export let name: string;
    export let icon: string | undefined;
    export let enabled: boolean;
    // NOTE: It would be better if enabled state handling was performed by a wrapper element.
    export let showEnabledState = true;
    export let pointerCursor = true;

    let failedIcon = false;
    let retriedAfterReady = false;
    let previousIcon: string | undefined;
    $: if (icon !== previousIcon) {
        previousIcon = icon;
        failedIcon = false;
        retriedAfterReady = false;
    }

    async function handleIconError() {
        const failedFor = icon;
        failedIcon = true;
        if (retriedAfterReady) return;
        retriedAfterReady = true;
        await whenItemTexturesReady();
        if (icon === failedFor) failedIcon = false;
    }
</script>

<!-- svelte-ignore a11y-no-static-element-interactions -->
<!-- svelte-ignore a11y-click-events-have-key-events -->
<div class="item" class:has-icon={icon !== undefined} class:has-enabled-state={showEnabledState}
     class:pointer-cursor={pointerCursor} on:click={() => dispatch("toggle", {enabled: !enabled, value: value})}>
    {#if icon}
        {#if failedIcon}
            <span class="icon unavailable" aria-hidden="true">▣</span>
        {:else}
            <img class="icon" src={icon} alt={value} on:error={handleIconError}/>
        {/if}
    {/if}
    <div class="name">{name}</div>
    {#if showEnabledState}
        <div class="tick">
            {#if enabled}
                <img src="img/clickgui/icon-tick-checked.svg" alt="enabled">
            {:else}
                <img src="img/clickgui/icon-tick.svg" alt="disabled">
            {/if}
        </div>
    {/if}
</div>

<style lang="scss">

  .item {
    display: grid;
    grid-template-columns: 1fr;
    align-items: center;
    column-gap: 5px;
    margin: 2px 5px 2px 0;

    &.pointer-cursor {
      cursor: pointer;
    }

    &.has-icon:not(.has-enabled-state) {
      grid-template-columns: max-content 1fr;
    }

    &.has-icon.has-enabled-state {
      grid-template-columns: max-content 1fr max-content;
    }

    &:not(.has-icon).has-enabled-state {
      grid-template-columns: 1fr max-content;
    }
  }

  .icon {
    height: 25px;
    width: 25px;
  }

  .unavailable {
    display: grid;
    place-items: center;
    color: var(--clickgui-text-color);
    opacity: .5;
  }

  .name {
    font-size: 12px;
    color: var(--clickgui-text-color);
    text-overflow: ellipsis;
    white-space: nowrap;
    overflow: hidden;
  }
</style>
