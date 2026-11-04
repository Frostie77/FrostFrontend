# FrostFrontend

**FrostFrontend** is a reusable Android settings overlay for game ports and handhelds, first designed for **Arcane: The Armor Collector** on the **AYN Odin 2 Portal**.

It gives games a compact controller-friendly menu without making them depend on Arcane, Ruffle, or any particular game engine.

> **Status: v0.1.0 development foundation.** The reusable library is newly extracted in concept and **has compiled successfully as an AAR and passed core demo interaction tests on the AYN Odin 2 Portal**. Arcane v0.2.0 still uses its existing, proven in-app FrostFrontend. Nothing in Arcane has been replaced.

## What's included

- **Android library module**: `frostfrontend/`
- Compact dark menu with gold highlights, a top navigation bar and a dedicated close button
- Five menu sections in order: **Graphics, Sound, Controls, Touch Settings, Updates**
- Tabs and setting rows supplied by each host game
- Touch navigation and gamepad navigation: **L1/R1**, **D-pad**, **A**, **B**, **Start**
- Setting descriptions that appear only for the selected row
- Host callbacks for setting changes and pausing/resuming gameplay
- Reusable controller button mapping and physical-button capture (new; device test pending)

Each game provides its **own** tabs, settings and update checks, and connects the mapping model to its own gameplay input. FrostFrontend provides the common interface, navigation and optional reusable button-mapping model.

## Building the library

Requirements: **JDK 17**, **Android SDK 35**, and **Gradle 8.11.1**.

From the repository root:

```powershell
gradle :frostfrontend:assembleDebug
```

The output is an **Android library (AAR)**, not a standalone APK. A game embeds the library into its own Android project.

## Test the standalone demo APK

The separate `demo/` app exercises FrostFrontend's five tabs, touch input, gamepad navigation, native stereo speaker test and saved sample settings. It does **not** contain or change Arcane, its files, or its saved games.

From the repository root (with JDK 17 and `ANDROID_HOME` configured):

```powershell
gradle :demo:assembleDebug
```

Install `demo/build/outputs/apk/debug/demo-debug.apk` on your Android device.

Open it using **OPEN FROSTFRONTEND** or the controller **Start** button. Try changing tabs, selecting settings and closing/reopening the menu. Demo setting values are saved only inside the demo app. The "Check for Updates" row intentionally displays a demo-only message rather than accessing a real release feed.

## Sound: stereo speaker test (in testing)

**Sound** appears immediately after **Graphics** in the five-tab demo. Its **Speaker Test: Left → Right → Both** option uses the reusable `FrostSpeakerTest` Android class to play three 440 Hz stereo tones at the device's media volume. This plays independently of WebView or a game's sound system, so it checks speaker/channel output rather than repairing game audio.

The host game should pause its own sound while the test plays and call `stop()` when its menu closes or Activity is destroyed. The demo does both. **Odin 2 Portal test: PASS.** The user confirmed the native Left → Right → Both speaker test works correctly through the device speakers. Arcane's already-released APK remains unchanged.

## One-command demo update

After pulling this script once, you can run:

```powershell
.\update-demo.ps1
```

This script runs `git pull --ff-only`, builds the latest demo APK and uses `adb install -r` to update the already-installed demo without clearing its saved settings. Your Odin must be connected with USB debugging enabled. It does not touch Arcane.

The script is currently configured for the Windows JDK/Gradle/Android SDK locations used during development. If those tools are moved, adjust their paths in `update-demo.ps1`.

## New: reusable controller button mapping (in testing)

The library now includes `FrostButtonMapper`, a generic model for physical gamepad assignments. Each game defines its own action names and defaults; the library handles one-button-per-action assignments, conflicts, unassigned actions, reset and `SharedPreferences` persistence.

`FrostFrontendView.beginButtonCapture(...)` temporarily captures the next supported gamepad button (A/B/X/Y, L1/R1/L2/R2, L3/R3). **Start** or Android **Back** cancels without changing a binding. Start, the D-pad and Android Back cannot be assigned as gameplay buttons through this interface. If a button is already mapped, the previous action becomes **Unmapped** instead of silently swapping.

The separate demo now has **Controls → Button Mapping** with seven sample action assignments. These are saved only in the demo app and do not control Arcane's actual game actions. The demo intentionally uses the same package ID for in-place updates, so use `adb install -r` rather than uninstalling the previous demo.

