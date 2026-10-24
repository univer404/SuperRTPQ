package com.superrtpq;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class TeleportService {
    private final SuperRTPQ plugin;

    TeleportService(SuperRTPQ plugin) { this.plugin = plugin; }

    public World findConfiguredWorld() {
        for (String name : plugin.getConfig().getStringList("rtp.worlds")) {
            World world = Bukkit.getWorld(name);
            if (world != null) return world;
        }
        return null;
    }

    public Location findSafeLocation(World world) {
        if (world == null) return null;
        int minDistance = Math.max(0, plugin.getConfig().getInt("rtp.min-distance", 1500));
        int maxDistance = Math.max(minDistance + 1, plugin.getConfig().getInt("rtp.max-distance", 10000));
        int attempts = Math.max(1, Math.min(250, plugin.getConfig().getInt("rtp.max-attempts", 100)));
        int minY = Math.max(world.getMinHeight() + 1, plugin.getConfig().getInt("rtp.min-y", 5));
        int maxY = Math.min(world.getMaxHeight() - 2, plugin.getConfig().getInt("rtp.max-y", world.getMaxHeight() - 2));
        var random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble(0, Math.PI * 2);
            double distance = random.nextDouble(minDistance, maxDistance);
            var border = world.getWorldBorder();
            double centerX = border.getCenter().getX();
            double centerZ = border.getCenter().getZ();
            int x = (int) Math.round(centerX + Math.cos(angle) * distance);
            int z = (int) Math.round(centerZ + Math.sin(angle) * distance);

            if (plugin.getConfig().getBoolean("rtp.respect-world-border", true)) {
                double margin = Math.max(0, plugin.getConfig().getDouble("rtp.border-margin", 16));
                double half = border.getSize() / 2.0 - margin;
                if (half <= 0 || Math.abs(x - centerX) > half
                        || Math.abs(z - centerZ) > half) continue;
            }

            int chunkX = x >> 4, chunkZ = z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                if (!plugin.getConfig().getBoolean("rtp.load-chunks", true)) continue;
                world.getChunkAt(chunkX, chunkZ);
            }

            int groundY = world.getHighestBlockYAt(x, z);
            if (groundY < minY || groundY > maxY) continue;
            Block ground = world.getBlockAt(x, groundY, z);
            Block feet = world.getBlockAt(x, groundY + 1, z);
            Block head = world.getBlockAt(x, groundY + 2, z);
            if (plugin.getConfig().getBoolean("rtp.require-solid-ground", true)
                    && (!ground.getType().isSolid() || ground.isPassable())) continue;
            if (!feet.isPassable() || !head.isPassable()) continue;
            if (plugin.getConfig().getBoolean("rtp.avoid-water", true)
                    && (isWater(ground) || isWater(feet) || isWater(head))) continue;
            if (plugin.getConfig().getBoolean("rtp.avoid-lava", true)
                    && (isLava(ground) || isLava(feet) || isLava(head))) continue;
            if (plugin.getConfig().getBoolean("rtp.avoid-fire", true)
                    && (isHazard(ground) || isHazard(feet) || isHazard(head))) continue;
            return new Location(world, x + 0.5, groundY + 1.0, z + 0.5, random.nextFloat() * 360f, 0f);
        }
        return null;
    }

    public boolean isSafeLanding(Location location) {
        if (location == null || location.getWorld() == null) return false;
        World world = location.getWorld();
        if (plugin.getConfig().getBoolean("rtp.respect-world-border", true)
                && !world.getWorldBorder().isInside(location)) return false;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) return false;
        Block ground = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        if (!ground.getType().isSolid() || ground.isPassable()) return false;
        if (!feet.isPassable() || !head.isPassable()) return false;
        if (plugin.getConfig().getBoolean("rtp.avoid-water", true)
                && (isWater(ground) || isWater(feet) || isWater(head))) return false;
        if (plugin.getConfig().getBoolean("rtp.avoid-lava", true)
                && (isLava(ground) || isLava(feet) || isLava(head))) return false;
        return !plugin.getConfig().getBoolean("rtp.avoid-fire", true)
                || !(isHazard(ground) || isHazard(feet) || isHazard(head));
    }

    public boolean teleport(Player player, Location destination) {
        return destination != null && isSafeLanding(destination) && player.teleport(destination);
    }

    private boolean isWater(Block b) { return b.getType() == Material.WATER || b.getType() == Material.BUBBLE_COLUMN; }
    private boolean isLava(Block b) { return b.getType() == Material.LAVA; }
    private boolean isHazard(Block b) {
        Material t = b.getType();
        return t == Material.FIRE || t == Material.SOUL_FIRE || t == Material.CAMPFIRE
                || t == Material.SOUL_CAMPFIRE || t == Material.MAGMA_BLOCK || t == Material.CACTUS
                || t == Material.SWEET_BERRY_BUSH || t == Material.POWDER_SNOW;
    }
}