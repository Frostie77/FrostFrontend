# FrostFrontend

**FrostFrontend** is a reusable, controller-friendly Android settings overlay for game ports and handhelds. It originated in [Arcane: The Armor Collector Android](https://github.com/Frostie77/Arcane-The-Armor-Collector) and is developed separately so other games can embed it without depending on Arcane or Ruffle.

> **Current development baseline:** `v0.6.0-dev-demo`. The standalone demo has been tested on the AYN Odin 2 Portal, with the user confirming the existing features and appearance remained satisfactory after the control-profile update. Integration into a second game and testing a different control profile are **still pending**. The published Arcane v0.2.0 APK has not been modified.

## What stays universal

- Arcane-style dark/gold menu, layout, controller navigation, highlighted-row descriptions, and close button.
- Headers in order: **Graphics → Sound → Controls → Touch Settings → Updates**.
- **Sound → Speaker Test: Left → Right → Both**, using native Android stereo playback through `FrostSpeakerTest`.
- Controller button capture and conflict handling: assigning an occupied button leaves the previous action **Unmapped**.
- Yellow warning triangle beside unmapped actions and the **UNMAPPED CONTROLS** dialog with **Keep Editing / Continue Anyway**.
- Reusable `FrostButtonMapper` and `FrostControlProfile` classes.

The only intentional visible differences from the Arcane menu are the added **Sound** header and moving **Speaker Test** out of Graphics into Sound. Game-specific settings and button actions are supplied by the host port.

**Important:** The speaker test checks native Left/Right/Both output; it does not diagnose or repair the host game's audio mixer. The host should pause its game audio while the test plays and stop the test when closing the menu or destroying the Activity.

## Per-game control profiles

Each port declares its action IDs, display names, and default **physical button** assignments. The shared mapping menu remains unchanged. For example:

```java
FrostControlProfile profile = FrostControlProfile.of("example-port",
    new FrostButtonMapper.Action("jump", "Jump", KeyEvent.KEYCODE_BUTTON_A),
    new FrostButtonMapper.Action("attack", "Attack", KeyEvent.KEYCODE_BUTTON_B),
    new FrostButtonMapper.Action("interact", "Interact", KeyEvent.KEYCODE_BUTTON_X)
);
SharedPreferences prefs = getSharedPreferences("controls", MODE_PRIVATE);
FrostButtonMapper mapper = profile.load(prefs);

// On button capture:
mapper.assign("jump", KeyEvent.KEYCODE_BUTTON_Y);
mapper.save(prefs, profile.preferencePrefix());
```

Use a stable profile ID and private preferences for each game. The port renders `mapper.actions()` / `mapper.buttonFor(action.id)` in its Button Mapping rows and calls `frontend.enterButtonMapping(mapper, onLeaveMapping)` on entering that submenu. The host must translate the resulting action IDs and bindings into **actual gameplay input**.

The demo uses the original Arcane action names and defaults (Attack R2, Alternate Attack R1, Switch Weapon A, Swap Armour B, Throw X, Previous Throwable Y, Next Throwable L1). It continues using its earlier `binding_` preference prefix to preserve installed users' saved mappings.

The current profile system supports discrete gamepad buttons. Analogue stick behaviour, touch controls, pausing, graphics options, and in-game audio remain the responsibility of the host port. A second, genuinely different control profile has not yet been tested on-device.

## Embed FrostFrontend in an Android port

FrostFrontend **does not need a separate GitHub Release**. Games include its source as a Gradle library module. We recommend pinning each game to a known Git revision using a Git submodule.

From the game's repository root:

```powershell
git submodule add https://github.com/Frostie77/FrostFrontend.git third_party/FrostFrontend
```

In the host project's `settings.gradle` (Groovy), add:

```gradle
include ':frostfrontend'
project(':frostfrontend').projectDir = file('third_party/FrostFrontend/frostfrontend')
```

In the game's Android app module's `build.gradle`:

```gradle
dependencies {
    implementation project(':frostfrontend')
}
```

Other developers cloning the game should use `git clone --recurse-submodules` (or `git submodule update --init --recursive` for an existing clone). The host project must use compatible Android Gradle Plugin/SDK settings; do not blindly copy the frontend's standalone root build files into the game's build.

The host Activity then creates `new FrostFrontendView(this)`, adds it above the game view, supplies five tabs with `setTabs(...)`, and implements `FrostFrontendView.Adapter` to display and apply the game's own setting values:

- `itemsForTab(tabId)`: provide rows for that page, including mapping rows and unmapped flags.
- `onItemSelected(tabId, itemId)` / `onItemAdjusted(..., direction)`: apply and save settings; start the speaker test when its row is activated.
- `onVisibilityChanged(visible)`: pause/resume gameplay and audio as appropriate; stop the speaker test when hidden.

When open, deliver controller events to `frontend.handleKeyEvent(event)` and joystick motion to `frontend.handleGenericMotionEvent(event)` **before** the game's input bridge. The host must provide its own way to open the overlay (for example Start). Call `frontend.refresh()` after changing displayed values. The frontend handles its own warning UI, but the host supplies the mapper and mapping-submenu lifecycle.

`FrostSpeakerTest` is part of the library; it needs no game-specific implementation. For example, a host can retain an instance and call `speakerTest.start()` from the universal Sound row, then `speakerTest.stop()` when the menu closes and in `onDestroy()`.

## Build and test the standalone demo

Requirements: **JDK 17**, **Android SDK 35**, and **Gradle 8.11.1**. The standalone repository uses Android Gradle Plugin 8.9.2. From the repository root:

```powershell
gradle :frostfrontend:assembleDebug
gradle :demo:assembleDebug
```

The library outputs an **AAR**; the demo outputs `demo/build/outputs/apk/debug/demo-debug.apk`. The demo app contains no Arcane game assets, does not run Arcane, and does not change Arcane's settings or releases.

For the existing Windows/Odin development environment, with USB debugging enabled and the device connected, the streamlined script pulls, builds, and installs the latest demo **without clearing its data**:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\update-demo.ps1
```

The script has local JDK, Gradle, and Android SDK paths configured for that development PC; adjust these if running it elsewhere. It does not install or modify Arcane.

### Odin 2 Portal test history

- Original four-tab standalone foundation: layout, navigation, settings persistence and touch input passed.
- Five-tab layout and native **Left → Right → Both** speaker test: user confirmed working through both speakers.
- Arcane-style menu restoration (v0.5.0): user tested and reported satisfaction with all tested features.
- Per-game profile migration (v0.6.0): user reported everything remained the same following the update.

These are **user-reported device checks**, not a claim of an exhaustive automated test suite or cross-game integration validation.

## Next steps

1. Test a second, differently configured `FrostControlProfile` in the demo.
2. Integrate FrostFrontend into a separate Android port and verify the host's input, pause, audio, and settings wiring.
3. Keep individual game ports pinned to tested FrostFrontend revisions; update their submodule intentionally.
4. Consider stable Git tags when appropriate. **Publishing separate GitHub Releases is optional, not required.**

The production Arcane v0.2.0 code and APK remain separate and unchanged.

## Licence and credits

Copyright © 2026 **Frostie77**.

FrostFrontend is available under the **MIT License**; see [LICENSE](LICENSE). Keep the copyright and licence notices with copies or substantial portions of the software.

This repository includes no original game SWFs or copyrighted game assets.
