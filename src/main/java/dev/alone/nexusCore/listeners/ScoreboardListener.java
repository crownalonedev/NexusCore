package dev.alone.nexusCore.listeners;

import dev.alone.nexusCore.NexusCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ScoreboardListener implements Listener {

    private final NexusCore plugin;

    public ScoreboardListener(NexusCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getScoreboardManager().createBoard(player);
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getScoreboardManager().removeBoard(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        plugin.getScoreboardManager().removeBoard(event.getPlayer().getUniqueId());
    }
}