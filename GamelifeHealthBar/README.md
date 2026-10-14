# GamelifeHealthBar

A standalone Paper/Purpur plugin for Minecraft 1.21.8+ (Java 21). It does not depend on HUDEngine or GamelifeHUD.

## Features
- Generates its own pixel-art bitmap-font resource pack at startup.
- Packages `pack.mcmeta`, bitmap texture PNG, and font JSON into `plugins/GamelifeHealthBar/healthbar-pack.zip`.
- Optionally hosts that ZIP from the plugin's own lightweight HTTP server.
- Sends the resource pack to players on join, with SHA-1 hash and configurable required/optional status.
- Displays a pixel health bar above mobs or players when damaged or looked at.
- Configurable visibility triggers, timeout, look distance, offset, scale, and mob blacklist.

## Build
Requires JDK 21 and Maven:

```bash
mvn clean package
```

Build output: `target/GamelifeHealthBar.jar`. Put that single JAR into the server's `plugins/` folder and restart. No GamelifeHUD jar or separate pack compiler is needed.

## Resource pack delivery
Default config hosts the pack on TCP port 12001. Change `resource-pack.host.public-url` to a URL players can reach from outside your server, e.g. `http://play.example.com:12001/healthbar.zip`. If hosting at home, forward the port in your router and allow it through the firewall. If you already host the pack elsewhere, set `resource-pack.host.enabled: false` and set `resource-pack.url` to that URL.

`resource-pack.auto-send: true` sends the pack on join. `resource-pack.require-pack: true` makes pack acceptance mandatory. Use `/healthbar reload` after changing config.

## Commands
- `/healthbar reload` — regenerate pack, restart hosting, and reload settings (permission `gamelifehealthbar.admin`, default OP).

## Pack details
- Namespace: `gamelifehealthbar`
- Font: `gamelifehealthbar:healthbar`
- Private-use glyphs U+E000–U+E00A map to eleven health levels (empty through full).
