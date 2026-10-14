package io.github.nacvark.hudengine.paper;

import io.github.nacvark.hudengine.core.compile.Encoding;
import io.github.nacvark.hudengine.core.compile.HudPackCompiler;
import io.github.nacvark.hudengine.core.model.Compiled;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thanh máu lơ lửng trên đầu player và mob.
 *
 * Có glyph (PNG đã biên dịch) thì vẽ ảnh; không có thì vẽ chữ "❤ hp/max" bằng font mặc định,
 * để thử được khi chưa có tài nguyên nào.
 */
final class MobBarService implements Listener {

    private static final long TICK_PERIOD = 5L;
    private static final float TEXT_SCALE = 1.0f;
    private static final float TEXT_OFFSET = 0.3f;

    record Settings(boolean mobs, boolean players, int showTicks, Set<EntityType> blacklist,
                    boolean look, boolean textOnly) {
    }

    /** frames rỗng nghĩa là chế độ chữ. */
    private record Loaded(Key font, List<Compiled.Glyph> frames, float scale, float offset,
                          Settings settings) {
        boolean textMode() {
            return frames.isEmpty() || settings.textOnly();
        }
    }

    private static final class Bar {
        final TextDisplay display;
        final Map<UUID, Long> viewers = new ConcurrentHashMap<>();
        volatile ScheduledTask task;
        volatile String last = "";

        Bar(TextDisplay display) {
            this.display = display;
        }
    }

    private final Plugin plugin;
    private final PluginLogger log;
    private final Map<UUID, Bar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> lookTasks = new ConcurrentHashMap<>();
    private volatile Loaded loaded;

    MobBarService(Plugin plugin, PluginLogger log) {
        this.plugin = plugin;
        this.log = log;
    }

