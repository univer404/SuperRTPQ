package com.superrtpq;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RtpCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String GUI_TITLE = "§5§lSuperRTPQ §8» §dRandom Teleport";
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
        if (command.getName().equalsIgnoreCase("rtpq")) return handleAdmin(sender, args);
        if (!(sender instanceof Player player)) { messages.send(sender, "player-only"); return true; }
        if (!player.hasPermission("superrtpq.use")) { messages.send(player, "no-permission"); return true; }
        String action = args.length == 0 ? "gui" : args[0].toLowerCase();
        switch (action) {
            case "join" -> queue.join(player);
            case "leave" -> queue.leave(player);
            case "status" -> sendStatus(player);
            case "gui", "menu" -> openGui(player);
            case "now" -> {
                if (!player.hasPermission("superrtpq.instant")) { messages.send(player, "no-permission"); break; }
                LocationResult.teleport(plugin, teleports, messages, player);
            }
            default -> player.sendMessage("§dSuperRTPQ §7Usage: §f/rtp [join|leave|status|gui|now]");
        }
        return true;
    }

    private void sendStatus(Player player) {
        messages.send(player, "status", Map.of("size", String.valueOf(queue.size()),
                "max", String.valueOf(plugin.getConfig().getInt("queue.max-size", 100)),
                "position", String.valueOf(queue.position(player.getUniqueId()))));
    }

    private void openGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(GUI_TITLE));
        inv.setItem(10, item(Material.ENDER_PEARL, "§d§lJoin RTP Queue",
                "§7Join the random teleport queue.", "§eQueue size: §f" + queue.size(),
                "§eYour position: §f" + (queue.position(player.getUniqueId()) == 0 ? "Not queued" : queue.position(player.getUniqueId())),
                "§aClick to join"));
        inv.setItem(12, item(Material.BARRIER, "§c§lLeave Queue", "§7Cancel your current queue request.", "§eClick to leave"));
        inv.setItem(14, item(Material.COMPASS, "§b§lQueue Status", "§7Players waiting: §f" + queue.size(),
                "§7Your position: §f" + (queue.position(player.getUniqueId()) == 0 ? "Not queued" : queue.position(player.getUniqueId()))));
        inv.setItem(16, item(Material.MAP, "§e§lHow RTP Works", "§7Choose Join to enter the queue.",
                "§7When your turn comes, a safe random", "§7location is searched and you teleport."));
        inv.setItem(22, item(Material.GRAY_STAINED_GLASS_PANE, "§8SuperRTPQ", "§7Standalone RTP + queue system"));
        player.openInventory(inv);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        switch (event.getRawSlot()) {
            case 10 -> { player.closeInventory(); queue.join(player); }
            case 12 -> { player.closeInventory(); queue.leave(player); }
            case 14 -> { player.closeInventory(); sendStatus(player); }
            default -> { }
        }
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("superrtpq.admin")) { messages.send(sender, "no-permission"); return true; }
        if (args.length == 0) { sender.sendMessage("§dSuperRTPQ §7/rtpq <status|clearqueue|remove|forceteleport|reload|debug>"); return true; }
        switch (args[0].toLowerCase()) {
            case "status" -> sender.sendMessage("§dSuperRTPQ §7Queue: §f" + queue.size() + "§7, RTP world: §f" + (teleports.findConfiguredWorld() == null ? "NOT FOUND" : teleports.findConfiguredWorld().getName()));
            case "clearqueue" -> { queue.clearQueue(); messages.send(sender, "queue-cleared"); }
            case "remove" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /rtpq remove <player>"); break; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !queue.remove(target)) sender.sendMessage("§cPlayer is offline or not in the queue.");
                else messages.send(sender, "player-removed", Map.of("player", target.getName()));
            }
            case "forceteleport" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /rtpq forceteleport <player>"); break; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) sender.sendMessage("§cPlayer not found.");
                else queue.forceTeleport(target);
            }
            case "reload" -> { plugin.reloadPlugin(); messages.send(sender, "reload"); }
            case "debug" -> {
                boolean enabled = !plugin.isDebugEnabled();
                plugin.getConfig().set("debug", enabled); plugin.saveConfig(); plugin.reloadPlugin();
                messages.send(sender, enabled ? "debug-on" : "debug-off");
            }
            default -> sender.sendMessage("§dSuperRTPQ §7/rtpq <status|clearqueue|remove|forceteleport|reload|debug>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("rtpq")) {
            if (args.length == 1) return matching(args[0], List.of("status", "clearqueue", "remove", "forceteleport", "reload", "debug"));
            if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("forceteleport")))
                return matching(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        } else if (args.length == 1) return matching(args[0], List.of("join", "leave", "status", "gui", "now"));
        return List.of();
    }

    private List<String> matching(String input, List<String> options) {
        String prefix = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) if (option.toLowerCase().startsWith(prefix)) result.add(option);
        return result;
    }

    private static final class LocationResult {
        static void teleport(SuperRTPQ plugin, TeleportService service, MessageService messages, Player player) {
            var world = service.findConfiguredWorld();
            if (world == null) { messages.send(player, "no-world"); return; }
            messages.send(player, "searching");
            var destination = service.findSafeLocation(world);
            if (destination == null) messages.send(player, "failed");
            else if (service.teleport(player, destination)) messages.send(player, "teleported");
            else messages.send(player, "failed");
        }
    }
}