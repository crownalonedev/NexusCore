package dev.alone.nexusCore.menus;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.profiles.PlayerProfile;
import dev.alone.nexusCore.utils.ItemBuilder;
import dev.alone.nexusCore.utils.MessageUtil;
import dev.alone.nexusCore.utils.SkullUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SettingsMenu {

    private final NexusCore plugin;

    public SettingsMenu(NexusCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, PlayerProfile profile) {
        YamlConfiguration gui = plugin.getGuiManager().getGui("settings");

        int size = getValidSize(gui.getInt("settings.size", 27));
        String title = replacePlaceholders(
                gui.getString("settings.title", "<gradient:#00CFFF:#0066FF><bold>Settings</bold></gradient>"),
                player,
                profile
        );

        int closeSlot = gui.getInt("items.close.slot", 22);
        int autoRebirthSlot = gui.getInt("items.auto-rebirth.slot", 11);
        int autoAscensionSlot = gui.getInt("items.auto-ascension.slot", 15);
        int hideMaxEnchantsSlot = gui.getInt("items.hide-max-enchants.slot", 13);

        Inventory inventory = Bukkit.createInventory(
                new Holder(player.getUniqueId(), closeSlot, autoRebirthSlot, autoAscensionSlot, hideMaxEnchantsSlot),
                size,
                MessageUtil.color(title)
        );

        applyFillers(inventory, gui);

        ConfigurationSection itemsSection = gui.getConfigurationSection("items");

        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);

                if (itemSection == null) {
                    continue;
                }

                int slot = itemSection.getInt("slot", -1);

                if (slot < 0 || slot >= size) {
                    continue;
                }

                inventory.setItem(slot, createItem(key, itemSection, player, profile));
            }
        }

        player.openInventory(inventory);
    }

    private void applyFillers(Inventory inventory, YamlConfiguration gui) {
        applyFillerSection(inventory, gui.getConfigurationSection("filler"));
        applyFillerSection(inventory, gui.getConfigurationSection("accent-filler"));
    }

    private void applyFillerSection(Inventory inventory, ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }

        Material material = getMaterial(section.getString("material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE);
        String name = section.getString("name", " ");
        List<Integer> slots = section.getIntegerList("slots");

        ItemStack item = new ItemBuilder(material)
                .name(name)
                .build();

        for (int slot : slots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item);
            }
        }
    }

    private ItemStack createItem(String key, ConfigurationSection section, Player player, PlayerProfile profile) {
        String type = section.getString("type", "MATERIAL").toUpperCase(Locale.ROOT);
        String name = replacePlaceholders(section.getString("name", "<white>Item"), player, profile);
        List<String> lore = replacePlaceholders(section.getStringList("lore"), player, profile);

        if (type.equals("BASE64_HEAD")) {
            String texture = section.getString("texture", "");
            return SkullUtil.createBase64Head(texture, name, lore);
        }

        Material material = getItemMaterial(key, section, profile);

        return new ItemBuilder(material)
                .name(name)
                .lore(lore)
                .build();
    }

    private Material getItemMaterial(String key, ConfigurationSection section, PlayerProfile profile) {
        if (key.equalsIgnoreCase("auto-rebirth")) {
            return profile.isAutoRebirth()
                    ? getMaterial(section.getString("enabled-material", "LIME_DYE"), Material.LIME_DYE)
                    : getMaterial(section.getString("disabled-material", "GRAY_DYE"), Material.GRAY_DYE);
        }

        if (key.equalsIgnoreCase("auto-ascension")) {
            return profile.isAutoAscension()
                    ? getMaterial(section.getString("enabled-material", "LIME_DYE"), Material.LIME_DYE)
                    : getMaterial(section.getString("disabled-material", "GRAY_DYE"), Material.GRAY_DYE);
        }

        if (key.equalsIgnoreCase("hide-max-enchants")) {
            return profile.isHideMaxEnchants()
                    ? getMaterial(section.getString("enabled-material", "LIME_DYE"), Material.LIME_DYE)
                    : getMaterial(section.getString("disabled-material", "GRAY_DYE"), Material.GRAY_DYE);
        }

        return getMaterial(section.getString("material", "STONE"), Material.STONE);
    }

    private String replacePlaceholders(String text, Player player, PlayerProfile profile) {
        if (text == null) {
            return "";
        }

        return text
                .replace("%player%", player.getName())
                .replace("%auto_rebirth%", formatBoolean(profile.isAutoRebirth()))
                .replace("%auto_rebirth_plain%", profile.isAutoRebirth() ? "Enabled" : "Disabled")
                .replace("%auto_ascension%", formatBoolean(profile.isAutoAscension()))
                .replace("%auto_ascension_plain%", profile.isAutoAscension() ? "Enabled" : "Disabled")
                .replace("%hide_max_enchants%", formatBoolean(profile.isHideMaxEnchants()))
                .replace("%hide_max_enchants_plain%", profile.isHideMaxEnchants() ? "Enabled" : "Disabled")
                .replace("%rank%", String.valueOf(profile.getRank()))
                .replace("%rebirth%", String.valueOf(profile.getRebirth()))
                .replace("%ascension%", String.valueOf(profile.getAscension()));
    }

    private List<String> replacePlaceholders(List<String> lines, Player player, PlayerProfile profile) {
        List<String> replaced = new ArrayList<>();

        for (String line : lines) {
            replaced.add(replacePlaceholders(line, player, profile));
        }

        return replaced;
    }

    private String formatBoolean(boolean value) {
        return value ? "<green>Enabled" : "<red>Disabled";
    }

    private Material getMaterial(String materialName, Material fallback) {
        if (materialName == null || materialName.isBlank()) {
            return fallback;
        }

        Material material = Material.matchMaterial(materialName);

        if (material == null) {
            return fallback;
        }

        return material;
    }

    private int getValidSize(int size) {
        if (size < 9 || size > 54 || size % 9 != 0) {
            return 27;
        }

        return size;
    }

    public static final class Holder implements InventoryHolder {

        private final UUID playerUuid;
        private final int closeSlot;
        private final int autoRebirthSlot;
        private final int autoAscensionSlot;
        private final int hideMaxEnchantsSlot;

        public Holder(UUID playerUuid, int closeSlot, int autoRebirthSlot, int autoAscensionSlot, int hideMaxEnchantsSlot) {
            this.playerUuid = playerUuid;
            this.closeSlot = closeSlot;
            this.autoRebirthSlot = autoRebirthSlot;
            this.autoAscensionSlot = autoAscensionSlot;
            this.hideMaxEnchantsSlot = hideMaxEnchantsSlot;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public int getCloseSlot() {
            return closeSlot;
        }

        public int getAutoRebirthSlot() {
            return autoRebirthSlot;
        }

        public int getAutoAscensionSlot() {
            return autoAscensionSlot;
        }

        public int getHideMaxEnchantsSlot() {
            return hideMaxEnchantsSlot;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
