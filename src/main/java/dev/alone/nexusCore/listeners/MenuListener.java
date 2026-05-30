package dev.alone.nexusCore.listeners;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.menus.ProfileMenu;
import dev.alone.nexusCore.menus.SettingsMenu;
import dev.alone.nexusCore.profiles.PlayerProfile;
import dev.alone.nexusCore.utils.MessageUtil;
import dev.alone.nexusCore.utils.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof ProfileMenu.Holder holder) {
            handleProfileMenuClick(event, holder);
            return;
        }

        if (event.getInventory().getHolder() instanceof SettingsMenu.Holder holder) {
            handleSettingsMenuClick(event, holder);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ProfileMenu.Holder) {
            event.setCancelled(true);
            return;
        }

        if (event.getInventory().getHolder() instanceof SettingsMenu.Holder) {
            event.setCancelled(true);
        }
    }

    private void handleProfileMenuClick(InventoryClickEvent event, ProfileMenu.Holder holder) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getRawSlot() == holder.getCloseSlot()) {
            player.closeInventory();
            SoundUtil.play(player, "sounds.menus.close");
        }
    }

    private void handleSettingsMenuClick(InventoryClickEvent event, SettingsMenu.Holder holder) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        NexusCore plugin = NexusCore.getInstance();
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        if (profile == null) {
            player.closeInventory();
            MessageUtil.sendWithPrefix(player, "<red>Your profile is not loaded.");
            return;
        }

        int slot = event.getRawSlot();

        if (slot == holder.getCloseSlot()) {
            player.closeInventory();
            SoundUtil.play(player, "sounds.menus.close");
            return;
        }

        if (slot == holder.getAutoRebirthSlot()) {
            profile.setAutoRebirth(!profile.isAutoRebirth());
            plugin.getProfileManager().saveProfile(player.getUniqueId());

            MessageUtil.sendWithPrefix(player, "<gray>Auto Rebirth is now " + formatBoolean(profile.isAutoRebirth()) + "<gray>.");
            SoundUtil.play(player, "sounds.menus.toggle");

            new SettingsMenu(plugin).open(player, profile);
            return;
        }

        if (slot == holder.getAutoAscensionSlot()) {
            profile.setAutoAscension(!profile.isAutoAscension());
            plugin.getProfileManager().saveProfile(player.getUniqueId());

            MessageUtil.sendWithPrefix(player, "<gray>Auto Ascension is now " + formatBoolean(profile.isAutoAscension()) + "<gray>.");
            SoundUtil.play(player, "sounds.menus.toggle");

            new SettingsMenu(plugin).open(player, profile);
        }
    }

    private String formatBoolean(boolean value) {
        return value ? "<green>Enabled" : "<red>Disabled";
    }
}