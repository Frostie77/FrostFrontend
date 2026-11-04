# FrostFrontend

FrostFrontend is a reusable frontend library for Android game ports, providing a controller-friendly settings interface, customisable button mapping, and native stereo speaker testing. It is designed to be integrated directly into individual game projects.

Originally developed for [Arcane: The Armor Collector Android](https://github.com/Frostie77/Arcane-The-Armor-Collector), FrostFrontend has now also been **successfully integrated and tested** in [Dad 'n Me Android](https://github.com/Frostie77/Dad-n-Me) (v0.1.1), confirming it can be reused in a new game port without depending on a particular game engine.

## Frontend

FrostFrontend provides a consistent settings menu with five sections: **Graphics**, **Sound**, **Controls**, **Touch Settings**, and **Updates**. It supports controller and touchscreen navigation, while each game supplies its own settings and behaviour.

## Controls

`FrostControlProfile` lets each game define its own actions and default controller buttons. `FrostButtonMapper` provides button reassignment, saved mappings, and warnings when an action is left unmapped.

The game itself remains responsible for connecting those mappings to gameplay.

## Sound

`FrostSpeakerTest` provides a universal **Left → Right → Both** speaker test using native Android audio output, independently of the game's audio system.

## Integration

Add FrostFrontend as an Android library module (for example, through a Git submodule) and include `implementation project(':frostfrontend')` in the game's Gradle dependencies. The host app defines its own menu items, button mappings, touch controls, and update-checking logic; FrostFrontend provides the shared interface and utilities.

**Validated in:**
- [Arcane: The Armor Collector Android](https://github.com/Frostie77/Arcane-The-Armor-Collector) — original implementation
- [Dad 'n Me Android](https://github.com/Frostie77/Dad-n-Me) — independent game port, tested on AYN Odin 2 Portal

## Development

The repository includes a standalone Android demo for testing the frontend. The library is included in individual game builds, not distributed as a separate app.

## License

FrostFrontend is licensed under the [MIT License](LICENSE).

Copyright © 2026 Frostie77.
