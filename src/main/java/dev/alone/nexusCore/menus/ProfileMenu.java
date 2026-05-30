package dev.alone.nexusCore.menus;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.profiles.PlayerProfile;
import dev.alone.nexusCore.utils.ItemBuilder;
import dev.alone.nexusCore.utils.MessageUtil;
import dev.alone.nexusCore.utils.SkullUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ProfileMenu {

    private final NexusCore plugin;

    public ProfileMenu(NexusCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, Player target, PlayerProfile profile) {
        YamlConfiguration gui = plugin.getGuiManager().getGui("profile");

        int size = getValidSize(gui.getInt("settings.size", 54));
        String title = replacePlaceholders(
                gui.getString("settings.title", "<gradient:#00CFFF:#0066FF><bold>Nexus Profile</bold></gradient> <dark_gray>| <white>%player%"),
                target,
                profile
        );

        int closeSlot = gui.getInt("items.close.slot", 49);

        Inventory inventory = Bukkit.createInventory(
                new Holder(target.getUniqueId(), closeSlot),
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

                inventory.setItem(slot, createItem(itemSection, target, profile));
            }
        }

        viewer.openInventory(inventory);
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

    private ItemStack createItem(ConfigurationSection section, Player target, PlayerProfile profile) {
        String type = section.getString("type", "MATERIAL").toUpperCase(Locale.ROOT);
        String name = replacePlaceholders(section.getString("name", "<white>Item"), target, profile);
        List<String> lore = replacePlaceholders(section.getStringList("lore"), target, profile);

        if (type.equals("PLAYER_HEAD")) {
            return createPlayerHead(target, name, lore);
        }

        if (type.equals("BASE64_HEAD")) {
            String texture = section.getString("texture", "");
            return SkullUtil.createBase64Head(texture, name, lore);
        }

        Material material = getMaterial(section.getString("material", "STONE"), Material.STONE);

        return new ItemBuilder(material)
                .name(name)
                .lore(lore)
                .build();
    }

    private ItemStack createPlayerHead(OfflinePlayer player, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.setOwningPlayer(player);
        meta.displayName(MessageUtil.color(name));

        List<net.kyori.adventure.text.Component> coloredLore = new ArrayList<>();

        for (String line : lore) {
            coloredLore.add(MessageUtil.color(line));
        }

        meta.lore(coloredLore);
        meta.addItemFlags(ItemFlag.values());

        item.setItemMeta(meta);
        return item;
    }

    private String replacePlaceholders(String text, Player target, PlayerProfile profile) {
        if (text == null) {
            return "";
        }

        int maxRank = plugin.getProfileManager().getMaxRank();
        int rebirthsRequired = plugin.getProfileManager().getRebirthsRequiredToAscend();

        long blocksRequired = plugin.getProgressionManager().getBlocksRequiredForNextRank(profile);
        long blocksRemaining = plugin.getProgressionManager().getBlocksRemainingForNextRank(profile);
        double rankProgressPercent = plugin.getProgressionManager().getRankProgressPercent(profile);

        return text
                .replace("%player%", target.getName())
                .replace("%uuid%", profile.getUuid().toString())

                .replace("%rank%", String.valueOf(profile.getRank()))
                .replace("%max_rank%", String.valueOf(maxRank))
                .replace("%next_rank%", String.valueOf(profile.getNextRank(maxRank)))
                .replace("%rank_progress_blocks%", String.valueOf(profile.getRankProgressBlocks()))
                .replace("%blocks_required_next_rank%", String.valueOf(blocksRequired))
                .replace("%blocks_remaining_next_rank%", String.valueOf(blocksRemaining))
                .replace("%rank_progress_percent%", formatPercent(rankProgressPercent))
                .replace("%rank_progress_bar%", createProgressBar(rankProgressPercent))
                .replace("%can_rebirth%", formatBoolean(plugin.getProgressionManager().canRebirth(profile)))

                .replace("%prestige%", String.valueOf(profile.getPrestige()))

                .replace("%ascension%", "A" + profile.getAscension())
                .replace("%ascension_raw%", String.valueOf(profile.getAscension()))

                .replace("%rebirth%", "R" + profile.getRebirth())
                .replace("%rebirth_raw%", String.valueOf(profile.getRebirth()))

                .replace("%rebirths_required%", String.valueOf(rebirthsRequired))
                .replace("%rebirths_until_ascension%", String.valueOf(profile.getRebirthsUntilAscension(rebirthsRequired)))
                .replace("%can_ascend%", formatBoolean(profile.canAscend(rebirthsRequired)))

                .replace("%money%", profile.getMoney().toPlainString())
                .replace("%tokens%", profile.getTokens().toString())
                .replace("%gems%", profile.getGems().toString())
                .replace("%beacons%", profile.getBeacons().toString())

                .replace("%blocks_mined%", String.valueOf(profile.getBlocksMined()))
                .replace("%raw_blocks_mined%", String.valueOf(profile.getRawBlocksMined()))

                .replace("%backpack_size%", String.valueOf(profile.getBackpackSize()))
                .replace("%pickaxe_level%", String.valueOf(profile.getPickaxeLevel()))
                .replace("%pickaxe_xp%", String.valueOf(profile.getPickaxeXp()))

                .replace("%current_mine%", formatCurrentMine(profile))
                .replace("%current_mine_number%", profile.getCurrentMine())

                .replace("%autosell%", formatBoolean(profile.isAutoSell()))
                .replace("%autopickup%", formatBoolean(profile.isAutoPickup()))
                .replace("%auto_rebirth%", formatBoolean(profile.isAutoRebirth()))
                .replace("%auto_ascension%", formatBoolean(profile.isAutoAscension()));
    }

    private List<String> replacePlaceholders(List<String> lines, Player target, PlayerProfile profile) {
        List<String> replaced = new ArrayList<>();

        for (String line : lines) {
            replaced.add(replacePlaceholders(line, target, profile));
        }

        return replaced;
    }

    private String formatBoolean(boolean value) {
        return value ? "<green>Enabled" : "<red>Disabled";
    }

    private String formatPercent(double percent) {
        if (percent == Math.floor(percent)) {
            return String.valueOf((int) percent);
        }

        return String.format(Locale.US, "%.1f", percent);
    }

    private String createProgressBar(double percent) {
        int totalBars = 10;
        int filledBars = (int) Math.round((percent / 100.0) * totalBars);

        filledBars = Math.max(0, Math.min(totalBars, filledBars));

        StringBuilder progressBar = new StringBuilder();

        progressBar.append("<aqua>");

        for (int i = 0; i < filledBars; i++) {
            progressBar.append("▌");
        }

        progressBar.append("<dark_gray>");

        for (int i = filledBars; i < totalBars; i++) {
            progressBar.append("▌");
        }

        return progressBar.toString();
    }

    private String formatCurrentMine(PlayerProfile profile) {
        String currentMine = profile.getCurrentMine();

        if (currentMine == null || currentMine.isBlank()) {
            return "Mine " + profile.getRank();
        }

        if (currentMine.toLowerCase(Locale.ROOT).startsWith("mine")) {
            return currentMine;
        }

        return "Mine " + currentMine;
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
            return 54;
        }

        return size;
    }

    public static final class Holder implements InventoryHolder {

        private final UUID targetUuid;
        private final int closeSlot;

        public Holder(UUID targetUuid, int closeSlot) {
            this.targetUuid = targetUuid;
            this.closeSlot = closeSlot;
        }

        public UUID getTargetUuid() {
            return targetUuid;
        }

        public int getCloseSlot() {
            return closeSlot;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}