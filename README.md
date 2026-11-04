# FrostFrontend

**FrostFrontend** is a reusable Android settings overlay for game ports and handhelds, first designed for **Arcane: The Armor Collector** on the **AYN Odin 2 Portal**.

It gives games a compact controller-friendly menu without making them depend on Arcane, Ruffle, or any particular game engine.

> **Status: v0.1.0 development foundation.** The reusable library is newly extracted in concept and **has not yet been compiled or tested on an Android device**. Arcane v0.2.0 still uses its existing, proven in-app FrostFrontend. Nothing in Arcane has been replaced.

## What's included

- **Android library module**: \`frostfrontend/\`
- Compact dark menu with gold highlights, a top navigation bar and a dedicated close button
- Tabs and setting rows supplied by each host game
- Touch navigation and gamepad navigation: **L1/R1**, **D-pad**, **A**, **B**, **Start**
- Setting descriptions that appear only for the selected row
- Host callbacks for setting changes and pausing/resuming gameplay

Each game provides its **own** tabs, settings, button mappings, saving and update checks. FrostFrontend provides the common interface and navigation.

## Building the library

Requirements: **JDK 17**, **Android SDK 35**, and **Gradle 8.11.1**.

From the repository root:

\`\`\`powershell
gradle :frostfrontend:assembleDebug
\`\`\`

The output is an **Android library (AAR)**, not a standalone APK. A game embeds the library into its own Android project.

## Integration

Add the \`frostfrontend\` module to the host game's Gradle project and include:

\`\`\`gradle
implementation project(':frostfrontend')
\`\`\`

Then create and add the overlay above your game content. The Android Activity supplies the tabs and the values:

\`\`\`java
FrostFrontendView frontend = new FrostFrontendView(this);
root.addView(frontend, new FrameLayout.LayoutParams(-1, -1));

frontend.setTabs(Arrays.asList(
    new FrostFrontendView.Tab("graphics", "Graphics"),
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
\`\`\`

Open with \`frontend.open()\` and close with \`frontend.close()\`. Call \`frontend.refresh()\` when values change.

When the overlay is open, route key events through \`frontend.handleKeyEvent(event)\` **before** game input. The host should use its Start/menu button to open FrostFrontend when it is closed.

**Important:** This first library covers the **settings UI**, not a game's touch joystick, aiming bridge, save system, audio output, or updater. Those remain game-specific until reusable interfaces are designed and tested.

## Roadmap

1. Compile the first library and test a small Android demo app.
2. Match the working Arcane menu's appearance and controller behavior.
3. Add reusable settings adapters and optional touch-control components.
4. Integrate FrostFrontend into a new test port.
5. Only after regression testing, consider migrating Arcane to the shared library.

## Credits

Created for Android game port projects and initially developed through the *Arcane: The Armor Collector* Android port.

This library contains **no original game SWFs or copyrighted game assets**. The Arcane release project is separate: [Arcane Android](https://github.com/Frostie77/Arcane-The-Armor-Collector).

A public redistribution license has **not yet been selected**.
