# Rikka compatibility audit

Branch: `check/rikkareturn`, based on `nextgen` at `66f363fa2`.

## Changes made

- Baritone and PacketMine now hand off automated breaks explicitly. Manual attacks no longer claim a Rikka target. PacketMine clears stale targets, rotations and silent slots when an automated break stops or changes; Immediate and Civ completion account for server confirmation.
- Rikka task modules switch off the previous automation before taking ownership, clean state on disconnect, and respect combat pauses without letting idle KillAura block pathing. RikkaPause owns a separate temporary process.
- Triggerbot now follows the reference first-hit damage edge and delay sign, honors combat pauses, and clears stale state on world changes. Clutch, AutoTool and shared inventory swaps also clear previous-world state.
- RikkaMine distinguishes configured resource targets from path obstacles. Resource blocks require a harvest-capable tool; an unsuitable hand or pickaxe cannot silently destroy the target. Tool ranking considers harvest tier, silk preference, fortune, durability and mining speed. PacketMine uses the same policy for Rikka-owned targets; AutoTool yields to it.
- Baritone's stone-based inventory replenishment could leave a suitable iron pickaxe in the main inventory while a weak pickaxe occupied the hotbar. The inventory Mixin now selects a harvest-capable tool for the current resource target and uses Baritone's existing swap scheduler. It does not swap while a container screen is open.

## Validation

- `cmd /c gradlew compileKotlin compileJava` and focused tests, including `ValueVisibilityTest` and `RikkaMiningToolsTest`, passed in isolated build output (`build/rikka-inventory-validation.log`). Earlier batches also passed 13 tests (`build/rikka-final-tests.log`).
- `cmd /c npm run check` in `src-theme` passed with 0 errors and 0 warnings (`build/rikka-inventory-theme.log`). Source language JSON files and the Mixin JSON parsed successfully. `git diff --check` passed.
- `cmd /c gradlew runClient` with the isolated build init script reached `LiquidBounce has been successfully initialized` (`build/rikka-inventory-client.log`, 2026-11-01 20:37:16 local). No relevant Mixin injection failure appeared. The client remains running. It reached Microsoft login, so automated mining has not been exercised in-world.
- Task changes were committed as `896b327c6`, `c60807d49`, and `8dd4df2b8`. Unrelated startup, download-proxy and README changes in the working tree were preserved.

## In-world checks still useful

- Mine ores with iron, stone and Fortune diamond pickaxes in mixed hotbar and main-inventory layouts. Confirm correct drops and Baritone's inventory move delay.
- Try PacketMine Normal, Immediate and Civ, target replacement, manual mining, combat interruption, task switching and disconnect/reconnect.
- Fortune usefulness is currently a general resource preference. Block-specific loot behavior remains an opportunity for tuning after gameplay observation.
