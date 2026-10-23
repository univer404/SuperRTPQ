package com.superrtpq;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class QueueManager implements Listener {
    private enum State { WAITING, COUNTDOWN, TELEPORTING }

    private final SuperRTPQ plugin;
    private final TeleportService teleportService;
    private final MessageService messages;
    private final Deque<UUID> queue = new ArrayDeque<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, State> states = new HashMap<>();
    private final Set<UUID> movedDuringCountdown = new HashSet<>();
    private BukkitTask task;

    QueueManager(SuperRTPQ plugin, TeleportService teleportService) {
        this.plugin = plugin;
        this.teleportService = teleportService;
        this.messages = new MessageService(plugin);
    }

    public void start() {
        stopTask();
        long interval = Math.max(1, plugin.getConfig().getLong("queue.process-interval-ticks", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::processQueue, interval, interval);
    }

    public void stop() {
        stopTask();
        queue.clear();
        states.clear();
        movedDuringCountdown.clear();
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reloadSettings() {
        start();
    }

    public boolean join(Player player) {
        if (!plugin.getConfig().getBoolean("queue.enabled", true)) {
            messages.send(player, "queue-disabled");
            return false;
        }
        if (states.containsKey(player.getUniqueId())) {
            messages.send(player, "already-queued");
            return false;
        }
        if (!player.hasPermission("superrtpq.bypass.cooldown")) {
            long remaining = cooldowns.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis();
            if (remaining > 0) {
                messages.send(player, "cooldown", Map.of("seconds", String.valueOf((remaining + 999) / 1000)));
                return false;
            }
        }
        int maxSize = Math.max(1, plugin.getConfig().getInt("queue.max-size", 100));
        if (queue.size() >= maxSize) {
            messages.send(player, "queue-full");
            return false;
        }
        queue.addLast(player.getUniqueId());
        states.put(player.getUniqueId(), State.WAITING);
        messages.send(player, "joined", Map.of("position", String.valueOf(position(player.getUniqueId())),
                "size", String.valueOf(queue.size())));
        return true;
    }

    public boolean leave(Player player) {
        UUID id = player.getUniqueId();
        if (!states.containsKey(id)) {
            messages.send(player, "not-queued");
            return false;
        }
        queue.remove(id);
        states.remove(id);
        movedDuringCountdown.remove(id);
        messages.send(player, "left");
        return true;
    }

    public int position(UUID id) {
        int position = 1;
        for (UUID queued : queue) {
            if (queued.equals(id)) return position;
            position++;
        }
        return 0;
    }

    public int size() {
        return queue.size();
    }

    public long getCooldownRemaining(UUID id) {
        return Math.max(0L, cooldowns.getOrDefault(id, 0L) - System.currentTimeMillis());
    }

    public void applyCooldown(Player player) {
        long cooldownMillis = Math.max(0L,
                plugin.getConfig().getLong("queue.cooldown-seconds", 60)) * 1000L;
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownMillis);
    }


    public boolean isQueued(UUID id) {
        return states.containsKey(id);
    }

    public void clearQueue() {
        for (UUID id : new ArrayList<>(states.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                messages.send(player, "left");
            }
        }
        queue.clear();
        states.clear();
        movedDuringCountdown.clear();
    }

    public boolean remove(Player target) {
        UUID id = target.getUniqueId();
        if (!states.containsKey(id)) return false;
        queue.remove(id);
        states.remove(id);
        movedDuringCountdown.remove(id);
        return true;
    }

    public void forceTeleport(Player player) {
        queue.remove(player.getUniqueId());
        states.remove(player.getUniqueId());
        Location destination = teleportService.findSafeLocation(teleportService.findConfiguredWorld());
        if (destination == null) {
            messages.send(player, "failed");
            return;
        }
        teleportService.teleport(player, destination);
    }

    private void processQueue() {
        int maxPerCycle = Math.max(1, plugin.getConfig().getInt("queue.max-teleports-per-cycle", 1));
        int processed = 0;
        while (!queue.isEmpty() && processed < maxPerCycle) {
            UUID id = queue.peekFirst();
            Player player = id == null ? null : Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                queue.pollFirst();
                if (id != null) states.remove(id);
                continue;
            }
            if (states.get(id) != State.WAITING) {
                return;
            }

            WorldSelection selection = selectDestination();
            if (selection.location == null) {
                // Keep the player queued, but do not send a failure message every tick.
                // Retry on the next processing interval and only log when debug is enabled.
                plugin.debug("No safe destination found for " + player.getName()
                        + ". Check rtp.worlds, world border, distance and safe-location settings.");
                return;
            }

            queue.pollFirst();
            states.put(id, State.COUNTDOWN);
            messages.send(player, "searching");
            beginCountdown(player, selection.location);
            processed++;
        }
    }

    private WorldSelection selectDestination() {
        return new WorldSelection(teleportService.findSafeLocation(teleportService.findConfiguredWorld()));
    }

    private void beginCountdown(Player player, Location destination) {
        UUID id = player.getUniqueId();
        int seconds = Math.max(0, Math.min(30, plugin.getConfig().getInt("queue.countdown-seconds", 3)));
        final int[] remaining = {seconds};
        Bukkit.getScheduler().runTaskTimer(plugin, countdownTask -> {
            if (!player.isOnline() || states.get(id) != State.COUNTDOWN) {
                countdownTask.cancel();
                return;
            }
            if (plugin.getConfig().getBoolean("queue.cancel-on-move", false)
                    && movedDuringCountdown.remove(id)) {
                states.remove(id);
                messages.send(player, "left");
                countdownTask.cancel();
                return;
            }
            if (remaining[0] <= 0) {
                states.put(id, State.TELEPORTING);
                boolean success = teleportService.teleport(player, destination);
                if (success) {
                    long cooldownMillis = Math.max(0, plugin.getConfig().getLong("queue.cooldown-seconds", 60)) * 1000L;
                    cooldowns.put(id, System.currentTimeMillis() + cooldownMillis);
                    messages.send(player, "teleported");
                } else {
                    messages.send(player, "failed");
                }
                states.remove(id);
                countdownTask.cancel();
                return;
            }
            messages.send(player, "countdown", Map.of("seconds", String.valueOf(remaining[0])));
            remaining[0]--;
        }, 0L, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        queue.remove(id);
        states.remove(id);
        movedDuringCountdown.remove(id);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (states.get(id) == State.COUNTDOWN) {
            movedDuringCountdown.add(id);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            if (states.get(event.getPlayer().getUniqueId()) == State.COUNTDOWN) {
                movedDuringCountdown.add(event.getPlayer().getUniqueId());
            }
        }
    }

    private record WorldSelection(Location location) {}
}