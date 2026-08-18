<script lang="ts">
    import type {Snippet} from "svelte";
    import {fade} from "svelte/transition";
    import {quintOut} from "svelte/easing";

    let { title, icon, children } = $props<{
        title: string;
        icon?: string;
        children: Snippet;
    }>();
</script>

<div class="window" transition:fade|global={{duration: 200, easing: quintOut}}>
    <div class="title">
        {#if icon}
            <img
                    class="icon"
                    src="img/clickgui/icon-{icon}.svg"
                    alt="icon"
            />
        {/if}
        <span class="title-text">{title}</span>
    </div>
    <div class="content">
        {@render children()}
    </div>
</div>

<style lang="scss">

  .window {
    position: fixed;
    top: 70px;
    left: 50%;
    transform: translateX(-50%);
    width: min(820px, 92vw);
    height: min(70vh, calc(100vh - 90px));
    display: flex;
    flex-direction: column;
    background-color: var(--clickgui-window-background-color);
    border-radius: 5px;
    overflow: hidden;
    box-shadow: 0 0 10px var(--clickgui-window-shadow-color);
    user-select: none;
  }

  .title {
    flex: 0 0 auto;
    display: grid;
    grid-template-columns: max-content 1fr;
    align-items: center;
    column-gap: 12px;
    background-color: var(--clickgui-window-header-background-color);
    padding: 16px 22px;
    font-size: 16px;
    font-weight: 600;
    color: var(--clickgui-text-color);
    border-bottom: 2px solid var(--clickgui-window-header-border-color);
  }

  .title-text {
    font-weight: 600;
  }

  .content {
    flex: 1 1 0;
    height: 0;
    min-height: 0;
    box-sizing: border-box;
    padding: 12px 22px 240px;
    overflow-y: auto;
    overscroll-behavior: contain;
  }
</style>
