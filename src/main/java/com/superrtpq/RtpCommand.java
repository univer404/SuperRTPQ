package com.superrtpq;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
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
    private static final String RTP_GUI = "Random Teleport";
    private static final String QUEUE_GUI = "RTP Queue";
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
            performInstantRtp(player, "overworld");
        } else if (List.of("overworld", "nether", "end").contains(action)) {
            performInstantRtp(player, action);
        } else {
            player.sendMessage("§dRandom Teleport §7Usage: §f/rtp [gui|overworld|nether|end]");
        }
        return true;
    }

    private boolean isAdminAction(String action) {
        return List.of("admin", "status", "clearqueue", "remove", "forceteleport", "reload", "debug").contains(action.toLowerCase());
    }

    private void performInstantRtp(Player player, String dimension) {
        if (!player.hasPermission("superrtpq.bypass.cooldown")) {
            long remaining = plugin.getQueueManager().getCooldownRemaining(player.getUniqueId());
            if (remaining > 0) {
                messages.send(player, "cooldown", Map.of("seconds", String.valueOf((remaining + 999) / 1000)));
                return;
            }
        }
        World world = getDimensionWorld(dimension);
        if (world == null) {
            player.sendMessage("§cThe " + dimensionName(dimension) + " world is not available. Check rtp.dimension-worlds in config.yml.");
            return;
        }
        messages.send(player, "searching");
        var destination = teleports.findSafeLocation(world);
        if (destination == null) { messages.send(player, "failed"); return; }
        if (teleports.teleport(player, destination)) {
            plugin.getQueueManager().applyCooldown(player);
            messages.send(player, "teleported");
        } else messages.send(player, "failed");
    }

    private World getDimensionWorld(String dimension) {
        String configured = plugin.getConfig().getString("rtp.dimension-worlds." + dimension, defaultWorldName(dimension));
        return Bukkit.getWorld(configured);
    }

    private String defaultWorldName(String dimension) {
        return switch (dimension) {
            case "nether" -> "world_nether";
            case "end" -> "world_the_end";
            default -> "world";
        };
    }

    private String dimensionName(String dimension) {
        return switch (dimension) {
            case "nether" -> "Nether";
            case "end" -> "The End";
            default -> "Overworld";
        };
    }

    private String borderDescription(World world) {
        if (world == null) return "§cWorld not loaded / not configured";
        double size = world.getWorldBorder().getSize();
        return "§7Border: §f" + String.format(java.util.Locale.US, "%.0f", size) + " × " + String.format(java.util.Locale.US, "%.0f", size) + " blocks";
    }

    private ItemStack dimensionItem(Material material, String dimension, String subtitle, String badge) {
        World world = getDimensionWorld(dimension);
        String worldName = world == null ? defaultWorldName(dimension) : world.getName();
        return item(material, "§d§l" + dimensionName(dimension),
                "§8" + badge,
                "§7" + subtitle,
                "§8&m----------------",
                "§7Target world: §f" + worldName,
                borderDescription(world),
                "§7Safe-location checks enabled",
                world == null ? "§cWorld is not loaded" : "§a▶ Click to begin RTP");
    }

    private void openRtpGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(RTP_GUI).decoration(TextDecoration.ITALIC, false));
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot < 9 || slot >= 18) inv.setItem(slot, item(Material.PURPLE_STAINED_GLASS_PANE, "§5 ", "§8SuperRTPQ"));
        }
        inv.setItem(10, dimensionItem(Material.GRASS_BLOCK, "overworld", "Explore a random safe spot.", "§aNORMAL WORLD"));
        inv.setItem(13, dimensionItem(Material.NETHERRACK, "nether", "Find a random spot in the Nether.", "§cDANGEROUS DIMENSION"));
        inv.setItem(16, dimensionItem(Material.END_STONE, "end", "Travel to a random spot in The End.", "§5THE END"));
        inv.setItem(22, item(Material.BARRIER, "§cClose Menu", "§7Return to the game."));
        player.openInventory(inv);
    }

    private void openQueueGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(QUEUE_GUI).decoration(TextDecoration.ITALIC, false));
        int position = queue.position(player.getUniqueId());
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot < 9 || slot >= 18) inv.setItem(slot, item(Material.BLACK_STAINED_GLASS_PANE, "§8 ", "§8SuperRTPQ"));
        }
        inv.setItem(10, item(Material.LIME_DYE, "§a§lFind a Match", "§7Queue for a random 1v1 encounter.", "§7Queue size: §f" + queue.size(), "§7Your position: §f" + (position == 0 ? "Not queued" : "#" + position), "§8&m----------------", "§a▶ Click to join"));
        inv.setItem(13, item(Material.PLAYER_HEAD, "§d§lQueue Status", "§7Players waiting: §f" + queue.size(), "§7Your position: §f" + (position == 0 ? "Not queued" : "#" + position), "§8Matches start when two players are ready."));
        inv.setItem(16, item(Material.RED_DYE, "§c§lLeave Queue", "§7Cancel your matchmaking search.", "§c▶ Click to leave"));
        inv.setItem(22, item(Material.COMPASS, "§b§lInstant RTP", "§7Explore without entering matchmaking.", "§7Open the dimension selector.", "§a▶ Click to open"));
        player.openInventory(inv);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(lore).stream().map(line -> LegacyComponentSerializer.legacySection().deserialize(line).decoration(TextDecoration.ITALIC, false)).toList());
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
            switch (event.getRawSlot()) {
                case 10 -> { player.closeInventory(); performInstantRtp(player, "overworld"); }
                case 13 -> { player.closeInventory(); performInstantRtp(player, "nether"); }
                case 16 -> { player.closeInventory(); performInstantRtp(player, "end"); }
                case 22 -> player.closeInventory();
                default -> { }
            }
            return;
        }
        switch (event.getRawSlot()) {
            case 10 -> { player.closeInventory(); queue.join(player); }
            case 13 -> { player.closeInventory(); sendQueueStatus(player); }
            case 16 -> { player.closeInventory(); queue.leave(player); }
            case 22 -> { player.closeInventory(); openRtpGui(player); }
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
        } else if (args.length == 1) return matching(args[0], List.of("gui", "overworld", "nether", "end", "now"));
        return List.of();
    }

    private List<String> matching(String input, List<String> options) {
        String prefix = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) if (option.toLowerCase().startsWith(prefix)) result.add(option);
        return result;
    }
}