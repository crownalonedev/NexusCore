package dev.alone.nexusCore.utils;

import dev.alone.nexusCore.NexusCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageUtil() {
        // Utility class
    }

    public static Component color(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty().decoration(TextDecoration.ITALIC, false);
        }

        return MINI_MESSAGE.deserialize(convertLegacyToMiniMessage(message))
                .decoration(TextDecoration.ITALIC, false);
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    public static void sendWithPrefix(CommandSender sender, String message) {
        String prefix = NexusCore.getInstance()
                .getConfig()
                .getString("messages.prefix", "<aqua><bold>Nexus</bold></aqua> <dark_gray>»</dark_gray> ");

        sender.sendMessage(color(prefix + message));
    }

    public static String getMessage(String path) {
        return NexusCore.getInstance().getConfig().getString(path, "<red>Missing message: " + path);
    }

    private static String convertLegacyToMiniMessage(String message) {
        return message
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&A", "<green>")
                .replace("&B", "<aqua>")
                .replace("&C", "<red>")
                .replace("&D", "<light_purple>")
                .replace("&E", "<yellow>")
                .replace("&F", "<white>")
                .replace("&l", "<bold>")
                .replace("&L", "<bold>")
                .replace("&o", "<italic>")
                .replace("&O", "<italic>")
                .replace("&n", "<underlined>")
                .replace("&N", "<underlined>")
                .replace("&m", "<strikethrough>")
                .replace("&M", "<strikethrough>")
                .replace("&r", "<reset>")
                .replace("&R", "<reset>");
    }
}