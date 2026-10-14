package com.gamelife.healthbar;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class GamelifeHealthBarPlugin extends JavaPlugin {
    private HealthBarService healthBarService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        healthBarService = new HealthBarService(this);
        healthBarService.start();
        getLogger().info("GamelifeHealthBar enabled.");
    }

    @Override
    public void onDisable() {
        if (healthBarService != null) {
            healthBarService.stop();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("healthbar")) {
            return false;
        }
        if (!sender.hasPermission("gamelifehealthbar.admin")) {
            sender.sendMessage("§cBạn không có quyền sử dụng lệnh này.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            healthBarService.reloadSettings();
            sender.sendMessage("§aĐã tải lại cấu hình GamelifeHealthBar.");
            return true;
        }
        sender.sendMessage("§eDùng: /healthbar reload");
        return true;
    }
}