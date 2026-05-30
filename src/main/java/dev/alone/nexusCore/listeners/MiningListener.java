package dev.alone.nexusCore.listeners;

import dev.alone.nexusCore.NexusCore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class MiningListener implements Listener {

    private final NexusCore plugin;

    public MiningListener(NexusCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.getProgressionManager().handleBlockBreak(
                event.getPlayer(),
                event.getBlock().getType()
        );
    }
}