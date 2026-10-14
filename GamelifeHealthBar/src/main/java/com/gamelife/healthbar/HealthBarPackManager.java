package com.gamelife.healthbar;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class HealthBarPackManager implements Listener {
    private static final int FRAME_COUNT = 11;
    private static final int CELL_WIDTH = 80;
    private static final int IMAGE_HEIGHT = 10;
    private final GamelifeHealthBarPlugin plugin;
    private HttpServer httpServer;
    private Path zipPath;
    private byte[] sha1;
    private String packUrl;

    HealthBarPackManager(GamelifeHealthBarPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
            plugin.getLogger().info("Resource pack delivery is disabled.");
            return;
        }
        try {
            zipPath = plugin.getDataFolder().toPath().resolve("healthbar-pack.zip");
            Files.createDirectories(zipPath.getParent());
            byte[] zip = buildPack();
            Files.write(zipPath, zip);
            sha1 = MessageDigest.getInstance("SHA-1").digest(zip);
            startHostIfEnabled();
            String configuredUrl = plugin.getConfig().getString("resource-pack.url", "").trim();
            if (!configuredUrl.isEmpty()) {
                packUrl = configuredUrl;
            } else {
                packUrl = plugin.getConfig().getString("resource-pack.host.public-url", "").trim();
            }
            if (packUrl.isEmpty() || packUrl.contains("YOUR_SERVER_IP")) {
                plugin.getLogger().warning("Resource pack was generated at " + zipPath
                    + " but public URL is not configured. Set resource-pack.url or resource-pack.host.public-url.");
                packUrl = null;
            }
            Bukkit.getPluginManager().registerEvents(this, plugin);
            if (packUrl != null && plugin.getConfig().getBoolean("resource-pack.auto-send", true)) {
                for (Player player : Bukkit.getOnlinePlayers()) send(player);
            }
            plugin.getLogger().info("Generated independent healthbar resource pack: " + zipPath);
        } catch (Exception exception) {
            plugin.getLogger().severe("Could not prepare healthbar resource pack: " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    void reload() {
        stop();
        HandlerUnregister.unregister(this);
        start();
    }

    private void startHostIfEnabled() throws IOException {
        if (!plugin.getConfig().getBoolean("resource-pack.host.enabled", true)) return;
        String bind = plugin.getConfig().getString("resource-pack.host.bind", "0.0.0.0");
        int port = Math.max(1, plugin.getConfig().getInt("resource-pack.host.port", 12001));
        String path = plugin.getConfig().getString("resource-pack.host.path", "/healthbar.zip");
        if (!path.startsWith("/")) path = "/" + path;
        final String endpoint = path;
        httpServer = HttpServer.create(new InetSocketAddress(bind, port), 0);
        httpServer.createContext(endpoint, exchange -> servePack(exchange, endpoint));
        httpServer.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "GamelifeHealthBar-PackHost");
            thread.setDaemon(true);
            return thread;
        }));
        httpServer.start();
        plugin.getLogger().info("Healthbar pack host listening on " + bind + ":" + port + endpoint);
    }

    private void servePack(HttpExchange exchange, String expectedPath) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, HEAD");
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        if (!expectedPath.equals(exchange.getRequestURI().getPath())) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        if (zipPath == null || !Files.isRegularFile(zipPath)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/zip");
        headers.set("Content-Disposition", "attachment; filename=\"healthbar-pack.zip\"");
        headers.set("Cache-Control", "public, max-age=300");
        headers.set("Access-Control-Allow-Origin", "*");
        long size = Files.size(zipPath);
        if ("HEAD".equals(method)) {
            headers.set("Content-Length", Long.toString(size));
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, size);
            try (var output = exchange.getResponseBody()) {
                Files.copy(zipPath, output);
            }
        }
        exchange.close();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("resource-pack.auto-send", true)) {
            send(event.getPlayer());
        }
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!plugin.getConfig().getBoolean("resource-pack.require-pack", false)) return;
        switch (event.getStatus()) {
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                event.getPlayer().kick(Component.text("Bạn cần tải resource pack của GamelifeHealthBar để vào máy chủ."));
            }
            default -> { }
        }
    }

    private void send(Player player) {
        if (packUrl == null || sha1 == null || !plugin.getConfig().getBoolean("resource-pack.enabled", true)) return;
        boolean required = plugin.getConfig().getBoolean("resource-pack.require-pack", false);
        String prompt = plugin.getConfig().getString("resource-pack.prompt", "GamelifeHealthBar resource pack");
        player.setResourcePack(packUrl, sha1.clone(), required, Component.text(prompt));
    }

    private byte[] buildPack() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addEntry(zip, "pack.mcmeta", """
                {
                  "pack": {
                    "pack_format": 64,
                    "description": "GamelifeHealthBar standalone pixel health bar"
                  }
                }
                """);
            addEntry(zip, "assets/gamelifehealthbar/font/healthbar.json", createFontJson());
            zip.putNextEntry(new ZipEntry("assets/gamelifehealthbar/textures/font/healthbar.png"));
            ImageIO.write(createSpriteSheet(), "png", zip);
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private String createFontJson() {
        StringBuilder chars = new StringBuilder();
        for (int i = 0; i < FRAME_COUNT; i++) chars.append((char) (0xE000 + i));
        return """
            {
              "providers": [
                {
                  "type": "bitmap",
                  "file": "gamelifehealthbar:font/healthbar.png",
                  "height": 10,
                  "ascent": 8,
                  "chars": ["%s"]
                }
              ]
            }
            """.formatted(chars);
    }

    private BufferedImage createSpriteSheet() {
        BufferedImage image = new BufferedImage(CELL_WIDTH * FRAME_COUNT, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            int x = frame * CELL_WIDTH;
            // Dark outer border and dark empty track.
            graphics.setColor(new java.awt.Color(12, 12, 12, 255));
            graphics.fillRect(x, 1, CELL_WIDTH, 8);
            graphics.setColor(new java.awt.Color(65, 65, 65, 255));
            graphics.fillRect(x + 1, 2, CELL_WIDTH - 2, 6);
            for (int segment = 0; segment < 10; segment++) {
                int sx = x + 2 + segment * 7;
                graphics.setColor(segment < frame
                    ? new java.awt.Color(220, 45, 45, 255)
                    : new java.awt.Color(35, 35, 35, 255));
                graphics.fillRect(sx, 3, 6, 4);
                graphics.setColor(segment < frame
                    ? new java.awt.Color(255, 105, 105, 255)
                    : new java.awt.Color(82, 82, 82, 255));
                graphics.fillRect(sx, 3, 6, 1);
            }
        }
        graphics.dispose();
        return image;
    }

    private static void addEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static final class HandlerUnregister {
        private static void unregister(HealthBarPackManager listener) {
            org.bukkit.event.HandlerList.unregisterAll(listener);
        }
    }
}