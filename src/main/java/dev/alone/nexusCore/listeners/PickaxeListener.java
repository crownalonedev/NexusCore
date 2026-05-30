package dev.alone.nexusCore.listeners;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.managers.PickaxeManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public class PickaxeListener implements Listener {

    private final NexusCore plugin;
    private final PickaxeManager pickaxeManager;

    public PickaxeListener(NexusCore plugin) {
        this.plugin = plugin;
        this.pickaxeManager = plugin.getPickaxeManager();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> pickaxeManager.handleJoin(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> pickaxeManager.syncPickaxe(event.getPlayer()), 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(pickaxeManager::isNexusPickaxe);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!pickaxeManager.isNexusPickaxe(event.getItemDrop().getItemStack())) {
            return;
        }

        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> pickaxeManager.syncPickaxe(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (pickaxeManager.isNexusPickaxe(event.getMainHandItem()) || pickaxeManager.isNexusPickaxe(event.getOffHandItem())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> pickaxeManager.syncPickaxe(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
            return;
        }

        if (pickaxeManager.handleMenuClick(player, event)) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbarItem = null;

        if (event.getHotbarButton() >= 0) {
            hotbarItem = player.getInventory().getItem(event.getHotbarButton());
        }

        if (pickaxeManager.isNexusPickaxe(current)
                || pickaxeManager.isNexusPickaxe(cursor)
                || pickaxeManager.isNexusPickaxe(hotbarItem)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> pickaxeManager.syncPickaxe(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
            return;
        }

        if (pickaxeManager.isNexusPickaxe(event.getOldCursor())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> pickaxeManager.syncPickaxe(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();

        if (!pickaxeManager.isNexusPickaxe(item)) {
            return;
        }

        event.setCancelled(true);
        pickaxeManager.openEnchantMenu(event.getPlayer());
    }
}