# GamelifeHealthBar

Standalone Paper/Purpur plugin for Minecraft 1.21+ that displays a pixel-art health bar above living entities. It does **not** depend on HUDEngine or GamelifeHUD.

## Features
- Pixel-art bar above mobs and players, based on current/max health.
- Show on damage and optionally while looking at an entity.
- Per-viewer visibility and automatic timeout.
- Configure mobs/players, timeout, look distance, scale, offset, and entity blacklist.
- Generates its own resource pack and can serve it over HTTP; no HUDEngine pack compiler is used.

## Build
Requires JDK 21 and Maven:

```bash
mvn clean package
```

Copy `target/GamelifeHealthBar.jar` to the server's `plugins/` directory and restart.

## Resource pack hosting
1. Set `resource-pack.public-url` to a URL reachable by players, for example `http://play.example.com:12001/healthbar.zip`.
2. Open/forward TCP port 12001 to the Minecraft host if the server is behind a router.
3. Install the plugin and restart. It generates the pack and serves it at the configured path.
4. Keep `required: false` if players should be allowed to decline the pack.

The generated pack contains its own bitmap font. It is independent from the HUDEngine resource pack. If another pack overrides the same font namespace (unlikely), resolve the pack order on the client.

## Commands
- `/healthbar reload` — reload configuration and regenerate the pack (permission: `gamelifehealthbar.admin`).

## Notes
- Java 21, Paper/Purpur 1.21.x API.
- The HTTP endpoint serves only the configured ZIP path and supports GET/HEAD.
