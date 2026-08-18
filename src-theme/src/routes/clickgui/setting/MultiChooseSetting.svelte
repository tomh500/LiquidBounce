<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import type {ModuleSetting, MultiChooseSetting,} from "../../../integration/types";
    import {slide} from "svelte/transition";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";
    import ExpandArrow from "./common/ExpandArrow.svelte";
    import {setItem} from "../../../integration/persistent_storage";

    export let setting: ModuleSetting;
    export let path: string;

    const cSetting = setting as MultiChooseSetting;
    const thisPath = `${path}.${cSetting.name}`;

    let errorValue: string | null = null;
    let timeoutId: ReturnType<typeof setTimeout>;
    let confirmXuanRikka = false;
    const isCombatTargetSelector = (path.endsWith(".Targets") || path.endsWith(".目标")) &&
        (cSetting.name === "Combat" || cSetting.name === "战斗目标");

    const dispatch = createEventDispatcher();

    function handleChange(v: string) {
        if (isCombatTargetSelector && v === "XuanRikka" && !cSetting.value.includes(v)) {
            confirmXuanRikka = true;
            return;
        }

        applyChange(v);
    }

    function applyChange(v: string) {
        if (cSetting.value.includes(v)) {
            const filtered = cSetting.value.filter(item => item !== v);

            if (filtered.length === 0 && !cSetting.canBeNone) {
                // Doesn't remove the element because in this case the value will be empty
                // And indicate the value
                errorValue = v
                clearTimeout(timeoutId);
                timeoutId = setTimeout(() => errorValue = null, 300);

                return;
            }

            cSetting.value = filtered;
        } else {
            cSetting.value = [...cSetting.value, v]
        }

        setting = {...cSetting};
        dispatch("change");
    }

    function confirmXuanRikkaSelection() {
        confirmXuanRikka = false;
        applyChange("XuanRikka");
    }

    let expanded = localStorage.getItem(thisPath) === "true";

    $: setItem(thisPath, expanded.toString());

    function toggleExpanded() {
        expanded = !expanded;
    }

    function formatChoice(choice: string) {
        return choice === "XuanRikka" || !$spaceSeperatedNames
            ? choice
            : convertToSpacedString(choice);
    }
</script>

<!-- svelte-ignore a11y-click-events-have-key-events -->
<!-- svelte-ignore a11y-no-static-element-interactions -->
<div class="setting">
    <div class="head" class:expanded on:contextmenu|preventDefault={toggleExpanded}>
        <div class="title">{$spaceSeperatedNames ? convertToSpacedString(cSetting.name) : cSetting.name}</div>
        <div class="amount">{cSetting.value.length}/{cSetting.choices.length}</div>
        <ExpandArrow bind:expanded/>
    </div>

    {#if expanded}
        <div class="choices" transition:slide|global={{duration: 200, axis: "y"}}>
            {#each cSetting.choices as choice (choice)}
                <div
                        class="choice"
                        class:active={cSetting.value.includes(choice)}
                        class:error={errorValue === choice}
                        on:click={() => {
                            handleChange(choice)
                        }}
                >
                    {formatChoice(choice)}
                </div>
            {/each}
        </div>
    {/if}
</div>

{#if confirmXuanRikka}
    <div class="confirmation-backdrop" role="presentation">
        <div class="confirmation" role="dialog" aria-modal="true" tabindex="-1">
            <div class="confirmation-title">Dangerous target</div>
            <div class="confirmation-message">确认要包含轩酱吗？</div>
            <div class="confirmation-actions">
                <button type="button" class="cancel" on:click={() => confirmXuanRikka = false}>取消</button>
                <button type="button" class="confirm" on:click={confirmXuanRikkaSelection}>确定</button>
            </div>
        </div>
    </div>
{/if}

<style lang="scss">

  .setting {
    padding: 7px 0;
    color: var(--clickgui-text-color);
  }

  .title {
    color: var(--clickgui-text-color);
    font-size: 12px;
    font-weight: 600;
  }

  .choice {
    color: var(--clickgui-text-dimmed-color);
    background-color: var(--clickgui-selection-chip-background-color);
    border-radius: 3px;
    padding: 3px 6px;
    cursor: pointer;
    font-weight: 500;
    transition: ease color 0.2s;
    overflow-wrap: anywhere;

    &:hover {
      color: var(--clickgui-text-color);
    }

    &.error {
      background-color: var(--clickgui-selection-chip-remove-background-color) !important;
      color: var(--clickgui-selection-chip-remove-color) !important;
    }

    &.active {
      background-color: var(--clickgui-selection-chip-selected-background-color);
      color: var(--clickgui-selection-chip-selected-color);
    }
  }

  .amount {
    letter-spacing: 1px;
    font-weight: 500;
    font-size: 12px;
    font-family: monospace;
  }

  .head {
    display: grid;
    grid-template-columns: 1fr max-content max-content;
    transition: ease margin-bottom .2s;
    align-items: center;

    &.expanded {
      margin-bottom: 10px;
    }
  }

  .choices {
    border-left: solid 2px var(--clickgui-setting-group-border-color);
    color: var(--clickgui-text-color);
    padding: 7px 7px;
    display: flex;
    flex-wrap: wrap;
    gap: 7px;
    font-size: 12px;
  }

  .confirmation-backdrop {
    position: fixed;
    inset: 0;
    z-index: 10000000000;
    display: grid;
    place-items: center;
    background: rgba(35, 0, 0, 0.58);
  }

  .confirmation {
    width: min(340px, 80vw);
    padding: 18px;
    border: 2px solid #d94444;
    border-radius: 5px;
    background: #291414;
    color: #fff1f1;
    box-shadow: 0 8px 30px rgba(0, 0, 0, 0.55);
  }

  .confirmation-title {
    color: #ff7070;
    font-size: 15px;
    font-weight: 700;
  }

  .confirmation-message {
    margin-top: 12px;
    font-size: 13px;
  }

  .confirmation-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 18px;
  }

  .confirmation-actions button {
    border: 1px solid #8f3a3a;
    border-radius: 3px;
    padding: 6px 14px;
    cursor: pointer;
    color: #fff1f1;
  }

  .confirmation-actions .cancel {
    background: #452121;
  }

  .confirmation-actions .confirm {
    background: #b52f3b;
    border-color: #ff7070;
  }
</style>
