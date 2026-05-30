package dev.alone.nexusCore.listeners;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.managers.MineManager;
import dev.alone.nexusCore.managers.PrivateMine;
import dev.alone.nexusCore.menus.MineMenuHolder;
import dev.alone.nexusCore.menus.MineMenus;
import dev.alone.nexusCore.utils.MineItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class MineMenuListener implements Listener {

    private final NexusCore plugin;
    private final MineManager mineManager;

    public MineMenuListener(NexusCore plugin, MineManager mineManager) {
        this.plugin = plugin;
        this.mineManager = mineManager;
    }


    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!mineManager.isMineWorld(player.getWorld())) {
            return;
        }

        if (!plugin.getConfig().getBoolean("private-mines.protection.cancel-all-player-damage", true)) {
            return;
        }

        event.setCancelled(true);
        event.setDamage(0.0);

        player.setFallDistance(0.0f);
        player.setFireTicks(0);
        player.setRemainingAir(player.getMaximumAir());

        if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION
                && plugin.getConfig().getBoolean("private-mines.protection.teleport-on-suffocation", true)) {
            PrivateMine mine = mineManager.getMine(player.getUniqueId());

            if (mine != null) {
                mineManager.teleportToMine(player, mine);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MineMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        switch (holder.getMenuType()) {
            case MAIN -> handleMainClick(player, event.getSlot());
            case BLOCKS -> handleBlocksClick(player, holder, clicked, event.getSlot());
        }
    }

    private void handleMainClick(Player player, int slot) {
        PrivateMine mine = mineManager.getOrCreateMine(player);

        switch (slot) {
            case 11 -> {
                mineManager.playConfiguredSound(player, "click");
                player.closeInventory();
                mineManager.teleportToMine(player, mine);
            }

            case 13 -> {
                mineManager.playConfiguredSound(player, "click");
                mineManager.resetMine(mine);
                mineManager.saveAll();
                mineManager.playConfiguredSound(player, "reset");
                player.sendMessage(MineItemUtil.prefixed(
                        "<green>Your mine has been reset."
                ));

                player.closeInventory();
                mineManager.teleportToMine(player, mine);
            }

            case 15 -> {
                if (MineMenus.openBlocks(plugin, mineManager, player)) {
                    mineManager.playConfiguredSound(player, "click");
                }
            }

            case 40 -> {
                mineManager.playConfiguredSound(player, "click");
                player.closeInventory();
            }

            default -> {
                // Filler item. No sound.
            }
        }
    }

    private void handleBlocksClick(Player player, MineMenuHolder holder, ItemStack clicked, int slot) {
        Set<Material> selectedBlocks = new LinkedHashSet<>(holder.getSelectedBlocks());
        int maxBlocks = MineMenus.getMaxSelectableBlocks(plugin);

        switch (slot) {
            case 48 -> {
                mineManager.playConfiguredSound(player, "click");
                selectedBlocks.clear();
                MineMenus.openBlocks(plugin, mineManager, player, selectedBlocks);
            }

            case 49 -> {
                mineManager.playConfiguredSound(player, "click");
                MineMenus.openMain(plugin, mineManager, player);
            }

            case 50 -> {
                if (selectedBlocks.isEmpty()) {
                    mineManager.playConfiguredSound(player, "error");
                    player.sendMessage(MineItemUtil.prefixed(
                            "<red>You must select at least <aqua>1</aqua><red> block."
                    ));
                    return;
                }

                if (selectedBlocks.size() > maxBlocks) {
                    mineManager.playConfiguredSound(player, "error");
                    player.sendMessage(MineItemUtil.prefixed(
                            "<red>You can only select up to <aqua>" + maxBlocks + "</aqua><red> blocks."
                    ));
                    return;
                }

                Map<Material, Integer> palette = MineMenus.createPaletteFromSelection(selectedBlocks);

                if (palette.isEmpty()) {
                    mineManager.playConfiguredSound(player, "error");
                    player.sendMessage(MineItemUtil.prefixed(
                            "<red>Your selected blocks are invalid."
                    ));
                    return;
                }

                PrivateMine mine = mineManager.getOrCreateMine(player);
                mineManager.setPalette(mine, palette, true);
                mineManager.playConfiguredSound(player, "reset");

                player.sendMessage(MineItemUtil.prefixed(
                        "<green>Your mine blocks have been updated with <aqua>" + palette.size() + "</aqua><green> selected block(s)."
                ));

                player.closeInventory();
                mineManager.teleportToMine(player, mine);
            }

            default -> {
                Material material = clicked.getType();

                if (!MineMenus.isBlockOption(material)) {
                    // Filler item. No sound.
                    return;
                }

                if (selectedBlocks.contains(material)) {
                    selectedBlocks.remove(material);
                    mineManager.playConfiguredSound(player, "click");
                    MineMenus.openBlocks(plugin, mineManager, player, selectedBlocks);
                    return;
                }

                if (selectedBlocks.size() >= maxBlocks) {
                    mineManager.playConfiguredSound(player, "error");
                    player.sendMessage(MineItemUtil.prefixed(
                            "<red>You can only select <aqua>" + maxBlocks + "</aqua><red> blocks."
                    ));
                    return;
                }

                selectedBlocks.add(material);
                mineManager.playConfiguredSound(player, "click");
                MineMenus.openBlocks(plugin, mineManager, player, selectedBlocks);
            }
        }
    }
}