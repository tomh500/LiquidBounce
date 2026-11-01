# Rikka compatibility audit (in progress)

Branch: `check/rikkareturn`, based on `nextgen` at `66f363fa2`.

## Implemented, awaiting complete runtime validation

- PacketMine: explicit primary Baritone break handoff through a host-owned Mixin; manual block attacks no longer infer automation ownership. Clear owned targets when automation stops requesting a break or pauses, and clear cached aim and owned silent slot on target replacement.
- PacketMine Immediate: evaluate completion independently of whether progress can still advance; mark completion while waiting for server confirmation.
- PacketMine Civ: release automated targets on server-confirmed air, while preserving manual Civ behavior.
- Combat pause: enabled KillAura without a target no longer permanently pauses automation. Threat movement can take priority over the general combat pause, with AutoDodge yielding retained.
- RikkaKAHelper: guard inactive/worldless operation and stale entity references; LB helper targets pass through aiming, and Vape/Silent helper targets pass through their mode filters.
- Rikka task modules: switch off previous automation modules before starting a new task; disable task state on disconnect; normalize reversed mining height bounds; assign custom module origins.
- RikkaPause: use an independent high-priority temporary process, so releasing the module does not release a command-owned pause.
- Shared inventory swaps and AutoTool: independently listen for world changes so pending restore records and mining-delay positions do not survive disconnect even when the module's normal handlers are inactive.
- Clutch: discard old landing positions, delayed camera resets, movement delays and saved return slots on world change without restoring a previous-world slot into the new inventory.
- Triggerbot: honor shared combat pauses and dead/spectator states; correct ExtraDelay so positive ticks delay attacks and negative ticks advance them. Verified intended sign against Vape Triggerbot's cooldownOffset calculation. SelectFirstHit now waits for a new local-player damage edge, as in the reference, with state reset on world change and activation. Return the actual attack result to the click scheduler when another module cancels the attack.
- Added regression tests for inactive-owner inventory cleanup and first-hit damage/target/setting transitions. All nine cases passed (four visibility, four first-hit, one inventory lifecycle); subsequent small Triggerbot lifecycle/scheduler edits are undergoing another validation pass.

## Evidence

- `cmd /c gradlew compileKotlin compileJava`: passed after current code changes.
- `cmd /c gradlew test --tests net.ccbluex.liquidbounce.config.types.ValueVisibilityTest --no-parallel`: passed (up-to-date results). Log: `build/rikka-tests.log`.
- After the live client locked resource outputs, the same test was rerun with `--init-script build/rikka-isolated.init.gradle` into `build-rikka-audit`; passed with test execution (47 seconds), log `build/rikka-isolated-tests.log`. This covers the shared-inventory/AutoTool additions, before the later Clutch cleanup.
- `cmd /c npm run check` in `src-theme`: 0 errors, 0 warnings. Log: `build/rikka-theme-check.log`.
- 68 language JSON files parsed using PowerShell `ConvertFrom-Json -AsHashtable` (case-sensitive keys preserved).
- Runtime validation started with `cmd /c gradlew runClient`; log: `build/rikka-client-validation.log`. At 05:16:40 on 2026-11-01, the client reported successful initialization and its browser theme loaded. No edited-module/Mixin failure was observed. Windows OSHI/Perflib system-statistics errors and development Realms authentication warnings occurred before initialization.
- The live client reached the Microsoft authorization flow because no saved Microsoft account exists. Do not automate the authorization UI. The client remains running. In-world behavior is not yet verified.
- The later shared-inventory/AutoTool world-cleanup changes compiled successfully but are not loaded in that already-running client; they require subsequent runtime validation.
- First runtime-initialized batch committed as `896b327c6` (13 files): pathing handoff, combat coordination, task switching and mode filtering. Later lifecycle/Triggerbot additions remain uncommitted pending runtime validation. Existing startup/download-proxy changes and README modifications belong to the user and were excluded.

## Remaining investigation and validation

### User-directed mining policy

Implementation in progress: `RikkaMiningTools` now provides a shared hotbar policy and `MixinToolSet` uses configured RikkaMine resource blocks for both path-estimate and execution selection. `MixinBlockBreakHelper` refuses resource breaks with no eligible hotbar tool. Policy regression tests cover stone/iron/fortune-diamond selection, harvest eligibility, silk preference and durability protection. Validation log: `build/rikka-mining-policy-validation.log`, last launched session 5582.

PacketMine now uses the same resource/obstacle tool policy for Rikka-owned targets, acquires its silent slot before progressing, and skips manual tool-switch modes for automated mining. AutoTool yields for those targets and Baritone-requested breaks, clears its stale silent selection and cancels pending inventory requests. Missing tools produce a throttled notification. The final isolated build and 13 tests passed (`build/rikka-final-tests.log`). The theme check reported 0 errors and 0 warnings; 68 language JSON files parsed; `git diff --check` passed. `cmd /c gradlew --init-script build/rikka-client-isolated.init.gradle runClient` initialized the current code (`build/rikka-final-client.log`, 13:59:34). The unrelated branch-update lookup returned HTTP 404 because this local branch has no published build. Existing client sessions were left running.

In-world scenarios remain for user verification: target ores with mixed iron/fortune/stone tools, no eligible hotbar tool, inventory provisioning, PacketMine mode switching, combat interruption and world transitions. The development client reached Microsoft login and was not exercised in-world. Fortune usefulness is currently a general resource preference; block-specific loot semantics are future tuning. The client remains running for testing.

- Distinguish intended resource targets from disposable path obstacles. A fortune diamond pickaxe should collect fortune-compatible target ores; expendable stone picks should handle ordinary stone where practical, with iron/higher tools used when required or when the speed tradeoff warrants it.
- Determine harvest eligibility before optimizing speed, durability or enchantments. Breaking a target without its drops is not a successful mining action. With no suitable tool, skip/pause that target instead of destroying it without yield.
- Honor explicit silk-touch collection preferences and block-specific fortune usefulness; do not blindly rank every enchantment as beneficial.
- Preserve valuable tool durability while clearing paths. Path cost estimates must use the same selected tool policy as execution.
- The current local Baritone implementation attempts separate obstacle/resource policies, but incorrectly classifies usefulness by normalized progress greater than 1.0. A host-owned Mixin replaces tool selection only during RikkaMine, preserving LiquidBounce/Baritone default behavior outside this mode. Runtime validation remains pending.
- RikkaMine now exposes AllowInventory (default true) and explicitly enables internal tool selection instead of inheriting stale external-tool delegation. Validate interaction with LB inventory scheduling before committing.

- Confirm runtime initialization, Mixin application and absence of relevant exceptions; keep the client running.
- Exercise automated mining with PacketMine Normal/Immediate/Civ, server confirmation, task stop/switch, combat interruption, manual target priority and world transitions.
- Audit hotbar/tool switching (AutoTool/AutoWeapon), placement/rotation conflicts (Scaffold/Clutch/BlockIn), movement conflicts and target selection restrictions.
- Review all registered custom modules, including Triggerbot, RightClicker, BlockHit, dynamic lights and music integration. Initial source reads alone do not establish compatibility.
- Add focused behavioral regression coverage where feasible; the visibility test does not test pathing or mining behavior.
- Inspect final diff/status, run required checks after any further edits, and commit only verified task-owned changes.

The local `baritone/` directory is an ignored separate Git repository. Its temporary edit was removed; the integration resides in the main repository Mixin instead.
