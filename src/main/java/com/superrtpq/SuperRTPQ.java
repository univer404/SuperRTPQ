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
        RtpCommand handler = new RtpCommand(this, queueManager, teleportService);
        registerCommand("rtp", handler);
        registerCommand("rtpq", handler);
        getServer().getPluginManager().registerEvents(queueManager, this);
        getServer().getPluginManager().registerEvents(handler, this);
        queueManager.start();
        getLogger().info("SuperRTPQ enabled: RTP, queue and inventory GUI registered.");
    }

    private void registerCommand(String name, RtpCommand handler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml!");
            return;
        }
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    @Override
    public void onDisable() {
        if (queueManager != null) queueManager.stop();
    }

    public void reloadPlugin() {
        reloadConfig();
        debug = getConfig().getBoolean("debug", false);
        if (queueManager != null) queueManager.reloadSettings();
    }

    public QueueManager getQueueManager() { return queueManager; }
    public boolean isDebugEnabled() { return debug; }
    public void debug(String message) { if (debug) getLogger().info("[debug] " + message); }
}