package dev.alone.nexusCore.listeners;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.utils.MessageUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ProfileListener implements Listener {

    private final NexusCore plugin;

    public ProfileListener(NexusCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getProfileManager().loadProfile(event.getPlayer());

        MessageUtil.sendWithPrefix(
                event.getPlayer(),
                "<gray>Your Nexus profile has been loaded."
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getProfileManager().saveAndUnloadProfile(event.getPlayer().getUniqueId());
    }
}