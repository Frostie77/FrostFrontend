# FrostFrontend

**FrostFrontend** is a reusable Android settings overlay for game ports and handhelds, first designed for **Arcane: The Armor Collector** on the **AYN Odin 2 Portal**.

It gives games a compact controller-friendly menu without making them depend on Arcane, Ruffle, or any particular game engine.

> **Status: v0.5.0-dev-demo — Odin 2 Portal user acceptance tests reported satisfactory.** The revised demo follows Arcane's proven layout, including its warning triangle, exit confirmation and settings structure. Integration into another game is still pending; the production Arcane v0.2.0 remains unchanged.

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

## Per-game controller profiles

Ports can now define their own actions and default **physical gamepad buttons**
with `FrostControlProfile`. The universal menu, button conflict handling,
unmapped-control warning, and **Sound → Speaker Test** do not change.

Example in a game's Android Activity:

```java
FrostControlProfile profile = FrostControlProfile.of("example-port",
    new FrostButtonMapper.Action("jump", "Jump", KeyEvent.KEYCODE_BUTTON_A),
    new FrostButtonMapper.Action("attack", "Attack", KeyEvent.KEYCODE_BUTTON_B),
    new FrostButtonMapper.Action("interact", "Interact", KeyEvent.KEYCODE_BUTTON_X)
);
SharedPreferences prefs = getSharedPreferences("controls", MODE_PRIVATE);
FrostButtonMapper mapper = profile.load(prefs);

// Supply mapper.actions() and mapper.buttonFor(id) to the familiar Button Mapping
// rows; call frontend.enterButtonMapping(mapper, onLeaveMapping).
// After capture:
mapper.assign("jump", KeyEvent.KEYCODE_BUTTON_Y);
mapper.save(prefs, profile.preferencePrefix());
```

The host port translates the mapped action IDs into its actual gameplay
inputs. Profile IDs are stable and separate their saved button assignments.
The demo uses an Arcane-compatible profile with its existing `binding_`
storage prefix, preserving the user's current demo assignments.

**Current scope:** physical button action definitions and defaults. This
does not yet implement analogue-stick remapping, touch control behaviour,
or the gameplay input bridge. Those remain game-specific. The universal
`FrostSpeakerTest` remains unchanged.

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
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\update-demo.ps1
```

This script runs `git pull --ff-only`, builds the latest demo APK and uses `adb install -r` to update the already-installed demo without clearing its saved settings. Your Odin must be connected with USB debugging enabled. It does not touch Arcane.

The script is currently configured for the Windows JDK/Gradle/Android SDK locations used during development. If those tools are moved, adjust their paths in `update-demo.ps1`.

## Arcane menu parity — demo v0.5.0-dev-demo

The demo has been realigned with the **working Arcane v0.2.0 FrostFrontend** rather than inventing a different settings layout. It retains the same header styling, row spacing, highlighted-row descriptions, submenu structure, controller navigation, default button mappings, touch settings and movement settings.

**Only changes to the visible Arcane menu:** a new **Sound** heading immediately after **Graphics**, and **Speaker Test: Left → Right → Both** moved from Graphics into Sound. All other menu pages follow Arcane's layout and labels.

The Arcane-derived button mapping screen now includes:

- The small, **painted yellow warning triangle** alongside any **Unmapped** action.
- The matching **UNMAPPED CONTROLS** modal when leaving mapping with missing assignments, listing the affected actions.
- **Keep Editing** (default) and **Continue Anyway**, selectable by touch or D-pad/A. B, Start or Android Back dismiss the warning without exiting.
- Warning checks for **Back to Controls**, changing tabs and exiting using the X or Start.
- Original Arcane defaults: Attack R2, Alternate Attack R1, Switch Weapon A, Swap Armour B, Throw X, Previous Throwable Y, Next Throwable L1.

The game-independent mapping model keeps the same conflict rule as Arcane: assigning a previously used button makes its old action **Unmapped**. The demo saves its own settings but does **not** launch the Arcane game or alter game input. Only Arcane's in-game version actually controls Arcane gameplay.

**Testing status (user report):** The user has tested the updated standalone demo on the Odin 2 Portal and reports that all tested features are satisfactory so far. This includes review of the revised Arcane-style interface and settings. No separate automated regression suite or integration test in another game is yet recorded. The production Arcane v0.2.0 code and release remain unchanged.

## Odin 2 Portal demo verification

The user tested the separately installed `FrostFrontend Demo` APK on the AYN Odin 2 Portal:

- **PASS:** The four-tab layout renders as expected.
- **PASS:** Example settings change and display their new values.
- **PASS:** Settings persist after closing and reopening the app.
- **PASS:** Physical controls: L1/R1 tab switching, D-pad navigation, A selection, B close, and Start reopen.
- **PASS:** Touch-only tab switching, setting selection and close (X) button.

This validates the demo host and core library interaction. It does not yet prove compatibility with all third-party ports.
The original four-tab demo passed device testing. The five-tab demo and native Sound speaker test also passed, and the user subsequently reported satisfaction after testing the revised v0.5.0 feature set. This is a user acceptance report, not a claim of independent automated verification.


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
