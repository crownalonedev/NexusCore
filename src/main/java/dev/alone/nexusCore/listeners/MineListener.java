package dev.alone.nexusCore.listeners;

import dev.alone.nexusCore.managers.MineManager;
import dev.alone.nexusCore.managers.PrivateMine;
import dev.alone.nexusCore.utils.MineItemUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import java.util.Optional;

public class MineListener implements Listener {

    private final MineManager mineManager;

    public MineListener(MineManager mineManager) {
        this.mineManager = mineManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        if (!mineManager.isMineWorld(location.getWorld())) {
            return;
        }

        event.setDropItems(false);
        event.setExpToDrop(0);

        if (!mineManager.canBreakBlock(player, location)) {
            event.setCancelled(true);
            player.sendMessage(MineItemUtil.component("<red>You can only mine inside your own private mine."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!mineManager.isMineWorld(event.getBlock().getWorld())) {
            return;
        }

        event.setCancelled(true);
        event.getItems().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!mineManager.isMineWorld(event.getBlock().getWorld())) {
            return;
        }

        if (event.getPlayer().hasPermission("nexuscore.mine.admin")) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(MineItemUtil.component("<red>You cannot place blocks in private mines."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!mineManager.isMineWorld(player.getWorld())) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!mineManager.isMineWorld(player.getWorld())) {
            return;
        }

        event.setCancelled(true);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!mineManager.isMineWorld(event.getLocation().getWorld())) {
            return;
        }

        event.blockList().clear();
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!mineManager.isMineWorld(event.getBlock().getWorld())) {
            return;
        }

        event.blockList().clear();
        event.setCancelled(true);
    }
}