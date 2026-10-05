# Keeping XuanRikka compatible with upstream

`upstream` tracks CCBlueX's `nextgen`. Adopt upstream's infrastructure and fixes;
keep client-owned behavior behind small, explicit integration points. This reduces
text conflicts but does not remove the need to review API and runtime changes.

## Extension locations

| Concern | Client-owned implementation | Upstream integration point |
| --- | --- | --- |
| Custom module registration | `XuanRikkaModules.java` | One import and list concatenation in `ModuleManager.registerInbuilt` |
| ClickGUI language and initial scale | `extensions/XuanRikkaTabs.svelte`, `settings_i18n.ts` | One `Tabs` import in `TabbedClickGui`; optional display-label callback in `tabs/Tabs.svelte` |
| Browser resource lifetime, first paint and release focus | Java mixins under `injection/mixins/xuanrikka`, `FrameRetirementQueue` | Separate `liquidbounce.xuanrikka.mixins.json` registered in the mod manifest |
| Persisted value schema upgrades | `config/compat/LegacyValueMigration.java` | One normalization call in the existing `deserializeLeafValue` migration hook |
| Keyboard name and SDL modifier compatibility | `InputKeyNames.java`, `KeyboardInputCompatibility.java` | Name lookup in `InputUtil`; keyboard event observation before MaLiLib's cancellable hook |
| Windows extended right Shift (MC-311424) | `WindowsShiftCompatibility.java`, `MixinWindowsShiftInput`, `MixinWindowsShiftState` | Repair unknown native SDL events before keyboard consumers; preserve held state until release or focus loss |
| Vape implementations | Dedicated mode/controller files and `GlobalVapeRotationSettings` | Existing mode selection and shared rotation entry points |

Add new custom modules to `XuanRikkaModules`, not to upstream's built-in imports
or category lists. They still go through upstream's registration, configuration
key initialization and description validation, so existing saved module keys and
categories do not change. Compatible modes of existing modules remain in their
existing settings trees; do not duplicate an entire upstream module to avoid a
merge conflict.

Keep tab titles as stable English IDs. Translate their displayed labels only.
The extension wrapper initializes language and initial scaling; upstream owns
tab persistence, focus handling and subsequent scale updates. Settings controls
continue using the existing display-only localization layer.

Normalize legacy values in `LegacyValueMigration` rather than changing upstream
value classes or rewriting users' configuration files manually. The current rules
preserve explicit boolean acceleration preferences as `On`/`Off`, and scalar
settings upgraded to ranges as fixed intervals. Modern shapes pass through unchanged.

## Browser ownership

The Windows right Shift adapter backports SDL's `0xE036` mapping, documented in
[SDL's Windows scancode table](https://github.com/libsdl-org/SDL/blob/main/src/events/scancodes_windows.h).
It only repairs unknown keys with a recognized raw right Shift code, never arbitrary
unknown events. These keyboard mixins apply independently of the CEF add-on.
Remove this adapter once the bundled SDL recognizes that scan code natively.

Use upstream's bundled `liquidbounce-cef` dependency and backend registration.
Do not restore the deleted client-side `CefBrowser`/`CefBrowserBackend`, vendor
the add-on sources, or expose CEF internals in the main client API.

The optional Java mixins link to CEF by target name, with no compile dependency
on its classes. They activate only when the CEF add-on is installed. The native
browser close and incognito context disposal wait for two completed render frames;
input and rendering listeners are still removed immediately by upstream. Pending
closes flush before the CEF runtime stops. First paint uses `Browser.invalidate()`
through the public browser contract. The new add-on handles SDL clipboard keys,
so the old MCEF-specific Mac shortcut workaround is not carried forward.

Keep injection requirements strict: if an add-on update changes a method signature,
client startup should expose the incompatibility instead of silently disabling a
resource-safety fix. Review whether upstream has absorbed each adapter when the
CEF dependency changes, and delete adapters that become redundant.

## Merge and verification

1. Fetch upstream and fast-forward the local `upstream` tracking branch. Preserve
   unrelated dirty changes separately before merging; never include them implicitly
   in the merge commit.
2. Merge upstream normally. Review both textual conflicts and automatic merges at
   integration points. Prefer upstream implementations, then adapt local extensions.
3. Compile with `cmd /c gradlew compileKotlin compileJava`.
4. Run `ValueVisibilityTest`, `ValueListenerTest` and `FrameRetirementQueueTest`
   with `cmd /c gradlew test --tests ... --no-parallel`; check language JSON with a
   structured parser and run `cmd /c npm run check` from `src-theme`.
5. Run `cmd /c gradlew runClient`. Confirm add-on/browser initialization, ClickGUI
   input and Settings language, and screen-close behavior without new exceptions.
   Keep the client running for user testing.
6. Inspect status and `git diff --check`, then commit only the verified merge and
   extension changes.

Local `git rerere` can remember conflict resolutions; it is an aid, not a substitute
for reviewing the resulting behavior. API changes, Minecraft rendering changes and
Vape compatibility hooks can still require adaptation even when Git merges cleanly.