    void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getOnlinePlayers().forEach(this::attachLook);
    }

    void stop() {
        HandlerList.unregisterAll(this);
        lookTasks.values().forEach(ScheduledTask::cancel);
        lookTasks.clear();
        removeAll();
    }

    /**
     * @param pack có thể null: khi đó dùng chế độ chữ
     * @param settings null nghĩa là tắt hẳn tính năng
     */
    void configure(HudPackCompiler.MobBars pack, String barKey, Settings settings) {
        if (settings == null) {
            loaded = null;
            removeAll();
            return;
        }
        HudPackCompiler.MobBarGlyphs glyphs =
            pack == null || barKey == null ? null : pack.bars().get(barKey);
        removeAll(); // bar cũ mang scale/offset/chế độ của cấu hình cũ
        if (glyphs == null || glyphs.frames().isEmpty()) {
            loaded = new Loaded(Key.key("minecraft:default"), List.of(),
                TEXT_SCALE, TEXT_OFFSET, settings);
        } else {
            loaded = new Loaded(Key.key(pack.font()), glyphs.frames(),
                (float) glyphs.scale(), (float) glyphs.offset(), settings);
        }
    }

    /* ---------------- events ---------------- */

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        attachLook(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        Loaded current = loaded;
        if (current == null || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Player viewer = attacker(event);
        if (viewer == null || viewer.equals(target) || isBedrock(viewer) || !allowed(current, target)) {
            return;
        }
        target.getScheduler().run(plugin, task -> show(current, target, viewer), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        remove(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        ScheduledTask look = lookTasks.remove(id);
        if (look != null) {
            look.cancel();
        }
        remove(id);
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
            && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private static boolean allowed(Loaded current, LivingEntity target) {
        Settings s = current.settings();
        if (target instanceof Player) {
            return s.players();
        }
        return s.mobs() && !s.blacklist().contains(target.getType());
    }

    private static boolean isBedrock(Player player) {
        return player.getUniqueId().getMostSignificantBits() == 0;
    }

    /* ---------------- nhìn vào là hiện ---------------- */

    /** Mỗi player một ticker; gọi lại thì hủy cái cũ nên reload không nhân đôi. */
    private void attachLook(Player player) {
        ScheduledTask previous = lookTasks.remove(player.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, t -> {
            Loaded current = loaded;
            if (current == null || !current.settings().look() || isBedrock(player)) {
                return;
            }
            var hit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(),
                20, 0.2, e -> e instanceof LivingEntity && !e.equals(player));
            if (hit != null && hit.getHitEntity() instanceof LivingEntity target
                && allowed(current, target)) {
                target.getScheduler().run(plugin, x -> show(current, target, player), null);
            }
        }, () -> lookTasks.remove(player.getUniqueId()), 1L, TICK_PERIOD);
        if (task != null) {
            lookTasks.put(player.getUniqueId(), task);
        }
    }

    /* ---------------- show / tick / remove ---------------- */

    /** Chạy trên luồng của {@code target}. */
    private void show(Loaded current, LivingEntity target, Player viewer) {
        if (!target.isValid()) {
            return;
        }
        Bar bar = bars.get(target.getUniqueId());
        boolean fresh = bar == null;
        if (fresh) {
            bar = create(current, target);
            bars.put(target.getUniqueId(), bar);
        }
        bar.viewers.put(viewer.getUniqueId(),
            System.currentTimeMillis() + current.settings().showTicks() * 50L);

        if (fresh || !bar.viewers.containsKey(viewer.getUniqueId())) {
            final TextDisplay display = bar.display;
            viewer.getScheduler().run(plugin, task -> viewer.showEntity(plugin, display), null);
        } else {
            // Đã có bar: vẫn gọi showEntity (rẻ, idempotent) phòng viewer mới nhìn vào.
            final TextDisplay display = bar.display;
            viewer.getScheduler().run(plugin, task -> viewer.showEntity(plugin, display), null);
        }
    }

    private Bar create(Loaded current, LivingEntity target) {
        TextDisplay display = target.getWorld().spawn(target.getLocation(), TextDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(current.textMode());
            d.setLineWidth(1000);
            d.setTransformation(new Transformation(
                new Vector3f(0, current.offset(), 0),
                new AxisAngle4f(),
                new Vector3f(current.scale()),
                new AxisAngle4f()));
        });
        target.addPassenger(display);

        Bar bar = new Bar(display);
        bar.task = target.getScheduler().runAtFixedRate(plugin,
            task -> tick(target, bar),
            () -> remove(target.getUniqueId()),
            1L, TICK_PERIOD);
        return bar;
    }

    @SuppressWarnings("deprecation") // getMaxHealth: cùng lý do với BuiltInValues
    private void tick(LivingEntity target, Bar bar) {
        Loaded current = loaded;
        UUID id = target.getUniqueId();
        if (current == null || !target.isValid() || target.getHealth() <= 0) {
            remove(id);
            return;
        }
        long now = System.currentTimeMillis();
        bar.viewers.entrySet().removeIf(entry -> {
            if (entry.getValue() > now) {
                return false;
            }
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null) {
                viewer.getScheduler().run(plugin,
                    task -> viewer.hideEntity(plugin, bar.display), null);
            }
            return true;
        });
        if (bar.viewers.isEmpty()) {
            remove(id);
            return;
        }

        double health = target.getHealth();
        double max = target.getMaxHealth();

        String key;
        Component text;
        if (current.textMode()) {
            key = (int) Math.ceil(health) + "/" + (int) Math.ceil(max);
            text = Component.text("\u2764 " + key, NamedTextColor.RED);
        } else {
            double ratio = max > 0 ? Math.clamp(health / max, 0.0, 1.0) : 0;
            int frame = (int) Math.ceil(ratio * current.frames().size());
            if (frame <= 0) {
                remove(id);
                return;
            }
            key = "f" + frame;
            int cp = current.frames().get(Math.min(frame, current.frames().size()) - 1).cp();
            text = Component.text(Encoding.CharPool.str(cp)).font(current.font());
        }
        if (!key.equals(bar.last)) {
            bar.last = key;
            bar.display.text(text);
        }
    }

    private void remove(UUID id) {
        Bar bar = bars.remove(id);
        if (bar == null) {
            return;
        }
        ScheduledTask task = bar.task;
        if (task != null) {
            task.cancel();
        }
        try {
            bar.display.getScheduler().run(plugin, t -> bar.display.remove(), null);
        } catch (RuntimeException e) {
            bar.display.remove();
        }
    }

    private void removeAll() {
        List.copyOf(bars.keySet()).forEach(this::remove);
    }
}
