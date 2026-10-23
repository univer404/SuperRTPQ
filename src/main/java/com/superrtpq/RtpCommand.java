package com.superrtpq;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RtpCommand implements CommandExecutor, TabCompleter {
    private final SuperRTPQ plugin;
    private final QueueManager queue;
    private final TeleportService teleports;
    private final MessageService messages;

    RtpCommand(SuperRTPQ plugin, QueueManager queue, TeleportService teleports) {
        this.plugin = plugin;
        this.queue = queue;
        this.teleports = teleports;
        this.messages = new MessageService(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rtpq")) {
            return handleAdmin(sender, args);
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("superrtpq.use")) {
            messages.send(player, "no-permission");
            return true;
        }
        String action = args.length == 0 ? "join" : args[0].toLowerCase();
        switch (action) {
            case "join" -> queue.join(player);
            case "leave" -> queue.leave(player);
            case "status" -> messages.send(player, "status", Map.of(
                    "size", String.valueOf(queue.size()),
                    "max", String.valueOf(plugin.getConfig().getInt("queue.max-size", 100)),
                    "position", String.valueOf(queue.position(player.getUniqueId()))));
            case "gui" -> messages.send(player, "not-queued");
            default -> messages.send(player, "admin-usage");
        }
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("superrtpq.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "admin-usage");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "status" -> sender.sendMessage("SuperRTPQ queue size: " + queue.size());
            case "clearqueue" -> {
                queue.clearQueue();
                messages.send(sender, "queue-cleared");
            }
            case "remove" -> {
                if (args.length < 2) {
                    messages.send(sender, "admin-usage");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !queue.remove(target)) {
                    sender.sendMessage("That player is online but not queued, or is not online.");
                } else {
                    messages.send(sender, "player-removed", Map.of("player", target.getName()));
                }
            }
            case "forceteleport" -> {
                if (args.length < 2) {
                    messages.send(sender, "admin-usage");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("Player not found.");
                } else {
                    queue.forceTeleport(target);
                }
            }
            case "reload" -> {
                plugin.reloadPlugin();
                messages.send(sender, "reload");
            }
            case "debug" -> {
                boolean enabled = !plugin.isDebugEnabled();
                plugin.getConfig().set("debug", enabled);
                plugin.saveConfig();
                plugin.reloadPlugin();
                messages.send(sender, enabled ? "debug-on" : "debug-off");
            }
            default -> messages.send(sender, "admin-usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("rtpq")) {
            if (args.length == 1) return matching(args[0], List.of("status", "clearqueue", "remove", "forceteleport", "reload", "debug"));
            if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("forceteleport"))) {
                return matching(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            }
        } else if (args.length == 1) {
            return matching(args[0], List.of("join", "leave", "status", "gui"));
        }
        return List.of();
    }

    private List<String> matching(String input, List<String> options) {
        String prefix = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(prefix)) result.add(option);
        }
        return result;
    }
}