package com.gamelife.healthbar;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class HealthBarService implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Key BAR_FONT = Key.key("gamelifehealthbar", "healthbar");
    private final GamelifeHealthBarPlugin plugin;
    private final Map<UUID, Bar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> lookTasks = new ConcurrentHashMap<>();

    private volatile boolean enabled, showMobs, showPlayers, showOnDamage, showOnLook;
    private volatile int showSeconds, updateTicks;
    private volatile double lookDistance;
    private volatile float offset, scale;
    private volatile Set<String> blacklist;

    private static final class Bar {
        private final TextDisplay display;
        private final Map<UUID, Long> viewers = new ConcurrentHashMap<>();
        private BukkitTask task;
        private String lastText = "";
        private Bar(TextDisplay display) { this.display = display; }
    }

    HealthBarService(GamelifeHealthBarPlugin plugin) {
        this.plugin = plugin;
        loadSettings();
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getOnlinePlayers().forEach(this::attachLookTask);
    }

    void stop() {
        HandlerList.unregisterAll(this);
        lookTasks.values().forEach(BukkitTask::cancel);
        lookTasks.clear();
        for (UUID id : bars.keySet().toArray(UUID[]::new)) removeBar(id);
    }

    void reloadSettings() {
        loadSettings();
        for (UUID id : bars.keySet().toArray(UUID[]::new)) removeBar(id);
        lookTasks.values().forEach(BukkitTask::cancel);
        lookTasks.clear();
        Bukkit.getOnlinePlayers().forEach(this::attachLookTask);
    }

    private void loadSettings() {
        enabled = plugin.getConfig().getBoolean("enabled", true);
        showMobs = plugin.getConfig().getBoolean("mobs", true);
        showPlayers = plugin.getConfig().getBoolean("players", true);
        showOnDamage = plugin.getConfig().getBoolean("show-on-damage", true);
        showOnLook = plugin.getConfig().getBoolean("show-on-look", true);
        showSeconds = Math.max(1, plugin.getConfig().getInt("show-seconds", 3));
        updateTicks = Math.max(1, plugin.getConfig().getInt("update-ticks", 5));
        lookDistance = Math.max(1.0, plugin.getConfig().getDouble("look-distance", 20.0));
        offset = (float) plugin.getConfig().getDouble("offset", 0.35);
        scale = (float) plugin.getConfig().getDouble("scale", 1.0);
        blacklist = Set.copyOf(plugin.getConfig().getStringList("blacklist"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { attachLookTask(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled || !showOnDamage || !(event.getEntity() instanceof LivingEntity target)) return;
        Player viewer = findAttackingPlayer(event.getDamager());
        if (viewer != null && !viewer.equals(target) && isAllowed(target)) show(target, viewer);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) { removeBar(event.getEntity().getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        BukkitTask task = lookTasks.remove(id);
        if (task != null) task.cancel();
        removeBar(id);
        bars.values().forEach(bar -> bar.viewers.remove(id));
    }

    private Player findAttackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean isAllowed(LivingEntity target) {
        if (target instanceof Player) return showPlayers;
        return showMobs && !blacklist.contains(target.getType().name());
    }

    private void attachLookTask(Player player) {
        BukkitTask old = lookTasks.remove(player.getUniqueId());
        if (old != null) old.cancel();
        if (!showOnLook) return;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled || !player.isOnline()) return;
            var hit = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), lookDistance, 0.2,
                entity -> entity instanceof LivingEntity && !entity.equals(player));
            if (hit != null && hit.getHitEntity() instanceof LivingEntity target && isAllowed(target)) show(target, player);
        }, 1L, updateTicks);
        lookTasks.put(player.getUniqueId(), task);
    }

    private void show(LivingEntity target, Player viewer) {
        if (!enabled || !target.isValid() || !viewer.isOnline()) return;
        UUID id = target.getUniqueId();
        Bar bar = bars.get(id);
        if (bar == null || !bar.display.isValid()) {
            TextDisplay display = target.getWorld().spawn(target.getLocation(), TextDisplay.class, d -> {
                d.setVisibleByDefault(false);
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(false);
                d.setLineWidth(200);
                d.setTransformation(new Transformation(new Vector3f(0, offset, 0),
                    new AxisAngle4f(), new Vector3f(scale), new AxisAngle4f()));
            });
            target.addPassenger(display);
            bar = new Bar(display);
            bars.put(id, bar);
            Bar activeBar = bar;
            activeBar.task = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> tick(target, activeBar), 1L, updateTicks);
        }
        bar.viewers.put(viewer.getUniqueId(), System.currentTimeMillis() + showSeconds * 1000L);
        viewer.showEntity(plugin, bar.display);
    }

    private void tick(LivingEntity target, Bar bar) {
        if (!enabled || !target.isValid() || target.isDead() || target.getHealth() <= 0) {
            removeBar(target.getUniqueId());
            return;
        }
        long now = System.currentTimeMillis();
        bar.viewers.entrySet().removeIf(entry -> {
            if (entry.getValue() > now) return false;
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null) viewer.hideEntity(plugin, bar.display);
            return true;
        });
        if (bar.viewers.isEmpty()) {
            removeBar(target.getUniqueId());
            return;
        }

        double max = target.getMaxHealth();
        double health = Math.max(0, Math.min(target.getHealth(), max));
        int frame = max <= 0 ? 0 : (int) Math.ceil(health / max * 10.0);
        frame = Math.max(0, Math.min(10, frame));
        String glyph = String.valueOf((char) (0xE000 + frame));
        Component text = Component.text(glyph).font(BAR_FONT)
            .append(Component.text(" " + (int) Math.ceil(health) + "/" + (int) Math.ceil(max)));
        String cacheKey = frame + ":" + (int) Math.ceil(health) + ":" + (int) Math.ceil(max);
        if (!cacheKey.equals(bar.lastText)) {
            bar.lastText = cacheKey;
            bar.display.text(text);
        }
    }

    private void removeBar(UUID id) {
        Bar bar = bars.remove(id);
        if (bar == null) return;
        if (bar.task != null) bar.task.cancel();
        for (UUID viewerId : bar.viewers.keySet()) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) viewer.hideEntity(plugin, bar.display);
        }
        bar.display.remove();
    }
}