**New-device checks to perform on the Odin:**

1. Open **Controls → Button Mapping** and select any action.
2. Assign a different physical gamepad button. Confirm the displayed assignment changes.
3. Reuse a button belonging to another action; the previous action should show **Unmapped**.
4. Close and reopen the demo; assignments should persist.
5. Try **Restore Default Buttons** and confirm the originals return.
6. During button capture, press **Start**; it should cancel rather than assign a button.

**Update (demo v0.4.0-dev-demo):** A user reported that assigning a button already used by another action did not take effect on the Odin 2 Portal. The mapping model is designed to override and unmap the previous action. We added a prominent in-menu confirmation and capture of L2/R2 when the Android gamepad reports those triggers as analogue axes. This revised behavior still requires device verification.

**Re-test:** Under Controls → Button Mapping, assign **Attack** to **X** (normally Throw). Expected: **Attack: X**, **Throw: Unmapped**, and a visible override confirmation. Then restart the demo and confirm those values persist. If the display does not change, report whether the **ASSIGN ATTACK** prompt appeared. Restoring defaults should reset Attack to R1 and Throw to X.

The controller mapping feature remains in development until this is confirmed on-device.

## Odin 2 Portal demo verification

The user tested the separately installed `FrostFrontend Demo` APK on the AYN Odin 2 Portal:

- **PASS:** The four-tab layout renders as expected.
- **PASS:** Example settings change and display their new values.
- **PASS:** Settings persist after closing and reopening the app.
- **PASS:** Physical controls: L1/R1 tab switching, D-pad navigation, A selection, B close, and Start reopen.
- **PASS:** Touch-only tab switching, setting selection and close (X) button.

This validates the demo host and core library interaction. It does not yet prove compatibility with all third-party ports.
The original four-tab demo passed device testing. The **five-tab demo and native Sound speaker test also passed** on the Odin 2 Portal. The newer button-mapping interface still needs device validation.


## Integration

Add the `frostfrontend` module to the host game's Gradle project and include:

```gradle
implementation project(':frostfrontend')
```

Then create and add the overlay above your game content. The Android Activity supplies the tabs and the values:

```java
FrostFrontendView frontend = new FrostFrontendView(this);
root.addView(frontend, new FrameLayout.LayoutParams(-1, -1));

frontend.setTabs(Arrays.asList(
    new FrostFrontendView.Tab("graphics", "Graphics"),
    new FrostFrontendView.Tab("sound", "Sound"),
    new FrostFrontendView.Tab("controls", "Controls"),
    new FrostFrontendView.Tab("touch", "Touch Settings"),
    new FrostFrontendView.Tab("updates", "Updates")
));

frontend.setAdapter(new FrostFrontendView.Adapter() {
    @Override public List<FrostFrontendView.Item> itemsForTab(String tabId) {
        if ("graphics".equals(tabId)) {
            return Arrays.asList(
                new FrostFrontendView.Item(
                    "display", "Display Mode", "Original 4:3", "Change the screen layout")
            );
        }
        return Collections.emptyList();
    }

    @Override public void onItemSelected(String tabId, String itemId) {
        // Apply the game's own settings and save them here.
    }

    @Override public void onVisibilityChanged(boolean visible) {
        // Pause gameplay when visible; resume when hidden.
    }
});
```

Open with `frontend.open()` and close with `frontend.close()`. Call `frontend.refresh()` when values change.

When the overlay is open, route key events through `frontend.handleKeyEvent(event)` **before** game input. The host should use its Start/menu button to open FrostFrontend when it is closed.

**Important:** This first library covers the **settings UI**, not a game's touch joystick, aiming bridge, save system, audio output, or updater. Those remain game-specific until reusable interfaces are designed and tested.

## Roadmap

1. ~~Compile the first library and test the standalone demo APK on the Odin 2 Portal.~~ **Done**.
2. Match the working Arcane menu's appearance and controller behavior.
3. Add reusable settings adapters and optional touch-control components.
4. Integrate FrostFrontend into a new test port.
5. Only after regression testing, consider migrating Arcane to the shared library.

## Credits

Created for Android game port projects and initially developed through the *Arcane: The Armor Collector* Android port.

This library contains **no original game SWFs or copyrighted game assets**. The Arcane release project is separate: [Arcane Android](https://github.com/Frostie77/Arcane-The-Armor-Collector).

A public redistribution license has **not yet been selected**.
