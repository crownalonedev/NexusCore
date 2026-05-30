package dev.alone.nexusCore.listeners;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.profiles.PlayerProfile;
import dev.alone.nexusCore.utils.MessageUtil;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.UUID;

public final class ChatListener implements Listener {

    private final NexusCore plugin;

    public ChatListener(NexusCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        if (profile == null) {
            return;
        }

        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) -> {
            String format = getFormat(source, profile);

            String[] parts = format.split("%message%", -1);

            Component rendered = Component.empty();

            rendered = rendered.append(MessageUtil.color(parts[0]));

            Component chatMessage = message.colorIfAbsent(NamedTextColor.WHITE);
            rendered = rendered.append(chatMessage);

            if (parts.length > 1) {
                rendered = rendered.append(MessageUtil.color(parts[1]));
            }

            return rendered;
        }));
    }

    private String getFormat(Player player, PlayerProfile profile) {
        String path = "chat.formats.rank";

        if (profile.getAscension() > 0) {
            path = "chat.formats.ascension";
        } else if (profile.getRebirth() > 0) {
            path = "chat.formats.rebirth";
        }

        String format = plugin.getConfig().getString(
                path,
                "&8[%rank%&8] %luckperms-prefix% %player_name% &8» &f%message%"
        );

        return replaceChatPlaceholders(format, player, profile);
    }

    private String replaceChatPlaceholders(String text, Player player, PlayerProfile profile) {
        if (text == null) {
            return "";
        }

        return text
                .replace("%chat_tag%", plugin.getProgressionManager().getChatTag(profile))

                .replace("%rank%", plugin.getProgressionManager().getColoredRank(profile))
                .replace("%rank_raw%", String.valueOf(profile.getRank()))

                .replace("%rebirth%", plugin.getProgressionManager().getColoredRebirth(profile))
                .replace("%rebirth_raw%", String.valueOf(profile.getRebirth()))

                .replace("%ascension%", plugin.getProgressionManager().getColoredAscension(profile))
                .replace("%ascension_raw%", String.valueOf(profile.getAscension()))

                .replace("%luckperms-prefix%", getLuckPermsPrefix(player))
                .replace("%luckperms_prefix%", getLuckPermsPrefix(player))

                .replace("%player_name%", player.getName())
                .replace("%player%", player.getName());
    }

    private String getLuckPermsPrefix(Player player) {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object luckPerms = getMethod.invoke(null);

            Object userManager = luckPerms.getClass()
                    .getMethod("getUserManager")
                    .invoke(luckPerms);

            Object user = userManager.getClass()
                    .getMethod("getUser", UUID.class)
                    .invoke(userManager, player.getUniqueId());

            if (user == null) {
                return "";
            }

            Object cachedData = user.getClass()
                    .getMethod("getCachedData")
                    .invoke(user);

            Object metaData = cachedData.getClass()
                    .getMethod("getMetaData")
                    .invoke(cachedData);

            Object prefix = metaData.getClass()
                    .getMethod("getPrefix")
                    .invoke(metaData);

            if (prefix == null) {
                return "";
            }

            return prefix.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}