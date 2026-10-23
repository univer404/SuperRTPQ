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
    private static final String RTP_GUI = "§5§lRandom Teleport";
    private static final String QUEUE_GUI = "§5§lRTP Queue";
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
            if (args.length > 0 && isAdminAction(args[0])) return handleAdmin(sender, args);
            if (!(sender instanceof Player player)) { messages.send(sender, "player-only"); return true; }
            if (!player.hasPermission("superrtpq.queue.use")) { messages.send(player, "no-permission"); return true; }
            String action = args.length == 0 ? "gui" : args[0].toLowerCase();
            switch (action) {
                case "join" -> queue.join(player);
                case "leave" -> queue.leave(player);
                case "status" -> sendQueueStatus(player);
                case "gui", "menu" -> openQueueGui(player);
                default -> player.sendMessage("§dRTP Queue §7Usage: §f/rtpq [gui|join|leave|status]");
            }
            return true;
        }

        if (!(sender instanceof Player player)) { messages.send(sender, "player-only"); return true; }
        if (!player.hasPermission("superrtpq.use")) { messages.send(player, "no-permission"); return true; }
        String action = args.length == 0 ? "gui" : args[0].toLowerCase();
        if (action.equals("gui") || action.equals("menu")) {
            openRtpGui(player);
        } else if (action.equals("now") || action.equals("random")) {
            performInstantRtp(player);
        } else {
            player.sendMessage("§dRandom Teleport §7Usage: §f/rtp [gui|now]");
        }
        return true;
    }

    private boolean isAdminAction(String action) {
        return List.of("admin", "status", "clearqueue", "remove", "forceteleport", "reload", "debug").contains(action.toLowerCase());
    }

    private void performInstantRtp(Player player) {
        if (!player.hasPermission("superrtpq.bypass.cooldown")) {
            long remaining = plugin.getQueueManager().getCooldownRemaining(player.getUniqueId());
            if (remaining > 0) {
                messages.send(player, "cooldown", Map.of("seconds", String.valueOf((remaining + 999) / 1000)));
                return;
            }
        }
        var world = teleports.findConfiguredWorld();
        if (world == null) { messages.send(player, "no-world"); return; }
        messages.send(player, "searching");
        var destination = teleports.findSafeLocation(world);
        if (destination == null) { messages.send(player, "failed"); return; }
        if (teleports.teleport(player, destination)) {
            plugin.getQueueManager().applyCooldown(player);
            messages.send(player, "teleported");
        } else messages.send(player, "failed");
    }

    private void openRtpGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(RTP_GUI));
        inv.setItem(11, item(Material.ENDER_PEARL, "§d§lRandom Teleport", "§7Teleport to a random safe location.", "§eThis is instant RTP, not the queue.", "§aClick to teleport"));
        inv.setItem(15, item(Material.BARRIER, "§cClose", "§7Close this menu."));
        player.openInventory(inv);
    }

    private void openQueueGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(QUEUE_GUI));
        int position = queue.position(player.getUniqueId());
        inv.setItem(10, item(Material.LIME_DYE, "§a§lJoin Queue", "§7Join the RTP queue.", "§7Your position: §f" + (position == 0 ? "Not queued" : position), "§aClick to join"));
        inv.setItem(13, item(Material.PAPER, "§d§lQueue Status", "§7Players waiting: §f" + queue.size(), "§7Your position: §f" + (position == 0 ? "Not queued" : position)));
        inv.setItem(16, item(Material.RED_DYE, "§c§lLeave Queue", "§7Leave the RTP queue.", "§cClick to leave"));
        inv.setItem(22, item(Material.COMPASS, "§b§lRandom Teleport", "§7For instant RTP, use §f/rtp§7.", "§7This menu is only for queue actions."));
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
        String title = event.getView().getTitle();
        if (!title.equals(RTP_GUI) && !title.equals(QUEUE_GUI)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (title.equals(RTP_GUI)) {
            if (event.getRawSlot() == 11) { player.closeInventory(); performInstantRtp(player); }
            else if (event.getRawSlot() == 15) player.closeInventory();
            return;
        }
        switch (event.getRawSlot()) {
            case 10 -> { player.closeInventory(); queue.join(player); }
            case 13 -> { player.closeInventory(); sendQueueStatus(player); }
            case 16 -> { player.closeInventory(); queue.leave(player); }
            case 22 -> { player.closeInventory(); player.sendMessage("§7Use §f/rtp §7for instant random teleport."); }
            default -> { }
        }
    }

    private void sendQueueStatus(Player player) {
        messages.send(player, "status", Map.of("size", String.valueOf(queue.size()),
                "max", String.valueOf(plugin.getConfig().getInt("queue.max-size", 100)),
                "position", String.valueOf(queue.position(player.getUniqueId()))));
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("superrtpq.admin")) { messages.send(sender, "no-permission"); return true; }
        int offset = args[0].equalsIgnoreCase("admin") ? 1 : 0;
        if (args.length <= offset) { sender.sendMessage("§dSuperRTPQ §7/rtpq admin <status|clearqueue|remove|forceteleport|reload|debug>"); return true; }
        String action = args[offset].toLowerCase();
        String[] a = java.util.Arrays.copyOfRange(args, offset, args.length);
        switch (action) {
            case "status" -> sender.sendMessage("§dSuperRTPQ §7Queue: §f" + queue.size() + "§7, RTP world: §f" + (teleports.findConfiguredWorld() == null ? "NOT FOUND" : teleports.findConfiguredWorld().getName()));
            case "clearqueue" -> { queue.clearQueue(); messages.send(sender, "queue-cleared"); }
            case "remove" -> {
                if (a.length < 2) { sender.sendMessage("§cUsage: /rtpq admin remove <player>"); break; }
                Player target = Bukkit.getPlayerExact(a[1]);
                if (target == null || !queue.remove(target)) sender.sendMessage("§cPlayer is offline or not queued.");
                else messages.send(sender, "player-removed", Map.of("player", target.getName()));
            }
            case "forceteleport" -> {
                if (a.length < 2) { sender.sendMessage("§cUsage: /rtpq admin forceteleport <player>"); break; }
                Player target = Bukkit.getPlayerExact(a[1]);
                if (target == null) sender.sendMessage("§cPlayer not found.");
                else queue.forceTeleport(target);
            }
            case "reload" -> { plugin.reloadPlugin(); messages.send(sender, "reload"); }
            case "debug" -> {
                boolean enabled = !plugin.isDebugEnabled();
                plugin.getConfig().set("debug", enabled); plugin.saveConfig(); plugin.reloadPlugin();
                messages.send(sender, enabled ? "debug-on" : "debug-off");
            }
            default -> sender.sendMessage("§dSuperRTPQ §7/rtpq admin <status|clearqueue|remove|forceteleport|reload|debug>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("rtpq")) {
            if (args.length == 1) return matching(args[0], List.of("gui", "join", "leave", "status", "admin"));
            if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return matching(args[1], List.of("status", "clearqueue", "remove", "forceteleport", "reload", "debug"));
        } else if (args.length == 1) return matching(args[0], List.of("gui", "now"));
        return List.of();
    }

    private List<String> matching(String input, List<String> options) {
        String prefix = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) if (option.toLowerCase().startsWith(prefix)) result.add(option);
        return result;
    }
}