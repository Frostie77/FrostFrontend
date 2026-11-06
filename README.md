# FrostFrontend

FrostFrontend is a reusable frontend library for Android game ports, providing a controller-friendly settings interface, customisable button mapping, persistent touch layout editing, and native stereo speaker testing. It is designed to be integrated directly into individual game projects.

Originally developed for [Arcane: The Armor Collector Android](https://github.com/Frostie77/Arcane-The-Armor-Collector), FrostFrontend has now also been **successfully integrated and tested** in [Dad 'n Me Android](https://github.com/Frostie77/Dad-n-Me) (v0.1.1), confirming it can be reused in a new game port without depending on a particular game engine.

## Frontend

FrostFrontend provides a consistent settings menu with five sections: **Graphics**, **Sound**, **Controls**, **Touch Settings**, and **Updates**. It supports controller and touchscreen navigation, while each game supplies its own settings and behaviour.

## Controls

`FrostControlProfile` lets each game define its own actions and default controller buttons. `FrostButtonMapper` provides button reassignment, saved mappings, and warnings when an action is left unmapped.

The game itself remains responsible for connecting those mappings to gameplay.

## Sound

`FrostSpeakerTest` provides a universal **Left → Right → Both** speaker test using native Android audio output, independently of the game's audio system.

## Touch layouts

**Edit Touch Layout** is a built-in Touch Settings feature for ports that register a `FrostTouchControls` view. Players can drag every registered control, including movement pads, and choose **Save**, **Cancel** or **Reset**. Layouts persist between launches in each app's private preferences. Positions scale with the viewport, remain within its bounds and can mirror with swapped sides. Reset is a preview until saved. Back or gamepad B cancels; Start saves and returns to settings.

The editor stays inside the open frontend, preserving the host's gameplay pause while editing and across background/resume. Editor gestures never reach the gameplay input handler. The frontend restores the touch view's original parent, order, layout parameters and visibility when editing ends.

For integration:

1. Subclass `FrostTouchControls`, supplying a stable profile ID and stable control IDs to its constructor. Alternatively, supply an existing `SharedPreferences` store to retain layouts from a previous port version.
2. Implement `computeLayout()` using `positionControl(index, defaultX, defaultY, visibleRadius, hitRadius)` for every control. Use the resolved `centerX` and `centerY` values for artwork and gameplay hit areas. Call `setLayoutMirrored(...)` before positioning controls when sides are swapped.
3. Implement `drawControls`, `onGameplayTouch` and `releaseGameKeys`. The base class handles editing, persistence, bounds, pointer ownership and releasing inputs when editing starts.
4. Add both views to the host and register `frontend.setTouchControls(touchControls)`. FrostFrontend adds its own editor row to the tab with ID `touch`; hosts do not duplicate that row or its toolbar.
5. Keep the normal `Adapter.onVisibilityChanged` pause/resume handling. If the host updates touch visibility while editing, keep the controls visible while `frontend.isEditingTouchLayout()` is true. `frontend.handleKeyEvent`, `handleGenericMotionEvent` and `back()` handle editor navigation.

Call `resetLayout()` when the user chooses a default placement preset or resets touch settings. Size, opacity, artwork and game-specific input remain the port's responsibility. The standalone demo registers a movement pad and two action buttons; CF2 uses the same shared implementation with its own controls and existing preference store.

## Integration

Add FrostFrontend as an Android library module (for example, through a Git submodule) and include `implementation project(':frostfrontend')` in the game's Gradle dependencies. The host app defines its own menu items, button mappings, touch controls, and update-checking logic; FrostFrontend provides the shared interface and utilities.

**Validated in:**
- [Arcane: The Armor Collector Android](https://github.com/Frostie77/Arcane-The-Armor-Collector) — original implementation
- [Dad 'n Me Android](https://github.com/Frostie77/Dad-n-Me) — independent game port, tested on AYN Odin 2 Portal

## Development

The repository includes a standalone Android demo for testing the frontend. The library is included in individual game builds, not distributed as a separate app.

With Java 17, Android SDK 35 and Gradle 8.11.1, run `gradle :frostfrontend:testDebugUnitTest :demo:assembleDebug :demo:assembleRelease`. CI runs the shared layout and editor regression tests and builds both demo variants. Existing integrations that do not register touch controls retain their existing menu behavior.

## License

FrostFrontend is licensed under the [MIT License](LICENSE).

Copyright © 2026 Frostie77.
