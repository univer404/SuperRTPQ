package com.superrtpq;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
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
import java.io.File;

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

    private YamlConfiguration guiConfig() {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.exists()) plugin.saveResource("gui.yml", false);
        return YamlConfiguration.loadConfiguration(file);
    }

    private String guiTitle(String menu) {
        return ChatColor.translateAlternateColorCodes('&', guiConfig().getString(menu + ".title",
                menu.equals("rtp") ? "&5&lRandom Teleport" : "&5&lRTP Queue"));
    }

    private String borderDescription(World world) {
        if (world == null) return "&cWorld not loaded / not configured";
        double size = world.getWorldBorder().getSize();
        return "&7Border: &f" + String.format(java.util.Locale.US, "%.0f", size) + " × " + String.format(java.util.Locale.US, "%.0f", size) + " blocks";
    }

    private Material guiMaterial(YamlConfiguration cfg, String path, Material fallback) {
        Material material = Material.matchMaterial(cfg.getString(path, fallback.name()));
        return material == null ? fallback : material;
    }

    private ItemStack configuredItem(YamlConfiguration cfg, String path, Material fallback, Map<String, String> replacements) {
        Material material = guiMaterial(cfg, path + ".material", fallback);
        String name = cfg.getString(path + ".name", "&dSuperRTPQ");
        for (Map.Entry<String, String> entry : replacements.entrySet()) name = name.replace(entry.getKey(), entry.getValue());
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(color(name)).decoration(TextDecoration.ITALIC, false));
        List<String> lore = new ArrayList<>();
        for (String line : cfg.getStringList(path + ".lore")) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) line = line.replace(entry.getKey(), entry.getValue());
            lore.add(color(line));
        }
        meta.lore(lore.stream().map(line -> LegacyComponentSerializer.legacySection().deserialize(line).decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private int itemSlot(YamlConfiguration cfg, String path, int fallback) {
        return cfg.getInt(path + ".slot", fallback);
    }

    private void fillMenu(Inventory inv, YamlConfiguration cfg, String path, Material fallback) {
        if (!cfg.getBoolean(path + ".enabled", true)) return;
        Material material = guiMaterial(cfg, path + ".material", fallback);
        String name = cfg.getString(path + ".name", "&8 ");
        List<String> lore = cfg.getStringList(path + ".lore");
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(color(name)).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> LegacyComponentSerializer.legacySection().deserialize(color(line)).decoration(TextDecoration.ITALIC, false)).toList());
        filler.setItemMeta(meta);
        for (int slot : cfg.getIntegerList(path + ".slots")) {
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, filler);
        }
    }

    private void openRtpGui(Player player) {
        YamlConfiguration cfg = guiConfig();
        int size = cfg.getInt("rtp.size", 27);
        if (size < 9 || size > 54 || size % 9 != 0) size = 27;
        Inventory inv = Bukkit.createInventory(null, size, Component.text(guiTitle("rtp")).decoration(TextDecoration.ITALIC, false));
        fillMenu(inv, cfg, "rtp.filler", Material.PURPLE_STAINED_GLASS_PANE);
        for (String dimension : List.of("overworld", "nether", "end")) {
            Material fallback = switch (dimension) {
                case "nether" -> Material.NETHERRACK;
                case "end" -> Material.END_STONE;
                default -> Material.GRASS_BLOCK;
            };
            World world = getDimensionWorld(dimension);
            String worldName = world == null ? defaultWorldName(dimension) : world.getName();
            Map<String, String> replacements = Map.of("%world%", worldName, "%border%", color(borderDescription(world)));
            String path = "rtp.items." + dimension;
            int fallbackSlot = dimension.equals("overworld") ? 10 : dimension.equals("nether") ? 13 : 16;
            int slot = itemSlot(cfg, path, fallbackSlot);
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, configuredItem(cfg, path, fallback, replacements));
        }
        String closePath = "rtp.items.close";
        int closeSlot = itemSlot(cfg, closePath, 22);
        if (closeSlot >= 0 && closeSlot < inv.getSize()) inv.setItem(closeSlot,
                configuredItem(cfg, closePath, Material.BARRIER, Map.of()));
        player.openInventory(inv);
    }

    private void openQueueGui(Player player) {
        YamlConfiguration cfg = guiConfig();
        int size = cfg.getInt("queue.size", 27);
        if (size < 9 || size > 54 || size % 9 != 0) size = 27;
        Inventory inv = Bukkit.createInventory(null, size, Component.text(guiTitle("queue")).decoration(TextDecoration.ITALIC, false));
        fillMenu(inv, cfg, "queue.filler", Material.BLACK_STAINED_GLASS_PANE);
        int position = queue.position(player.getUniqueId());
        Map<String, String> replacements = Map.of("%queue_size%", String.valueOf(queue.size()),
                "%position%", position == 0 ? "Not queued" : "#" + position);
        String[][] items = {{"join", "LIME_DYE", "10"}, {"status", "PLAYER_HEAD", "13"},
                {"leave", "RED_DYE", "16"}, {"instant-rtp", "COMPASS", "22"}};
        for (String[] item : items) {
            String path = "queue.items." + item[0];
            int slot = itemSlot(cfg, path, Integer.parseInt(item[2]));
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot,
                    configuredItem(cfg, path, Material.valueOf(item[1]), replacements));
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        YamlConfiguration cfg = guiConfig();
        String title = event.getView().getTitle();
        String rtpTitle = ChatColor.stripColor(guiTitle("rtp"));
        String queueTitle = ChatColor.stripColor(guiTitle("queue"));
        if (!ChatColor.stripColor(title).equals(rtpTitle) && !ChatColor.stripColor(title).equals(queueTitle)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (ChatColor.stripColor(title).equals(rtpTitle)) {
            if (event.getRawSlot() == itemSlot(cfg, "rtp.items.overworld", 10)) { player.closeInventory(); performInstantRtp(player, "overworld"); }
            else if (event.getRawSlot() == itemSlot(cfg, "rtp.items.nether", 13)) { player.closeInventory(); performInstantRtp(player, "nether"); }
            else if (event.getRawSlot() == itemSlot(cfg, "rtp.items.end", 16)) { player.closeInventory(); performInstantRtp(player, "end"); }
            else if (event.getRawSlot() == itemSlot(cfg, "rtp.items.close", 22)) player.closeInventory();
            return;
        }
        if (event.getRawSlot() == itemSlot(cfg, "queue.items.join", 10)) { player.closeInventory(); queue.join(player); }
        else if (event.getRawSlot() == itemSlot(cfg, "queue.items.status", 13)) { player.closeInventory(); sendQueueStatus(player); }
        else if (event.getRawSlot() == itemSlot(cfg, "queue.items.leave", 16)) { player.closeInventory(); queue.leave(player); }
        else if (event.getRawSlot() == itemSlot(cfg, "queue.items.instant-rtp", 22)) { player.closeInventory(); openRtpGui(player); }
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