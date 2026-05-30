package dev.alone.nexusCore.managers;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.profiles.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PickaxeManager {

    private final NexusCore plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final NamespacedKey pickaxeKey;
    private final NamespacedKey menuActionKey;
    private final NamespacedKey enchantKey;
    private final NamespacedKey slotKey;

    private final File menuFile;
    private final File enchantFolder;
    private final Map<PickaxeEnchant, YamlConfiguration> enchantConfigs = new HashMap<>();

    private YamlConfiguration menuConfig;

    private int hasteTaskId = -1;

    public PickaxeManager(NexusCore plugin) {
        this.plugin = plugin;

        this.pickaxeKey = new NamespacedKey(plugin, "nexus_pickaxe");
        this.menuActionKey = new NamespacedKey(plugin, "nexus_menu_action");
        this.enchantKey = new NamespacedKey(plugin, "nexus_pickaxe_enchant");
        this.slotKey = new NamespacedKey(plugin, "nexus_pickaxe_slot");

        File guisFolder = new File(plugin.getDataFolder(), "guis");

        if (!guisFolder.exists()) {
            guisFolder.mkdirs();
        }

        this.menuFile = new File(guisFolder, "pickaxe-enchant-menu.yml");
        this.enchantFolder = new File(plugin.getDataFolder(), "enchant");

        if (!enchantFolder.exists() && !enchantFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create enchant folder.");
        }

        createDefaultMenuFile();
        createDefaultEnchantFiles();
        reload();
    }

    public void reload() {
        this.menuConfig = YamlConfiguration.loadConfiguration(menuFile);
        loadEnchantFiles();
    }

    public void shutdown() {
        if (hasteTaskId != -1) {
            Bukkit.getScheduler().cancelTask(hasteTaskId);
            hasteTaskId = -1;
        }
    }

    public void startTasks() {
        if (hasteTaskId != -1) {
            Bukkit.getScheduler().cancelTask(hasteTaskId);
        }

        hasteTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ItemStack item = player.getInventory().getItemInMainHand();

                if (!isNexusPickaxe(item)) {
                    continue;
                }

                PlayerProfile profile = plugin.getProfileManager().getProfile(player);

                int hasteLevel = profile.getEnchantLevel(PickaxeEnchant.HASTE);

                if (hasteLevel > 0 && isEnchantEnabled(PickaxeEnchant.HASTE)) {
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.HASTE,
                            80,
                            Math.max(0, hasteLevel - 1),
                            true,
                            false,
                            false
                    ));
                }

                int speedLevel = profile.getEnchantLevel(PickaxeEnchant.SPEED);

                if (speedLevel > 0 && isEnchantEnabled(PickaxeEnchant.SPEED)) {
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.SPEED,
                            80,
                            Math.max(0, speedLevel - 1),
                            true,
                            false,
                            false
                    ));
                }
            }
        }, 20L, 40L).getTaskId();
    }

    public void handleJoin(Player player) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        boolean autoGiveFirstJoin = plugin.getConfig().getBoolean("pickaxe.auto-give-on-first-join", true);
        boolean giveIfMissing = plugin.getConfig().getBoolean("pickaxe.give-if-missing-on-join", true);

        boolean firstJoinPickaxe = autoGiveFirstJoin && !profile.hasReceivedPickaxe();
        boolean missingPickaxe = giveIfMissing && !hasPickaxe(player);

        if (firstJoinPickaxe || missingPickaxe) {
            syncPickaxe(player);
            profile.setReceivedPickaxe(true);
            plugin.getProfileManager().saveProfile(profile);
        }
    }

    public boolean hasPickaxe(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isNexusPickaxe(item)) {
                return true;
            }
        }

        return isNexusPickaxe(player.getInventory().getItemInOffHand());
    }

    public void syncPickaxe(Player player) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);
        int slot = profile.getPickaxeSlot();

        PlayerInventory inventory = player.getInventory();

        removeAllPickaxes(player);

        ItemStack current = inventory.getItem(slot);

        if (!isAir(current)) {
            int emptySlot = firstEmptySlotExcept(inventory, slot);

            if (emptySlot == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), current);
            } else {
                inventory.setItem(emptySlot, current);
            }
        }

        inventory.setItem(slot, createPickaxe(player));
        player.updateInventory();
    }

    public void setPickaxeSlot(Player player, int slot) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        profile.setPickaxeSlot(slot);
        profile.setReceivedPickaxe(true);

        plugin.getProfileManager().saveProfile(profile);

        syncPickaxe(player);

        send(player, "<green>Your Nexus Pickaxe slot has been changed to <aqua>" + (profile.getPickaxeSlot() + 1) + "</aqua><green>.");
    }

    public ItemStack createPickaxe(Player player) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        Material material = getMaterial(plugin.getConfig().getString("pickaxe.material", "NETHERITE_PICKAXE"), Material.NETHERITE_PICKAXE);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        String name = replacePickaxePlaceholders(plugin.getConfig().getString("pickaxe.name", "<aqua><bold>Nexus Pickaxe</bold>"), player, profile);
        meta.displayName(color(name));

        List<Component> lore = new ArrayList<>();

        for (String line : plugin.getConfig().getStringList("pickaxe.lore")) {
            lore.add(color(replacePickaxePlaceholders(line, player, profile)));
        }

        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(pickaxeKey, PersistentDataType.STRING, "true");

        int efficiency = profile.getEnchantLevel(PickaxeEnchant.EFFICIENCY);
        int fortune = profile.getEnchantLevel(PickaxeEnchant.FORTUNE);

        if (efficiency > 0) {
            meta.addEnchant(Enchantment.EFFICIENCY, efficiency, true);
        }

        if (fortune > 0) {
            meta.addEnchant(Enchantment.FORTUNE, fortune, true);
        }

        for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
            meta.getPersistentDataContainer().set(enchantLevelKey(enchant), PersistentDataType.INTEGER, profile.getEnchantLevel(enchant));
        }

        item.setItemMeta(meta);
        return item;
    }

    private NamespacedKey enchantLevelKey(PickaxeEnchant enchant) {
        return new NamespacedKey(plugin, "nexus_pickaxe_level_" + enchant.getId());
    }

    public boolean isNexusPickaxe(ItemStack item) {
        if (isAir(item)) {
            return false;
        }

        if (!item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(pickaxeKey, PersistentDataType.STRING);
    }

    public void openEnchantMenu(Player player) {
        String title = getEnchantMenuTitle();
        int rows = Math.max(1, Math.min(6, menuConfig.getInt("enchants-menu.rows", 6)));

        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);

        fillMenu(inventory, "enchants-menu.filler");

        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        int previewSlot = menuConfig.getInt("enchants-menu.pickaxe-preview.slot", 4);
        ItemStack preview = createActionItem(
                Material.NETHERITE_PICKAXE,
                replacePickaxePlaceholders(menuConfig.getString("enchants-menu.pickaxe-preview.name", "<aqua><bold>Nexus Pickaxe</bold>"), player, profile),
                replacePickaxeLore(menuConfig.getStringList("enchants-menu.pickaxe-preview.lore"), player, profile),
                "slot_selector"
        );
        inventory.setItem(previewSlot, preview);

        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("enchants-menu.items");

        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                PickaxeEnchant enchant = PickaxeEnchant.fromInput(key);

                if (enchant == null || !isEnchantEnabled(enchant)) {
                    continue;
                }

                String path = "enchants-menu.items." + key;
                int slot = menuConfig.getInt(path + ".slot", 20);
                inventory.setItem(slot, createEnchantItem(player, enchant, path));
            }
        }

        int changeSlot = menuConfig.getInt("enchants-menu.change-slot.slot", 49);
        Material changeMaterial = getMaterial(menuConfig.getString("enchants-menu.change-slot.material", "NETHERITE_PICKAXE"), Material.NETHERITE_PICKAXE);

        ItemStack changeSlotItem = createActionItem(
                changeMaterial,
                replacePickaxePlaceholders(menuConfig.getString("enchants-menu.change-slot.name", "<aqua><bold>Change Pickaxe Slot</bold>"), player, profile),
                replacePickaxeLore(menuConfig.getStringList("enchants-menu.change-slot.lore"), player, profile),
                "slot_selector"
        );

        inventory.setItem(changeSlot, changeSlotItem);

        player.openInventory(inventory);
        playMenuSound(player, "sounds.menus.open");
    }

    public void openSlotMenu(Player player) {
        String title = getSlotMenuTitle();
        int rows = Math.max(1, Math.min(6, menuConfig.getInt("slot-menu.rows", 3)));

        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);

        fillMenu(inventory, "slot-menu.filler");

        PlayerProfile profile = plugin.getProfileManager().getProfile(player);
        int currentSlot = profile.getPickaxeSlot();
        int startSlot = menuConfig.getInt("slot-menu.slots.start-slot", 9);

        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            boolean current = hotbarSlot == currentSlot;

            Material material = current
                    ? getMaterial(menuConfig.getString("slot-menu.slots.current-material", "LIME_STAINED_GLASS_PANE"), Material.LIME_STAINED_GLASS_PANE)
                    : getMaterial(menuConfig.getString("slot-menu.slots.available-material", "LIGHT_BLUE_STAINED_GLASS_PANE"), Material.LIGHT_BLUE_STAINED_GLASS_PANE);

            String name = current
                    ? menuConfig.getString("slot-menu.slots.current-name", "<green><bold>Slot %slot%</bold></green>")
                    : menuConfig.getString("slot-menu.slots.available-name", "<aqua><bold>Slot %slot%</bold></aqua>");

            List<String> lore = current
                    ? menuConfig.getStringList("slot-menu.slots.current-lore")
                    : menuConfig.getStringList("slot-menu.slots.available-lore");

            ItemStack item = createActionItem(
                    material,
                    name.replace("%slot%", String.valueOf(hotbarSlot + 1)),
                    replaceSlotLore(lore, hotbarSlot),
                    "pickaxe_slot"
            );

            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                meta.getPersistentDataContainer().set(slotKey, PersistentDataType.INTEGER, hotbarSlot);
                item.setItemMeta(meta);
            }

            inventory.setItem(startSlot + hotbarSlot, item);
        }

        player.openInventory(inventory);
        playMenuSound(player, "sounds.menus.open");
    }

    public boolean handleMenuClick(Player player, InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (!title.equals(getEnchantMenuTitle()) && !title.equals(getSlotMenuTitle())) {
            return false;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null) {
            return true;
        }

        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return true;
        }

        ItemStack clicked = event.getCurrentItem();

        if (isAir(clicked)) {
            return true;
        }

        String action = getMenuAction(clicked);

        if ("slot_selector".equalsIgnoreCase(action)) {
            openSlotMenu(player);
            return true;
        }

        if ("pickaxe_slot".equalsIgnoreCase(action)) {
            Integer slot = getMenuSlot(clicked);

            if (slot != null) {
                setPickaxeSlot(player, slot);
                openEnchantMenu(player);
            }

            return true;
        }

        if ("upgrade_enchant".equalsIgnoreCase(action)) {
            PickaxeEnchant enchant = getMenuEnchant(clicked);

            if (enchant != null) {
                int amount = getUpgradeAmountFromClick(player, enchant, event.getClick());
                upgradeEnchant(player, enchant, amount);
                openEnchantMenu(player);
            }

            return true;
        }

        return true;
    }

    public void upgradeEnchant(Player player, PickaxeEnchant enchant, int amount) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        if (!isEnchantEnabled(enchant)) {
            send(player, "<red>That enchant is currently disabled.");
            return;
        }

        int currentLevel = profile.getEnchantLevel(enchant);
        int maxLevel = getMaxLevel(enchant);

        if (currentLevel >= maxLevel) {
            send(player, "<red>That enchant is already maxed.");
            return;
        }

        if (amount <= 0) {
            send(player, "<red>You cannot afford that upgrade.");
            return;
        }

        int upgraded = 0;
        BigInteger totalCost = BigInteger.ZERO;

        while (upgraded < amount) {
            currentLevel = profile.getEnchantLevel(enchant);

            if (currentLevel >= maxLevel) {
                break;
            }

            BigInteger cost = getUpgradeTokenCost(enchant, currentLevel);

            if (profile.getTokens().compareTo(cost) < 0) {
                break;
            }

            profile.removeTokens(cost);
            profile.addEnchantLevel(enchant, 1);

            totalCost = totalCost.add(cost);
            upgraded++;
        }

        if (upgraded <= 0) {
            BigInteger nextCost = getUpgradeTokenCost(enchant, profile.getEnchantLevel(enchant));
            send(player, "<red>You need <yellow>" + format(nextCost) + "</yellow><red> tokens to upgrade <white>" + enchant.getDisplayName() + "</white><red>.");
            return;
        }

        plugin.getProfileManager().saveProfile(profile);
        syncPickaxe(player);

        send(player, "<green>Upgraded <aqua>" + enchant.getDisplayName() + "</aqua><green> by <white>" + upgraded + "</white><green> level(s).");
        send(player, "<gray>Cost: <yellow>" + format(totalCost) + "</yellow><gray> tokens.");

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
    }

    public YamlConfiguration getEnchantConfig(PickaxeEnchant enchant) {
        if (enchant == null) {
            return new YamlConfiguration();
        }

        return enchantConfigs.getOrDefault(enchant, new YamlConfiguration());
    }

    public boolean getEnchantBoolean(PickaxeEnchant enchant, String path, boolean fallback) {
        return getEnchantConfig(enchant).getBoolean(path, fallback);
    }

    public int getEnchantInt(PickaxeEnchant enchant, String path, int fallback) {
        return getEnchantConfig(enchant).getInt(path, fallback);
    }

    public double getEnchantDouble(PickaxeEnchant enchant, String path, double fallback) {
        return getEnchantConfig(enchant).getDouble(path, fallback);
    }

    public String getEnchantString(PickaxeEnchant enchant, String path, String fallback) {
        return getEnchantConfig(enchant).getString(path, fallback);
    }

    public List<String> getEnchantStringList(PickaxeEnchant enchant, String path) {
        return getEnchantConfig(enchant).getStringList(path);
    }

    public BigInteger getEnchantBigInteger(PickaxeEnchant enchant, String path, BigInteger fallback) {
        String value = getEnchantString(enchant, path, null);

        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return new BigInteger(value.replace(",", "").replace("_", "").trim()).max(BigInteger.ZERO);
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning("Invalid BigInteger in enchant/" + enchant.getId() + ".yml at '" + path + "': " + value);
            return fallback;
        }
    }

    public boolean isEnchantEnabled(PickaxeEnchant enchant) {
        return getEnchantBoolean(enchant, "enabled", true);
    }

    public int getMaxLevel(PickaxeEnchant enchant) {
        return getEnchantInt(enchant, "max-level", 100);
    }

    public BigInteger getUpgradeTokenCost(PickaxeEnchant enchant, int currentLevel) {
        BigInteger base = getEnchantBigInteger(enchant, "base-token-cost", BigInteger.valueOf(1000));
        BigInteger increase = getEnchantBigInteger(enchant, "token-cost-increase", BigInteger.valueOf(500));

        return base.add(increase.multiply(BigInteger.valueOf(Math.max(0, currentLevel))));
    }

    private ItemStack createEnchantItem(Player player, PickaxeEnchant enchant, String path) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        int level = profile.getEnchantLevel(enchant);
        int maxLevel = getMaxLevel(enchant);
        BigInteger nextCost = getUpgradeTokenCost(enchant, level);

        Material material = getMaterial(menuConfig.getString(path + ".material", "BOOK"), Material.BOOK);
        String name = menuConfig.getString(path + ".name", "<aqua><bold>" + enchant.getDisplayName() + "</bold>");

        List<String> lore = new ArrayList<>();

        for (String line : menuConfig.getStringList(path + ".lore")) {
            lore.add(replaceEnchantPlaceholders(line, player, profile, enchant, level, maxLevel, nextCost));
        }

        ItemStack item = createActionItem(material, name, lore, "upgrade_enchant");
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.getPersistentDataContainer().set(enchantKey, PersistentDataType.STRING, enchant.getId());
            item.setItemMeta(meta);
        }

        return item;
    }

    private String replaceEnchantPlaceholders(
            String text,
            Player player,
            PlayerProfile profile,
            PickaxeEnchant enchant,
            int level,
            int maxLevel,
            BigInteger nextCost
    ) {
        String status;

        if (level >= maxLevel) {
            status = "<green><bold>MAXED</bold></green>";
        } else if (profile.getTokens().compareTo(nextCost) >= 0) {
            status = "<green>Click to upgrade.";
        } else {
            status = "<red>Not enough tokens.";
        }

        return text
                .replace("%player%", player.getName())
                .replace("%enchant%", enchant.getDisplayName())
                .replace("%level%", String.valueOf(level))
                .replace("%max_level%", String.valueOf(maxLevel))
                .replace("%next_cost%", format(nextCost))
                .replace("%tokens%", format(profile.getTokens()))
                .replace("%slot%", String.valueOf(profile.getPickaxeSlot() + 1))
                .replace("%status%", status);
    }

    private String replacePickaxePlaceholders(String text, Player player, PlayerProfile profile) {
        return text
                .replace("%player%", player.getName())
                .replace("%slot%", String.valueOf(profile.getPickaxeSlot() + 1))
                .replace("%tokens%", format(profile.getTokens()))
                .replace("%gems%", format(profile.getGems()))
                .replace("%pickaxe_level%", String.valueOf(profile.getPickaxeLevel()))
                .replace("%pickaxe_xp%", String.valueOf(profile.getPickaxeXp()))
                .replace("%efficiency%", String.valueOf(profile.getEnchantLevel(PickaxeEnchant.EFFICIENCY)))
                .replace("%fortune%", String.valueOf(profile.getEnchantLevel(PickaxeEnchant.FORTUNE)))
                .replace("%haste%", String.valueOf(profile.getEnchantLevel(PickaxeEnchant.HASTE)))
                .replace("%speed%", String.valueOf(profile.getEnchantLevel(PickaxeEnchant.SPEED)))
                .replace("%token_finder%", String.valueOf(profile.getEnchantLevel(PickaxeEnchant.TOKEN_FINDER)))
                .replace("%gem_finder%", String.valueOf(profile.getEnchantLevel(PickaxeEnchant.GEM_FINDER)))
                .replace("%key_finder%", String.valueOf(profile.getEnchantLevel(PickaxeEnchant.KEY_FINDER)))
                .replace("%lucky%", String.valueOf(profile.getEnchantLevel(PickaxeEnchant.LUCKY)))
                .replace("%explosive%", String.valueOf(profile.getEnchantLevel(PickaxeEnchant.EXPLOSIVE)))
                .replace("%jackhammer%", String.valueOf(profile.getEnchantLevel(PickaxeEnchant.JACKHAMMER)))
                .replace("%laser%", String.valueOf(profile.getEnchantLevel(PickaxeEnchant.LASER)));
    }

    private List<String> replacePickaxeLore(List<String> lines, Player player, PlayerProfile profile) {
        List<String> replaced = new ArrayList<>();

        for (String line : lines) {
            replaced.add(replacePickaxePlaceholders(line, player, profile));
        }

        return replaced;
    }

    private List<String> replaceSlotLore(List<String> lines, int hotbarSlot) {
        List<String> replaced = new ArrayList<>();

        for (String line : lines) {
            replaced.add(line.replace("%slot%", String.valueOf(hotbarSlot + 1)));
        }

        return replaced;
    }

    private void fillMenu(Inventory inventory, String path) {
        if (!menuConfig.getBoolean(path + ".enabled", true)) {
            return;
        }

        Material material = getMaterial(menuConfig.getString(path + ".material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE);
        String name = menuConfig.getString(path + ".name", " ");
        List<String> lore = menuConfig.getStringList(path + ".lore");

        ItemStack filler = createActionItem(material, name, lore, "filler");

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack createActionItem(Material material, String name, List<String> loreLines, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.displayName(color(name));

        List<Component> lore = new ArrayList<>();

        for (String line : loreLines) {
            lore.add(color(line));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(menuActionKey, PersistentDataType.STRING, action);

        item.setItemMeta(meta);
        return item;
    }

    private String getMenuAction(ItemStack item) {
        if (isAir(item) || !item.hasItemMeta()) {
            return "";
        }

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return "";
        }

        String action = meta.getPersistentDataContainer().get(menuActionKey, PersistentDataType.STRING);
        return action == null ? "" : action;
    }

    private PickaxeEnchant getMenuEnchant(ItemStack item) {
        if (isAir(item) || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return null;
        }

        String id = meta.getPersistentDataContainer().get(enchantKey, PersistentDataType.STRING);
        return PickaxeEnchant.fromInput(id);
    }

    private Integer getMenuSlot(ItemStack item) {
        if (isAir(item) || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return null;
        }

        return meta.getPersistentDataContainer().get(slotKey, PersistentDataType.INTEGER);
    }

    private int getUpgradeAmountFromClick(Player player, PickaxeEnchant enchant, ClickType clickType) {
        if (clickType.isShiftClick()) {
            return getMaxAffordableAmount(player, enchant);
        }

        if (clickType.isRightClick()) {
            return 10;
        }

        return 1;
    }

    private int getMaxAffordableAmount(Player player, PickaxeEnchant enchant) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        int amount = 0;
        int level = profile.getEnchantLevel(enchant);
        int maxLevel = getMaxLevel(enchant);
        BigInteger tokens = profile.getTokens();

        while (level < maxLevel) {
            BigInteger cost = getUpgradeTokenCost(enchant, level);

            if (tokens.compareTo(cost) < 0) {
                break;
            }

            tokens = tokens.subtract(cost);
            level++;
            amount++;
        }

        return amount;
    }

    private void removeAllPickaxes(Player player) {
        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);

            if (isNexusPickaxe(item)) {
                inventory.setItem(slot, null);
            }
        }

        if (isNexusPickaxe(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
    }

    private int firstEmptySlotExcept(PlayerInventory inventory, int excludedSlot) {
        for (int slot = 0; slot < 36; slot++) {
            if (slot == excludedSlot) {
                continue;
            }

            if (isAir(inventory.getItem(slot))) {
                return slot;
            }
        }

        return -1;
    }

    private String getEnchantMenuTitle() {
        return menuConfig.getString("enchants-menu.title", "Nexus Pickaxe Enchants");
    }

    private String getSlotMenuTitle() {
        return menuConfig.getString("slot-menu.title", "Nexus Pickaxe Slot");
    }

    private Material getMaterial(String input, Material fallback) {
        if (input == null) {
            return fallback;
        }

        Material material = Material.matchMaterial(input);

        if (material == null) {
            return fallback;
        }

        return material;
    }

    private boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private Component color(String message) {
        if (message == null || message.isEmpty()) {
            message = " ";
        }

        return miniMessage.deserialize(message).decoration(TextDecoration.ITALIC, false);
    }

    private void send(Player player, String message) {
        player.sendMessage(color(message));
    }

    private String format(BigInteger number) {
        return NumberFormat.getNumberInstance(Locale.US).format(number);
    }

    private void playMenuSound(Player player, String path) {
        if (!plugin.getConfig().getBoolean(path + ".enabled", true)) {
            return;
        }

        String soundName = plugin.getConfig().getString(path + ".sound", "UI_BUTTON_CLICK");
        float volume = (float) plugin.getConfig().getDouble(path + ".volume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble(path + ".pitch", 1.2);

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            // Invalid sound in config.
        }
    }

    private void createDefaultEnchantFiles() {
        for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
            File file = getEnchantFile(enchant);

            if (file.exists()) {
                continue;
            }

            try {
                plugin.saveResource("enchant/" + enchant.getId() + ".yml", false);
            } catch (IllegalArgumentException exception) {
                createFallbackEnchantFile(enchant, file);
            }
        }
    }

    private void loadEnchantFiles() {
        enchantConfigs.clear();

        for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
            File file = getEnchantFile(enchant);

            if (!file.exists()) {
                createFallbackEnchantFile(enchant, file);
            }

            enchantConfigs.put(enchant, YamlConfiguration.loadConfiguration(file));
        }
    }

    private File getEnchantFile(PickaxeEnchant enchant) {
        return new File(enchantFolder, enchant.getId() + ".yml");
    }

    private void createFallbackEnchantFile(PickaxeEnchant enchant, File file) {
        YamlConfiguration config = new YamlConfiguration();
        applyFallbackEnchantDefaults(config, enchant);

        try {
            config.save(file);
            plugin.getLogger().info("Created default enchant file: enchant/" + enchant.getId() + ".yml");
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not create enchant/" + enchant.getId() + ".yml.");
            exception.printStackTrace();
        }
    }

    private void applyFallbackEnchantDefaults(YamlConfiguration config, PickaxeEnchant enchant) {
        config.set("enabled", true);
        config.set("display-name", enchant.getDisplayName());

        switch (enchant) {
            case EFFICIENCY -> setCostDefaults(config, 250, 250, 125);
            case FORTUNE -> setCostDefaults(config, 150, 500, 225);
            case HASTE, SPEED -> setCostDefaults(config, 5, 2500, 2500);
            case TOKEN_FINDER -> {
                setCostDefaults(config, 2500, 1000, 450);
                setRewardDefaults(config, 8.0, 0.035, 90.0, "50", "250", "8", 10, "25", 1.0, 350);
            }
            case GEM_FINDER -> {
                setCostDefaults(config, 1500, 2500, 900);
                setRewardDefaults(config, 3.0, 0.018, 55.0, "2", "8", "1", 25, "10", 1.0, 350);
            }
            case KEY_FINDER -> {
                setCostDefaults(config, 100, 25000, 10000);
                config.set("base-chance", 0.0);
                config.set("chance-per-level", 0.006);
                config.set("max-chance", 5.0);
                config.set("min-reward", "1");
                config.set("max-reward", "1");
                config.set("levels-per-extra-reward", 100000);
                config.set("extra-reward", "0");
                config.set("max-reward-rolls-per-break", 350);
                config.set("commands", List.of("crate key give %player% mine %amount%"));
            }
            case LUCKY -> {
                setCostDefaults(config, 250, 3500, 1250);
                config.set("chance-boost-per-level", 0.02);
                config.set("reward-boost-percent-per-level", 0.25);
            }
            case EXPLOSIVE -> {
                setCostDefaults(config, 100, 5000, 1750);
                config.set("base-chance", 0.0);
                config.set("chance-per-level", 0.065);
                config.set("max-chance", 30.0);
                config.set("radius", 1);
                config.set("radius-increase-every-levels", 50);
                config.set("max-radius", 3);
                config.set("max-extra-blocks", 80);
            }
            case JACKHAMMER -> {
                setCostDefaults(config, 100, 7500, 2500);
                config.set("base-chance", 0.0);
                config.set("chance-per-level", 0.035);
                config.set("max-chance", 20.0);
                config.set("horizontal-radius", 4);
                config.set("radius-increase-every-levels", 40);
                config.set("max-horizontal-radius", 8);
                config.set("max-extra-blocks", 256);
            }
            case LASER -> {
                setCostDefaults(config, 100, 6500, 2200);
                config.set("base-chance", 0.0);
                config.set("chance-per-level", 0.05);
                config.set("max-chance", 25.0);
                config.set("length", 8);
                config.set("length-increase-every-levels", 25);
                config.set("max-length", 32);
            }
        }
    }

    private void setCostDefaults(YamlConfiguration config, int maxLevel, long baseCost, long costIncrease) {
        config.set("max-level", maxLevel);
        config.set("base-token-cost", String.valueOf(baseCost));
        config.set("token-cost-increase", String.valueOf(costIncrease));
    }

    private void setRewardDefaults(
            YamlConfiguration config,
            double baseChance,
            double chancePerLevel,
            double maxChance,
            String minReward,
            String maxReward,
            String rewardPerLevel,
            int levelsPerExtraReward,
            String extraReward,
            double rewardMultiplier,
            int maxRewardRolls
    ) {
        config.set("base-chance", baseChance);
        config.set("chance-per-level", chancePerLevel);
        config.set("max-chance", maxChance);
        config.set("min-reward", minReward);
        config.set("max-reward", maxReward);
        config.set("reward-per-level", rewardPerLevel);
        config.set("levels-per-extra-reward", levelsPerExtraReward);
        config.set("extra-reward", extraReward);
        config.set("reward-multiplier", rewardMultiplier);
        config.set("max-reward-rolls-per-break", maxRewardRolls);
    }

    private void createDefaultMenuFile() {
        if (menuFile.exists()) {
            return;
        }

        YamlConfiguration config = new YamlConfiguration();

        config.set("enchants-menu.title", "Nexus Pickaxe Enchants");
        config.set("enchants-menu.rows", 6);
        config.set("enchants-menu.filler.enabled", true);
        config.set("enchants-menu.filler.material", "BLACK_STAINED_GLASS_PANE");
        config.set("enchants-menu.filler.name", " ");
        config.set("enchants-menu.filler.lore", List.of());

        config.set("enchants-menu.pickaxe-preview.slot", 4);
        config.set("enchants-menu.pickaxe-preview.name", "<gradient:#00CFFF:#54FCFC:#008CFC><bold>Nexus Pickaxe</bold></gradient>");
        config.set("enchants-menu.pickaxe-preview.lore", List.of(
                "",
                "<gray>Owner: <aqua>%player%</aqua>",
                "<gray>Pickaxe Slot: <aqua>%slot%</aqua>",
                "<gray>Tokens: <yellow>%tokens%</yellow>",
                "",
                "<yellow>Click to change your pickaxe slot."
        ));

        setDefaultEnchant(config, "efficiency", 20, "DIAMOND_PICKAXE", "Efficiency", "Makes your pickaxe mine faster.");
        setDefaultEnchant(config, "fortune", 21, "EMERALD", "Fortune", "Boosts drops from ores.");
        setDefaultEnchant(config, "haste", 22, "SUGAR", "Haste", "Gives haste while holding your pickaxe.");
        setDefaultEnchant(config, "speed", 23, "FEATHER", "Speed", "Gives speed while holding your pickaxe.");
        setDefaultEnchant(config, "token_finder", 24, "SUNFLOWER", "Token Finder", "Finds extra tokens while mining.");
        setDefaultEnchant(config, "gem_finder", 29, "AMETHYST_SHARD", "Gem Finder", "Finds gems while mining.");
        setDefaultEnchant(config, "key_finder", 30, "TRIPWIRE_HOOK", "Key Finder", "Finds crate keys while mining.");
        setDefaultEnchant(config, "lucky", 31, "RABBIT_FOOT", "Lucky", "Boosts reward enchant chances and amounts.");
        setDefaultEnchant(config, "explosive", 32, "TNT", "Explosive", "Breaks nearby blocks while mining.");
        setDefaultEnchant(config, "jackhammer", 33, "ANVIL", "Jackhammer", "Breaks a layer of blocks while mining.");
        setDefaultEnchant(config, "laser", 34, "END_ROD", "Laser", "Breaks a straight line of blocks while mining.");

        config.set("enchants-menu.change-slot.slot", 49);
        config.set("enchants-menu.change-slot.material", "NETHERITE_PICKAXE");
        config.set("enchants-menu.change-slot.name", "<gradient:#00CFFF:#54FCFC:#008CFC><bold>Change Pickaxe Slot</bold></gradient>");
        config.set("enchants-menu.change-slot.lore", List.of(
                "",
                "<gray>Current Slot: <aqua>%slot%</aqua>",
                "",
                "<yellow>Click to choose slots 1-9."
        ));

        config.set("slot-menu.title", "Nexus Pickaxe Slot");
        config.set("slot-menu.rows", 3);
        config.set("slot-menu.filler.enabled", true);
        config.set("slot-menu.filler.material", "BLACK_STAINED_GLASS_PANE");
        config.set("slot-menu.filler.name", " ");
        config.set("slot-menu.filler.lore", List.of());
        config.set("slot-menu.slots.start-slot", 9);
        config.set("slot-menu.slots.current-material", "LIME_STAINED_GLASS_PANE");
        config.set("slot-menu.slots.available-material", "LIGHT_BLUE_STAINED_GLASS_PANE");
        config.set("slot-menu.slots.current-name", "<green><bold>Slot %slot%</bold></green>");
        config.set("slot-menu.slots.available-name", "<aqua><bold>Slot %slot%</bold></aqua>");
        config.set("slot-menu.slots.current-lore", List.of("", "<green>Your pickaxe is currently here."));
        config.set("slot-menu.slots.available-lore", List.of("", "<gray>Click to move your pickaxe here."));

        try {
            config.save(menuFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not create guis/pickaxe-enchant-menu.yml.");
            exception.printStackTrace();
        }
    }

    private void setDefaultEnchant(YamlConfiguration config, String id, int slot, String material, String displayName, String description) {
        String path = "enchants-menu.items." + id;

        config.set(path + ".slot", slot);
        config.set(path + ".material", material);
        config.set(path + ".name", "<gradient:#00CFFF:#54FCFC:#008CFC><bold>" + displayName + "</bold></gradient>");
        config.set(path + ".lore", List.of(
                "",
                "<gray>Current Level: <aqua>%level%</aqua><gray>/<aqua>%max_level%</aqua>",
                "<gray>Next Upgrade: <yellow>%next_cost%</yellow><gray> tokens",
                "",
                "<gray>" + description,
                "",
                "<yellow>Left-click: <white>+1 Level",
                "<yellow>Right-click: <white>+10 Levels",
                "<yellow>Shift-click: <white>Max Affordable",
                "",
                "%status%"
        ));
    }
}