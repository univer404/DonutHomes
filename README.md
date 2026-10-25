# DonutHomes

A Paper/Purpur homes plugin converted from the supplied DonutHomes Skript.

## Requirements
- Minecraft 1.21.11
- Java 21
- Paper or Purpur
- No Skript dependency

## Features
- Fixed-slot homes GUI for slots 1–5.
- Configurable default home limit and permission-based limits.
- YAML persistence in `plugins/DonutHomes/homes.yml`.
- Confirm-before-delete GUI.
- Configurable teleport countdown, cancelled when the player moves.
- Blocked-world list for setting homes.
- MiniMessage RGB/HEX formatting for messages and GUI labels.
- Optional team-home command bridge, configurable in `config.yml`.
- GitHub Actions Maven build with a downloadable JAR artifact.

## Commands
- `/home` or `/homes` — open the homes GUI.
- `/home <1-5>` — teleport to a saved home.
- `/home set` or `/sethome [1-5]` — set a home.
- `/home delete <slot>` or `/delhome <slot>` — open delete confirmation.
- `/donuthomes reload` — reload configuration.

## Permissions
- `donuthomes.use`, `donuthomes.sethome`, `donuthomes.delete`: default true.
- `donuthomes.limit.3` through `donuthomes.limit.5`: unlock higher slots.
- `homes.default`: ensures at least two slots.
- `homes.donutplus`: unlocks all five slots.
- `donuthomes.admin`: operator by default.

## Build
Run `mvn clean verify`. The JAR is `target/DonutHomes.jar`.
GitHub Actions uploads the built JAR as the `DonutHomes` artifact.

## Notes
Team-home actions delegate to the configured command (default: `team home`). A compatible teams plugin must be installed for that command to work. Home data is stored in YAML and is saved after changes and on shutdown.
