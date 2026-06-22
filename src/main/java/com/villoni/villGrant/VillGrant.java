package com.villoni.villGrant;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

public class VillGrant extends JavaPlugin implements TabCompleter {

    private File cooldownFile;
    private FileConfiguration cooldownConfig;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCooldownFile();

        Objects.requireNonNull(getCommand("grant"))
                .setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        getLogger().info("GrantPlugin disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cКоманду может использовать только игрок."));
            return true;
        }

        if (!player.hasPermission("grant.use")) {
            player.sendMessage(msg("no-permission"));
            return true;
        }

        String requiredPermission =
                getConfig().getString("required-permission", "group.elite");

        if (!player.hasPermission(requiredPermission)) {
            player.sendMessage(msg("elite-only"));
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(color("&cИспользование: /grant <ник> <killer|marauder>"));
            return true;
        }

        String targetName = args[0];
        String group = args[1].toLowerCase();

        if (!getConfig().contains("groups." + group)) {
            player.sendMessage(msg("invalid-group"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        UUID senderUUID = player.getUniqueId();
        long now = System.currentTimeMillis();

        cooldowns.putIfAbsent(senderUUID, new HashMap<>());

        String path = senderUUID + "." + group;

        long expires = cooldownConfig.getLong(path, 0L);

        if (expires > System.currentTimeMillis()) {

            long remaining =
                    (expires - System.currentTimeMillis()) / 1000;

            player.sendMessage(
                    msg("cooldown")
                            .replace("{group}", capitalize(group))
                            .replace("{time}", formatTime(remaining))
            );

            return true;
        }

        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "lp user " + target.getName() + " parent add " + group
        );

        int days = getConfig().getInt(
                "groups." + group + ".cooldown-days",
                30
        );

        long cooldownEnd =
                now + TimeUnit.DAYS.toMillis(days);

        cooldownConfig.set(
                senderUUID + "." + group,
                cooldownEnd
        );

        saveCooldownFile();

        player.sendMessage(
                msg("success")
                        .replace("{group}", group)
                        .replace("{player}", target.getName())
        );

        Bukkit.broadcastMessage(
                color(
                        msg("broadcast")
                                .replace("{sender}", player.getName())
                                .replace("{group}", group)
                                .replace("{player}", target.getName())
                )
        );

        if (target.isOnline()) {
            Player online = target.getPlayer();

            if (online != null) {
                online.sendMessage(
                        msg("receive")
                                .replace("{group}", group)
                );
            }
        }

        return true;
    }

    private String msg(String path) {
        String prefix = getConfig().getString("prefix", "");

        String message =
                getConfig().getString("messages." + path, path);

        message = message.replace("{prefix}", prefix);

        return color(message);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String capitalize(String text) {
        return text.substring(0, 1).toUpperCase() +
                text.substring(1).toLowerCase();
    }

    private String formatTime(long seconds) {

        long days = seconds / 86400;
        seconds %= 86400;

        long hours = seconds / 3600;
        seconds %= 3600;

        long minutes = seconds / 60;
        long secs = seconds % 60;

        StringBuilder builder = new StringBuilder();

        if (days > 0)
            builder.append(days).append(" дн. ");

        if (hours > 0)
            builder.append(hours).append(" ч. ");

        if (minutes > 0)
            builder.append(minutes).append(" мин. ");

        if (secs > 0)
            builder.append(secs).append(" сек.");

        return builder.toString().trim();
    }

    private void loadCooldownFile() {
        cooldownFile = new File(getDataFolder(), "cooldowns.yml");

        if (!cooldownFile.exists()) {
            try {
                getDataFolder().mkdirs();
                cooldownFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
    }

    private void saveCooldownFile() {
        try {
            cooldownConfig.save(cooldownFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        if (args.length == 2) {
            return new ArrayList<>(
                    getConfig()
                            .getConfigurationSection("groups")
                            .getKeys(false)
            );
        }

        return Collections.emptyList();
    }
}