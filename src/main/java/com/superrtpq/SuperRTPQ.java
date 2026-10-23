package com.superrtpq;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SuperRTPQ extends JavaPlugin {
    private QueueManager queueManager;
    private TeleportService teleportService;
    private boolean debug;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        debug = getConfig().getBoolean("debug", false);

        teleportService = new TeleportService(this);
        queueManager = new QueueManager(this, teleportService);
        RtpCommand commandHandler = new RtpCommand(this, queueManager, teleportService);

        PluginCommand rtp = getCommand("rtp");
        if (rtp != null) {
            rtp.setExecutor(commandHandler);
            rtp.setTabCompleter(commandHandler);
        }

        PluginCommand rtpq = getCommand("rtpq");
        if (rtpq != null) {
            rtpq.setExecutor(commandHandler);
            rtpq.setTabCompleter(commandHandler);
        }

        getServer().getPluginManager().registerEvents(queueManager, this);
        queueManager.start();
        getLogger().info("SuperRTPQ enabled. Queue processing is active.");
    }

    @Override
    public void onDisable() {
        if (queueManager != null) {
            queueManager.stop();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        debug = getConfig().getBoolean("debug", false);
        if (queueManager != null) {
            queueManager.reloadSettings();
        }
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public boolean isDebugEnabled() {
        return debug;
    }

    public void debug(String message) {
        if (debug) {
            getLogger().info("[debug] " + message);
        }
    }
}