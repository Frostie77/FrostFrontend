# FrostFrontend

FrostFrontend is a reusable frontend library for Android game ports, providing a controller-friendly settings interface, customisable button mapping, and native stereo speaker testing. It is designed to be integrated directly into individual game projects.

Originally developed for [Arcane: The Armor Collector Android](https://github.com/Frostie77/Arcane-The-Armor-Collector), FrostFrontend can be used with different games without depending on a particular game engine.

## Frontend

FrostFrontend provides a consistent settings menu with five sections: **Graphics**, **Sound**, **Controls**, **Touch Settings**, and **Updates**. It supports controller and touchscreen navigation, while each game supplies its own settings and behaviour.

## Controls

`FrostControlProfile` lets each game define its own actions and default controller buttons. `FrostButtonMapper` provides button reassignment, saved mappings, and warnings when an action is left unmapped.

The game itself remains responsible for connecting those mappings to gameplay.

## Sound

`FrostSpeakerTest` provides a universal **Left → Right → Both** speaker test using native Android audio output, independently of the game's audio system.

## Development

The repository includes a standalone Android demo for testing the frontend. The library is intended to be included in game builds rather than distributed as a separate app.

## License

FrostFrontend is licensed under the [MIT License](LICENSE).

Copyright © 2026 Frostie77.
