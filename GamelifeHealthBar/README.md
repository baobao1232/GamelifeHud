# GamelifeHealthBar

Standalone Paper/Purpur plugin for Minecraft 1.21.x. It does not depend on HUDEngine, GamelifeHUD, their compiler, or their resource pack.

## Features
- Shows a health bar above living entities when a player damages them.
- Optionally shows a bar when a player looks at an entity.
- Can display bars for mobs and players separately.
- Per-viewer visibility and automatic timeout.
- Configurable look distance, update interval, height offset, scale, and mob blacklist.
- Uses Minecraft's built-in text glyphs; this initial standalone version does not require or generate a resource pack.

## Build
Requires JDK 21 and Maven:

```bash
mvn clean package
```

Copy `target/GamelifeHealthBar.jar` to the server's `plugins/` folder and restart.

## Command
- `/healthbar reload` — reload configuration (permission: `gamelifehealthbar.admin`, default OP).

## Configuration
Edit `plugins/GamelifeHealthBar/config.yml`. Set `show-on-damage` or `show-on-look` to `false` to disable either trigger. Add entity type names to `blacklist` to exclude mobs.
