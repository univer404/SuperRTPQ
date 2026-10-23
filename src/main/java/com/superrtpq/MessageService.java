package com.superrtpq;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.Map;

final class MessageService {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final SuperRTPQ plugin;

    MessageService(SuperRTPQ plugin) {
        this.plugin = plugin;
    }

    void send(CommandSender recipient, String key) {
        send(recipient, key, Map.of());
    }

    void send(CommandSender recipient, String key, Map<String, String> replacements) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String message = plugin.getConfig().getString("messages." + key, "<red>Missing message: " + key + "</red>");
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        Component component = MINI_MESSAGE.deserialize(prefix + message);
        recipient.sendMessage(component);
    }
}