package com.superrtpq;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
    private enum State { WAITING, COUNTDOWN }

    private final SuperRTPQ plugin;
    private final TeleportService teleportService;
    private final MessageService messages;
    private final Deque<UUID> queue = new ArrayDeque<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, UUID> partners = new HashMap<>();
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
        partners.clear();
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
        UUID id = player.getUniqueId();
        if (states.containsKey(id)) {
            messages.send(player, "already-queued");
            return false;
        }
        if (!player.hasPermission("superrtpq.bypass.cooldown")) {
            long remaining = getCooldownRemaining(id);
            if (remaining > 0) {
                messages.send(player, "cooldown", Map.of("seconds", String.valueOf((remaining + 999) / 1000)));
                return false;
            }
        }
        int maxSize = Math.max(2, plugin.getConfig().getInt("queue.max-size", 100));
        if (queue.size() >= maxSize) {
            messages.send(player, "queue-full");
            return false;
        }
        queue.addLast(id);
        states.put(id, State.WAITING);
        messages.send(player, "joined", Map.of(
                "position", String.valueOf(position(id)),
                "size", String.valueOf(queue.size())));
        return true;
    }

    public boolean leave(Player player) {
        UUID id = player.getUniqueId();
        if (!states.containsKey(id)) {
            messages.send(player, "not-queued");
            return false;
        }
        cancelPair(id, true);
        queue.remove(id);
        states.remove(id);
        movedDuringCountdown.remove(id);
        messages.send(player, "left");
        return true;
    }

    private void cancelPair(UUID id, boolean notifyPartner) {
        UUID partnerId = partners.remove(id);
        if (partnerId == null) return;
        partners.remove(partnerId);
        states.remove(partnerId);
        movedDuringCountdown.remove(partnerId);
        queue.remove(partnerId);
        Player partner = Bukkit.getPlayer(partnerId);
        if (notifyPartner && partner != null && partner.isOnline()) {
            partner.sendMessage("§cYour RTP 1v1 partner left. You have been removed from the match.");
        }
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
            if (player != null) messages.send(player, "left");
        }
        queue.clear();
        states.clear();
        partners.clear();
        movedDuringCountdown.clear();
    }

    public boolean remove(Player target) {
        UUID id = target.getUniqueId();
        if (!states.containsKey(id)) return false;
        cancelPair(id, true);
        queue.remove(id);
        states.remove(id);
        movedDuringCountdown.remove(id);
        return true;
    }

    public void forceTeleport(Player player) {
        queue.remove(player.getUniqueId());
        cancelPair(player.getUniqueId(), true);
        states.remove(player.getUniqueId());
        Location destination = teleportService.findSafeLocation(teleportService.findConfiguredWorld());
        if (destination == null) {
            messages.send(player, "failed");
            return;
        }
        teleportService.teleport(player, destination);
    }

    private void processQueue() {
        pruneQueue();
        int maxPairsPerCycle = Math.max(1, plugin.getConfig().getInt("queue.max-teleports-per-cycle", 1));
        int pairsStarted = 0;

        while (queue.size() >= 2 && pairsStarted < maxPairsPerCycle) {
            UUID firstId = queue.peekFirst();
            Player first = firstId == null ? null : Bukkit.getPlayer(firstId);
            if (first == null || !first.isOnline() || states.get(firstId) != State.WAITING) {
                queue.pollFirst();
                if (firstId != null) states.remove(firstId);
                continue;
            }

            UUID secondId = null;
            Player second = null;
            for (UUID candidateId : queue) {
                if (candidateId.equals(firstId)) continue;
                Player candidate = Bukkit.getPlayer(candidateId);
                if (candidate != null && candidate.isOnline() && states.get(candidateId) == State.WAITING) {
                    secondId = candidateId;
                    second = candidate;
                    break;
                }
            }
            if (secondId == null || second == null) return;

            Location destination = teleportService.findSafeLocation(teleportService.findConfiguredWorld());
            if (destination == null) {
                plugin.debug("No safe destination found for the RTP 1v1 queue. Check rtp.worlds and safe-location settings.");
                return;
            }

            queue.remove(firstId);
            queue.remove(secondId);
            states.put(firstId, State.COUNTDOWN);
            states.put(secondId, State.COUNTDOWN);
            partners.put(firstId, secondId);
            partners.put(secondId, firstId);
            messages.send(first, "searching");
            messages.send(second, "searching");
            beginPairCountdown(first, second, destination);
            pairsStarted++;
            pruneQueue();
        }
    }

    private void pruneQueue() {
        queue.removeIf(id -> {
            Player player = Bukkit.getPlayer(id);
            boolean remove = player == null || !player.isOnline() || states.get(id) != State.WAITING;
            if (remove && (player == null || !player.isOnline())) states.remove(id);
            return remove;
        });
    }

    private void beginPairCountdown(Player first, Player second, Location destination) {
        UUID firstId = first.getUniqueId();
        UUID secondId = second.getUniqueId();
        int seconds = Math.max(0, Math.min(30, plugin.getConfig().getInt("queue.countdown-seconds", 3)));
        final int[] remaining = {seconds};

        Bukkit.getScheduler().runTaskTimer(plugin, countdownTask -> {
            boolean validPair = first.isOnline() && second.isOnline()
                    && secondId.equals(partners.get(firstId))
                    && firstId.equals(partners.get(secondId))
                    && states.get(firstId) == State.COUNTDOWN
                    && states.get(secondId) == State.COUNTDOWN;
            if (!validPair) {
                clearPairState(firstId, secondId);
                countdownTask.cancel();
                return;
            }

            if (plugin.getConfig().getBoolean("queue.cancel-on-move", false)
                    && (movedDuringCountdown.remove(firstId) || movedDuringCountdown.remove(secondId))) {
                clearPairState(firstId, secondId);
                first.sendMessage("§cThe 1v1 RTP countdown was cancelled because a player moved.");
                second.sendMessage("§cThe 1v1 RTP countdown was cancelled because a player moved.");
                countdownTask.cancel();
                return;
            }

            if (remaining[0] <= 0) {
                Location firstDestination = destination.clone();
                Location secondDestination = destination.clone().add(2.0, 0.0, 0.0);
                Location firstOriginal = first.getLocation().clone();
                Location secondOriginal = second.getLocation().clone();
                boolean firstSuccess = teleportService.teleport(first, firstDestination);
                boolean secondSuccess = firstSuccess && teleportService.teleport(second, secondDestination);

                if (firstSuccess && secondSuccess) {
                    applyCooldown(first);
                    applyCooldown(second);
                    first.sendMessage("§aRTP 1v1 match started! Your opponent is §f" + second.getName() + "§a.");
                    second.sendMessage("§aRTP 1v1 match started! Your opponent is §f" + first.getName() + "§a.");
                } else {
                    // Avoid leaving one player alone at the match destination if the other teleport fails.
                    if (firstSuccess) first.teleport(firstOriginal);
                    if (secondSuccess) second.teleport(secondOriginal);
                    first.sendMessage("§cCould not teleport both players. You were returned to your previous location.");
                    second.sendMessage("§cCould not teleport both players. You were returned to your previous location.");
                }
                clearPairState(firstId, secondId);
                countdownTask.cancel();
                return;
            }

            messages.send(first, "countdown", Map.of("seconds", String.valueOf(remaining[0])));
            messages.send(second, "countdown", Map.of("seconds", String.valueOf(remaining[0])));
            remaining[0]--;
        }, 0L, 20L);
    }

    private void clearPairState(UUID firstId, UUID secondId) {
        partners.remove(firstId);
        partners.remove(secondId);
        states.remove(firstId);
        states.remove(secondId);
        movedDuringCountdown.remove(firstId);
        movedDuringCountdown.remove(secondId);
        queue.remove(firstId);
        queue.remove(secondId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cancelPair(id, true);
        queue.remove(id);
        states.remove(id);
        movedDuringCountdown.remove(id);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (states.get(id) == State.COUNTDOWN) movedDuringCountdown.add(id);
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
}
