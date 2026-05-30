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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
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

    private static final int[] ENCHANT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43,
            46, 47, 48, 50, 51, 52, 53
    };

    private final NexusCore plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final NamespacedKey pickaxeKey;
    private final NamespacedKey menuActionKey;
    private final NamespacedKey enchantKey;
    private final NamespacedKey slotKey;
    private final NamespacedKey upgradeAmountKey;

    private final File menuFile;
    private final File enchantFolder;
    private final Map<PickaxeEnchant, YamlConfiguration> enchantConfigs = new HashMap<>();

    private YamlConfiguration menuConfig;
    private int passiveTaskId = -1;

    public PickaxeManager(NexusCore plugin) {
        this.plugin = plugin;
        this.pickaxeKey = new NamespacedKey(plugin, "nexus_pickaxe");
        this.menuActionKey = new NamespacedKey(plugin, "nexus_menu_action");
        this.enchantKey = new NamespacedKey(plugin, "nexus_pickaxe_enchant");
        this.slotKey = new NamespacedKey(plugin, "nexus_pickaxe_slot");
        this.upgradeAmountKey = new NamespacedKey(plugin, "nexus_upgrade_amount");

        File guisFolder = new File(plugin.getDataFolder(), "guis");
        if (!guisFolder.exists()) {
            guisFolder.mkdirs();
        }

        this.menuFile = new File(guisFolder, "pickaxe-enchant-menu.yml");
        this.enchantFolder = new File(plugin.getDataFolder(), "enchant");

        if (!enchantFolder.exists()) {
            enchantFolder.mkdirs();
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
        if (passiveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(passiveTaskId);
            passiveTaskId = -1;
        }
    }

    public void startTasks() {
        shutdown();
        passiveTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerProfile profile = plugin.getProfileManager().getProfile(player);
                updateExperienceBar(player, profile);

                ItemStack item = player.getInventory().getItemInMainHand();
                if (!isNexusPickaxe(item)) {
                    continue;
                }

                int hasteLevel = profile.getEnchantLevel(PickaxeEnchant.HASTE);
                if (hasteLevel > 0 && isEnchantEnabled(PickaxeEnchant.HASTE)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 80, Math.max(0, hasteLevel - 1), true, false, false));
                }

                int speedLevel = profile.getEnchantLevel(PickaxeEnchant.SPEED);
                if (speedLevel > 0 && isEnchantEnabled(PickaxeEnchant.SPEED)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, Math.max(0, speedLevel - 1), true, false, false));
                }
            }
        }, 20L, 20L).getTaskId();
    }

    public void handleJoin(Player player) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);
        ensureEfficiencyMaxed(profile);

        boolean autoGiveFirstJoin = plugin.getConfig().getBoolean("pickaxe.auto-give-on-first-join", true);
        boolean giveIfMissing = plugin.getConfig().getBoolean("pickaxe.give-if-missing-on-join", true);

        boolean firstJoinPickaxe = autoGiveFirstJoin && !profile.hasReceivedPickaxe();
        boolean missingPickaxe = giveIfMissing && !hasPickaxe(player);

        if (firstJoinPickaxe || missingPickaxe) {
            syncPickaxe(player);
            profile.setReceivedPickaxe(true);
            plugin.getProfileManager().saveProfile(profile);
        } else {
            syncPickaxe(player);
        }

        updateExperienceBar(player, profile);
    }

    public void addPickaxeXp(Player player, PlayerProfile profile, long amount) {
        if (player == null || profile == null || amount <= 0) {
            return;
        }

        profile.addPickaxeXp(amount);

        int levelsGained = 0;
        while (profile.getPickaxeLevel() < getMaxPickaxeLevel()) {
            long needed = getXpRequiredForNextPickaxeLevel(profile.getPickaxeLevel());
            if (profile.getPickaxeXp() < needed) {
                break;
            }

            profile.setPickaxeXp(profile.getPickaxeXp() - needed);
            profile.setPickaxeLevel(profile.getPickaxeLevel() + 1);
            levelsGained++;
        }

        if (levelsGained > 0) {
            send(player, "<gradient:#00CFFF:#0066FF><bold>PICKAXE LEVEL UP!</bold></gradient> <gray>You reached level <aqua>" + profile.getPickaxeLevel() + "</aqua><gray>.");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
            syncPickaxe(player);
        }

        updateExperienceBar(player, profile);
    }

    public void updateExperienceBar(Player player, PlayerProfile profile) {
        if (player == null || profile == null) {
            return;
        }

        int level = profile.getPickaxeLevel();
        long required = getXpRequiredForNextPickaxeLevel(level);
        float progress = required <= 0 ? 1.0f : Math.max(0.0f, Math.min(1.0f, (float) profile.getPickaxeXp() / (float) required));

        player.setLevel(level);
        player.setExp(level >= getMaxPickaxeLevel() ? 1.0f : progress);
    }

    public long getXpRequiredForNextPickaxeLevel(int level) {
        long base = Math.max(1L, plugin.getConfig().getLong("pickaxe.leveling.xp-base", 250L));
        long increase = Math.max(0L, plugin.getConfig().getLong("pickaxe.leveling.xp-increase-per-level", 125L));
        return base + (Math.max(1, level) - 1L) * increase;
    }

    public int getMaxPickaxeLevel() {
        return Math.max(1, plugin.getConfig().getInt("pickaxe.leveling.max-level", 650));
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
        ensureEfficiencyMaxed(profile);

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
        updateExperienceBar(player, profile);
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
        ensureEfficiencyMaxed(profile);

        Material material = getMaterial(plugin.getConfig().getString("pickaxe.material", "NETHERITE_PICKAXE"), Material.NETHERITE_PICKAXE);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.displayName(color(replacePickaxePlaceholders(plugin.getConfig().getString("pickaxe.name", "<aqua><bold>Nexus Pickaxe</bold>"), player, profile)));

        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("pickaxe.lore")) {
            if (line.contains("%fortune%")) {
                continue;
            }
            lore.add(color(replacePickaxePlaceholders(line, player, profile)));
        }

        List<String> dynamicLore = buildDynamicEnchantLore(profile);
        if (!dynamicLore.isEmpty()) {
            lore.add(color(""));
            lore.add(color("<gradient:#00CFFF:#0066FF><bold>Unlocked Enchants</bold></gradient>"));
            for (String line : dynamicLore) {
                lore.add(color(line));
            }
        }

        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(pickaxeKey, PersistentDataType.STRING, "true");
        meta.addEnchant(Enchantment.EFFICIENCY, getMaxLevel(PickaxeEnchant.EFFICIENCY), true);

        for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
            meta.getPersistentDataContainer().set(enchantLevelKey(enchant), PersistentDataType.INTEGER, profile.getEnchantLevel(enchant));
        }

        item.setItemMeta(meta);
        return item;
    }

    private List<String> buildDynamicEnchantLore(PlayerProfile profile) {
        List<String> lore = new ArrayList<>();
        for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
            if (enchant == PickaxeEnchant.EFFICIENCY) {
                lore.add("  <gray>Efficiency: <aqua>MAX</aqua>");
                continue;
            }

            int level = profile.getEnchantLevel(enchant);
            if (level <= 0) {
                continue;
            }

            lore.add("  <gray>" + enchant.getDisplayName() + ": <aqua>" + level + "</aqua>");
        }
        return lore;
    }

    private void ensureEfficiencyMaxed(PlayerProfile profile) {
        if (profile == null) {
            return;
        }
        int max = getMaxLevel(PickaxeEnchant.EFFICIENCY);
        if (profile.getEnchantLevel(PickaxeEnchant.EFFICIENCY) < max) {
            profile.setEnchantLevel(PickaxeEnchant.EFFICIENCY, max);
        }
    }

    private NamespacedKey enchantLevelKey(PickaxeEnchant enchant) {
        return new NamespacedKey(plugin, "nexus_pickaxe_level_" + enchant.getId());
    }

    public boolean isNexusPickaxe(ItemStack item) {
        if (isAir(item) || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(pickaxeKey, PersistentDataType.STRING);
    }

    public void openEnchantMenu(Player player) {
        String title = getEnchantMenuTitle();
        int rows = Math.max(1, Math.min(6, menuConfig.getInt("enchants-menu.rows", 6)));
        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);
        fillMenu(inventory, "enchants-menu.filler");

        PlayerProfile profile = plugin.getProfileManager().getProfile(player);
        updateExperienceBar(player, profile);

        int previewSlot = menuConfig.getInt("enchants-menu.pickaxe-preview.slot", 4);
        inventory.setItem(previewSlot, createActionItem(
                Material.NETHERITE_PICKAXE,
                replacePickaxePlaceholders(menuConfig.getString("enchants-menu.pickaxe-preview.name", "<aqua><bold>Nexus Pickaxe</bold>"), player, profile),
                replacePickaxeLore(menuConfig.getStringList("enchants-menu.pickaxe-preview.lore"), player, profile),
                "slot_selector"
        ));

        int index = 0;
        for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
            if (!isEnchantEnabled(enchant)) {
                continue;
            }
            if (index >= ENCHANT_SLOTS.length) {
                break;
            }
            inventory.setItem(ENCHANT_SLOTS[index], createEnchantItem(player, enchant));
            index++;
        }

        int changeSlot = menuConfig.getInt("enchants-menu.change-slot.slot", 49);
        inventory.setItem(changeSlot, createActionItem(
                getMaterial(menuConfig.getString("enchants-menu.change-slot.material", "NETHERITE_PICKAXE"), Material.NETHERITE_PICKAXE),
                replacePickaxePlaceholders(menuConfig.getString("enchants-menu.change-slot.name", "<aqua><bold>Change Pickaxe Slot</bold>"), player, profile),
                replacePickaxeLore(menuConfig.getStringList("enchants-menu.change-slot.lore"), player, profile),
                "slot_selector"
        ));

        player.openInventory(inventory);
        playMenuSound(player, "sounds.menus.open");
    }

    public void openUpgradeMenu(Player player, PickaxeEnchant enchant) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);
        Inventory inventory = Bukkit.createInventory(null, 45, getUpgradeMenuTitle(enchant));
        fillMenu(inventory, "upgrade-menu.filler");

        inventory.setItem(13, createEnchantItem(player, enchant));
        setUpgradeButton(inventory, 20, player, profile, enchant, 1, "<#00CFFF><bold>+1 Level</bold>");
        setUpgradeButton(inventory, 21, player, profile, enchant, 5, "<#00CFFF><bold>+5 Levels</bold>");
        setUpgradeButton(inventory, 22, player, profile, enchant, 25, "<#00CFFF><bold>+25 Levels</bold>");
        setUpgradeButton(inventory, 23, player, profile, enchant, 50, "<#0066FF><bold>+50 Levels</bold>");
        setUpgradeButton(inventory, 24, player, profile, enchant, 100, "<#0066FF><bold>+100 Levels</bold>");
        setUpgradeButton(inventory, 31, player, profile, enchant, -1, "<gradient:#FCFC54:#FCA800><bold>MAX UPGRADE</bold></gradient>");
        inventory.setItem(40, createActionItem(Material.ARROW, "<yellow><bold>Back</bold>", List.of("", "<gray>Return to the enchant list."), "back_enchants"));

        player.openInventory(inventory);
        playMenuSound(player, "sounds.menus.open");
    }

    private void setUpgradeButton(Inventory inventory, int slot, Player player, PlayerProfile profile, PickaxeEnchant enchant, int amount, String name) {
        int realAmount = amount == -1 ? getMaxAffordableAmount(player, enchant) : amount;
        BigInteger cost = getTotalUpgradeCost(enchant, profile.getEnchantLevel(enchant), realAmount);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Enchant: <aqua>" + enchant.getDisplayName());
        lore.add("<gray>Current Level: <aqua>" + profile.getEnchantLevel(enchant) + "</aqua><gray>/<aqua>" + getMaxLevel(enchant));
        lore.add("<gray>Upgrade Amount: <aqua>" + (realAmount <= 0 ? 0 : realAmount));
        lore.add("<gray>Cost: <yellow>" + format(cost) + "</yellow><gray> tokens");
        lore.add("");
        lore.add(realAmount <= 0 ? "<red>No affordable levels available." : "<yellow>Click to purchase.");

        ItemStack item = createActionItem(Material.LIME_DYE, name, lore, "buy_upgrade");
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(enchantKey, PersistentDataType.STRING, enchant.getId());
            meta.getPersistentDataContainer().set(upgradeAmountKey, PersistentDataType.INTEGER, realAmount);
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    public void openSlotMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, Math.max(1, Math.min(6, menuConfig.getInt("slot-menu.rows", 3))) * 9, getSlotMenuTitle());
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
            List<String> lore = current ? menuConfig.getStringList("slot-menu.slots.current-lore") : menuConfig.getStringList("slot-menu.slots.available-lore");

            ItemStack item = createActionItem(material, name.replace("%slot%", String.valueOf(hotbarSlot + 1)), replaceSlotLore(lore, hotbarSlot), "pickaxe_slot");
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
        if (!title.equals(getEnchantMenuTitle()) && !title.equals(getSlotMenuTitle()) && !title.startsWith("Upgrade ")) {
            return false;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
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
        if ("open_upgrade".equalsIgnoreCase(action)) {
            PickaxeEnchant enchant = getMenuEnchant(clicked);
            if (enchant != null) {
                if (!isUnlocked(player, enchant)) {
                    send(player, "<red>You need Pickaxe Level <white>" + getUnlockLevel(enchant) + "</white><red> to unlock " + enchant.getDisplayName() + ".");
                    playMenuSound(player, "sounds.menus.toggle");
                    return true;
                }
                openUpgradeMenu(player, enchant);
            }
            return true;
        }
        if ("buy_upgrade".equalsIgnoreCase(action)) {
            PickaxeEnchant enchant = getMenuEnchant(clicked);
            Integer amount = getUpgradeAmount(clicked);
            if (enchant != null && amount != null) {
                upgradeEnchant(player, enchant, amount);
                openUpgradeMenu(player, enchant);
            }
            return true;
        }
        if ("back_enchants".equalsIgnoreCase(action)) {
            openEnchantMenu(player);
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
        if (!isUnlocked(player, enchant)) {
            send(player, "<red>You need Pickaxe Level <white>" + getUnlockLevel(enchant) + "</white><red> to unlock " + enchant.getDisplayName() + ".");
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
        send(player, "<gradient:#00CFFF:#0066FF><bold>ENCHANT UPGRADED!</bold></gradient> <gray>" + enchant.getDisplayName() + " <dark_gray>»</dark_gray> <aqua>+" + upgraded + "</aqua> <gray>level(s)");
        send(player, "<gray>Cost: <yellow>" + format(totalCost) + "</yellow><gray> tokens.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
    }

    public YamlConfiguration getEnchantConfig(PickaxeEnchant enchant) {
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

    public boolean isProcMessagesEnabled(PickaxeEnchant enchant) {
        return plugin.getConfig().getBoolean("pickaxe.enchant-processing.proc-messages.enabled", true)
                && getEnchantBoolean(enchant, "proc-message.enabled", true);
    }

    public String getProcMessage(PickaxeEnchant enchant) {
        return getEnchantString(enchant, "proc-message.text", "<gradient:#00CFFF:#0066FF><bold>" + enchant.getDisplayName() + "</bold></gradient> <gray>activated!");
    }

    public int getMaxLevel(PickaxeEnchant enchant) {
        return getEnchantInt(enchant, "max-level", enchant == PickaxeEnchant.EFFICIENCY ? 255 : 500);
    }

    public int getUnlockLevel(PickaxeEnchant enchant) {
        return getEnchantInt(enchant, "unlock-pickaxe-level", enchant.getUnlockLevel());
    }

    public boolean isUnlocked(Player player, PickaxeEnchant enchant) {
        if (enchant == PickaxeEnchant.EFFICIENCY) {
            return true;
        }
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);
        return profile.getPickaxeLevel() >= getUnlockLevel(enchant);
    }

    public BigInteger getUpgradeTokenCost(PickaxeEnchant enchant, int currentLevel) {
        BigInteger base = getEnchantBigInteger(enchant, "base-token-cost", BigInteger.valueOf(1000));
        BigInteger increase = getEnchantBigInteger(enchant, "token-cost-increase", BigInteger.valueOf(500));
        return base.add(increase.multiply(BigInteger.valueOf(Math.max(0, currentLevel))));
    }

    public BigInteger getTotalUpgradeCost(PickaxeEnchant enchant, int currentLevel, int amount) {
        if (amount <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger total = BigInteger.ZERO;
        int max = getMaxLevel(enchant);
        for (int i = 0; i < amount && currentLevel + i < max; i++) {
            total = total.add(getUpgradeTokenCost(enchant, currentLevel + i));
        }
        return total;
    }

    private ItemStack createEnchantItem(Player player, PickaxeEnchant enchant) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);
        int level = profile.getEnchantLevel(enchant);
        int maxLevel = getMaxLevel(enchant);
        BigInteger nextCost = getUpgradeTokenCost(enchant, level);
        boolean unlocked = isUnlocked(player, enchant);
        Material material = getMaterial(getEnchantString(enchant, "material", defaultMaterial(enchant).name()), defaultMaterial(enchant));
        String name = unlocked
                ? getEnchantString(enchant, "menu-name", "<gradient:#00CFFF:#0066FF><bold>" + enchant.getDisplayName() + "</bold></gradient>")
                : getEnchantString(enchant, "locked-menu-name", "<gray>" + enchant.getDisplayName() + " <red>- Locked");

        List<String> lore = new ArrayList<>();
        if (unlocked) {
            lore.add("<dark_gray>Unlocked Enchant");
            lore.add("");
            lore.add(getEnchantString(enchant, "description", "<gray>Upgrade this enchant."));
            lore.add("");
            lore.add("<gray>Level: <aqua>" + level + "</aqua><gray>/<aqua>" + maxLevel);
            lore.add("<gray>Next Level: <yellow>" + format(nextCost) + "</yellow><gray> tokens");
            lore.add("");
            lore.add(level >= maxLevel ? "<green><bold>MAXED</bold>" : "<yellow>Click to upgrade.");
        } else {
            lore.add("<dark_gray>Locked Enchant");
            lore.add("");
            lore.add(getEnchantString(enchant, "description", "<gray>Upgrade this enchant."));
            lore.add("");
            lore.add("<gray>Unlock at Pickaxe Level <aqua>" + getUnlockLevel(enchant));
        }

        ItemStack item = createActionItem(material, name, lore, "open_upgrade");
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(enchantKey, PersistentDataType.STRING, enchant.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String replacePickaxePlaceholders(String text, Player player, PlayerProfile profile) {
        String replaced = text
                .replace("%player%", player.getName())
                .replace("%slot%", String.valueOf(profile.getPickaxeSlot() + 1))
                .replace("%tokens%", format(profile.getTokens()))
                .replace("%gems%", format(profile.getGems()))
                .replace("%beacons%", format(profile.getBeacons()))
                .replace("%pickaxe_level%", String.valueOf(profile.getPickaxeLevel()))
                .replace("%pickaxe_xp%", String.valueOf(profile.getPickaxeXp()))
                .replace("%pickaxe_xp_required%", String.valueOf(getXpRequiredForNextPickaxeLevel(profile.getPickaxeLevel())));
        for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
            replaced = replaced.replace("%" + enchant.getId() + "%", String.valueOf(profile.getEnchantLevel(enchant)));
        }
        return replaced.replace("%fortune%", "0");
    }

    private List<String> replacePickaxeLore(List<String> lines, Player player, PlayerProfile profile) {
        List<String> replaced = new ArrayList<>();
        for (String line : lines) {
            if (line.contains("%fortune%")) {
                continue;
            }
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
        ItemStack filler = createActionItem(
                getMaterial(menuConfig.getString(path + ".material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE),
                menuConfig.getString(path + ".name", " "),
                menuConfig.getStringList(path + ".lore"),
                "filler"
        );
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
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(menuActionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private String getMenuAction(ItemStack item) {
        if (isAir(item) || !item.hasItemMeta()) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        String action = meta == null ? null : meta.getPersistentDataContainer().get(menuActionKey, PersistentDataType.STRING);
        return action == null ? "" : action;
    }

    private PickaxeEnchant getMenuEnchant(ItemStack item) {
        if (isAir(item) || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String id = meta == null ? null : meta.getPersistentDataContainer().get(enchantKey, PersistentDataType.STRING);
        return PickaxeEnchant.fromInput(id);
    }

    private Integer getMenuSlot(ItemStack item) {
        if (isAir(item) || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(slotKey, PersistentDataType.INTEGER);
    }

    private Integer getUpgradeAmount(ItemStack item) {
        if (isAir(item) || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(upgradeAmountKey, PersistentDataType.INTEGER);
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
            amount++;
            level++;
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

    private String getUpgradeMenuTitle(PickaxeEnchant enchant) {
        return "Upgrade " + enchant.getDisplayName();
    }

    private Material getMaterial(String input, Material fallback) {
        if (input == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(input);
        return material == null ? fallback : material;
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
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void createDefaultEnchantFiles() {
        for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
            File file = getEnchantFile(enchant);
            if (file.exists()) {
                continue;
            }
            createFallbackEnchantFile(enchant, file);
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
        config.set("unlock-pickaxe-level", enchant.getUnlockLevel());
        config.set("material", defaultMaterial(enchant).name());
        config.set("menu-name", "<gradient:#00CFFF:#0066FF><bold>" + enchant.getDisplayName() + "</bold></gradient>");
        config.set("locked-menu-name", "<gray>" + enchant.getDisplayName() + " <red>- Locked");
        config.set("description", defaultDescription(enchant));
        config.set("proc-message.enabled", true);
        config.set("proc-message.text", "<gradient:#00CFFF:#0066FF><bold>" + enchant.getDisplayName() + "</bold></gradient> <dark_gray>»</dark_gray> <gray>Activated on <aqua>%blocks%</aqua><gray> block(s)!");
        config.set("max-level", enchant == PickaxeEnchant.EFFICIENCY ? 255 : 500);
        config.set("base-token-cost", "100000");
        config.set("token-cost-increase", "10000");
        config.set("base-chance", 0.0);
        config.set("chance-per-level", 0.02);
        config.set("max-chance", 35.0);
        config.set("min-reward", "1");
        config.set("max-reward", "3");
        config.set("reward-per-level", "1");
        config.set("levels-per-extra-reward", 25);
        config.set("extra-reward", "5");
        config.set("reward-multiplier", 1.0);
        config.set("max-reward-rolls-per-break", 350);

        switch (enchant) {
            case EFFICIENCY -> {
                config.set("max-level", 255);
                config.set("base-token-cost", "0");
                config.set("token-cost-increase", "0");
            }
            case HASTE, SPEED -> {
                config.set("max-level", 10);
                config.set("base-token-cost", "200000");
                config.set("token-cost-increase", "6500000");
            }
            case TOKEN_FINDER -> {
                config.set("max-level", 7500);
                config.set("base-token-cost", "45");
                config.set("token-cost-increase", "2500");
                config.set("base-chance", 100.0);
                config.set("chance-per-level", 0.0);
                config.set("max-chance", 100.0);
                config.set("min-reward", "50");
                config.set("max-reward", "250");
                config.set("reward-per-level", "8");
            }
            case JACKHAMMER -> {
                config.set("chance-per-level", 0.04);
                config.set("max-chance", 25.0);
                config.set("layer-radius", 64);
                config.set("max-extra-blocks", 5000);
            }
            case GEM_FINDER, BEACON_FINDER, GANG_POINT_FINDER -> {
                config.set("base-token-cost", "250000");
                config.set("token-cost-increase", "15000");
                config.set("base-chance", 2.0);
                config.set("chance-per-level", 0.03);
                config.set("max-chance", 40.0);
                if (enchant == PickaxeEnchant.GANG_POINT_FINDER) {
                    config.set("commands", List.of("gang points give %player% %amount%"));
                }
            }
            case KEY_FINDER -> {
                config.set("base-token-cost", "500000");
                config.set("token-cost-increase", "25000");
                config.set("base-chance", 0.0);
                config.set("chance-per-level", 0.01);
                config.set("max-chance", 10.0);
                config.set("commands", List.of("crate key give %player% mine %amount%"));
            }
            case DOUBLE_STRIKE -> {
                config.set("base-chance", 0.0);
                config.set("chance-per-level", 0.015);
                config.set("max-chance", 20.0);
            }
            case HYDROGEN_BOMB, BLACK_HOLE, DRAGONS_WRATH, VOLCANO, PROPHET -> {
                config.set("base-token-cost", "500000");
                config.set("token-cost-increase", "35000");
                config.set("chance-per-level", 0.018);
                config.set("max-chance", 28.0);
                config.set("min-reward", "500");
                config.set("max-reward", "1500");
                config.set("reward-per-level", "15");
            }
            default -> {
            }
        }
    }

    private Material defaultMaterial(PickaxeEnchant enchant) {
        return switch (enchant) {
            case EFFICIENCY -> Material.DIAMOND_PICKAXE;
            case HASTE -> Material.SUGAR;
            case SPEED -> Material.FEATHER;
            case TOKEN_FINDER -> Material.SUNFLOWER;
            case JACKHAMMER -> Material.ANVIL;
            case MINE_STRIKE -> Material.FIRE_CHARGE;
            case GEM_FINDER -> Material.EMERALD;
            case BEACON_FINDER -> Material.PRISMARINE_CRYSTALS;
            case VEIN_MINER -> Material.GOLDEN_PICKAXE;
            case KEY_FINDER -> Material.TRIPWIRE_HOOK;
            case TOKEN_EXPLOSION -> Material.TNT;
            case NUKE -> Material.TNT_MINECART;
            case METEOR -> Material.MAGMA_CREAM;
            case TOKEN_MERCHANT -> Material.GOLD_INGOT;
            case SECOND_HAND -> Material.IRON_PICKAXE;
            case HOLY_ARROWS -> Material.ARROW;
            case KEY_MERCHANT -> Material.COPPER_INGOT;
            case METEOR_STRIKE -> Material.BLAZE_POWDER;
            case GEM_MERCHANT -> Material.EMERALD_BLOCK;
            case SHOCKWAVE -> Material.PRISMARINE_SHARD;
            case CLUSTER_BOMB -> Material.SLIME_BALL;
            case TOKEN_GREED -> Material.GOLD_BLOCK;
            case WILD_BLAZE -> Material.BLAZE_POWDER;
            case DOUBLE_STRIKE -> Material.REDSTONE;
            case GANG_POINT_FINDER -> Material.LIME_DYE;
            case SNOW_ARMY -> Material.SNOWBALL;
            case DRAGONS_WRATH -> Material.DRAGON_BREATH;
            case SCAVENGER -> Material.GOLDEN_SHOVEL;
            case ENDERMAN_ABOMINATION -> Material.ENDER_PEARL;
            case VOLCANO -> Material.LAVA_BUCKET;
            case HYDROGEN_BOMB -> Material.TNT;
            case PROPHET -> Material.TOTEM_OF_UNDYING;
            case BLACK_HOLE -> Material.ENDER_EYE;
        };
    }

    private String defaultDescription(PickaxeEnchant enchant) {
        return switch (enchant) {
            case EFFICIENCY -> "<gray>Your pickaxe is always max efficiency.";
            case HASTE -> "<gray>Increase your mining speed.";
            case SPEED -> "<gray>Increase your running and flying speed.";
            case TOKEN_FINDER -> "<gray>Earn more Tokens with every block you break.";
            case JACKHAMMER -> "<gray>Breaks the entire layer of your mine when activated.";
            case MINE_STRIKE -> "<gray>Blasts a chunk of the mine for extra Tokens.";
            case GEM_FINDER -> "<gray>Grants Gems while mining.";
            case BEACON_FINDER -> "<gray>Earn Beacons as you mine.";
            case VEIN_MINER -> "<gray>Breaks blocks horizontally and vertically.";
            case KEY_FINDER -> "<gray>Has a chance to find crate keys while mining.";
            case TOKEN_EXPLOSION -> "<gray>Multiplies Tokens from Token Miner.";
            case NUKE -> "<gray>Destroys your mine in a single blast.";
            case METEOR -> "<gray>Summons meteors that grant Tokens and Gems.";
            case TOKEN_MERCHANT -> "<gray>Increases Tokens earned from Token Miner.";
            case SECOND_HAND -> "<gray>Mines virtual blocks and procs enchants again.";
            case HOLY_ARROWS -> "<gray>Spawns explosive arrows, granting Tokens.";
            case KEY_MERCHANT -> "<gray>Multiplies keys found from Key Finder.";
            case METEOR_STRIKE -> "<gray>Unleashes firestorms that grant Tokens and Gems.";
            case GEM_MERCHANT -> "<gray>Multiplies Gems from Gem Finder.";
            case SHOCKWAVE -> "<gray>Releases expanding shockwave rings.";
            case CLUSTER_BOMB -> "<gray>Summons slime bombs that explode your mine.";
            case TOKEN_GREED -> "<gray>Gives a large amount of Tokens when activated.";
            case WILD_BLAZE -> "<gray>Summons a wild blaze that rewards Tokens and Gems per block broken.";
            case DOUBLE_STRIKE -> "<gray>Has a chance to proc your enchants a second time.";
            case GANG_POINT_FINDER -> "<gray>Has a chance to find gang points.";
            case SNOW_ARMY -> "<gray>Summons a snowman squad that destroys layers of your mine, rewarding Tokens and Gems.";
            case DRAGONS_WRATH -> "<gray>Summons the Ender Dragon to rain destruction, granting Tokens and Gems.";
            case SCAVENGER -> "<gray>Has a chance to dig up lots of Tokens and Gems.";
            case ENDERMAN_ABOMINATION -> "<gray>Summons Endermen which steal blocks, rewarding Tokens and Gems.";
            case VOLCANO -> "<gray>Erupts explosions onto the mine, rewarding Tokens and Gems.";
            case HYDROGEN_BOMB -> "<gray>Nuke's big brother, destroying your mine and granting Tokens, Gems, and Rank XP.";
            case PROPHET -> "<gray>Summons the prophet and rains TNT down onto your mine.";
            case BLACK_HOLE -> "<gray>Sucks blocks out of your mine and into space, rewarding Tokens and Gems.";
        };
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
                "<gray>Pickaxe Level: <aqua>%pickaxe_level%</aqua>",
                "<gray>Pickaxe XP: <aqua>%pickaxe_xp%</aqua><dark_gray>/</dark_gray><aqua>%pickaxe_xp_required%</aqua>",
                "<gray>Tokens: <yellow>%tokens%</yellow>",
                "<gray>Gems: <light_purple>%gems%</light_purple>",
                "<gray>Beacons: <aqua>%beacons%</aqua>",
                "",
                "<yellow>Click to change your pickaxe slot."
        ));
        config.set("enchants-menu.change-slot.slot", 49);
        config.set("enchants-menu.change-slot.material", "NETHERITE_PICKAXE");
        config.set("enchants-menu.change-slot.name", "<gradient:#00CFFF:#54FCFC:#008CFC><bold>Change Pickaxe Slot</bold></gradient>");
        config.set("enchants-menu.change-slot.lore", List.of("", "<gray>Current Slot: <aqua>%slot%</aqua>", "", "<yellow>Click to choose slots 1-9."));
        config.set("upgrade-menu.filler.enabled", true);
        config.set("upgrade-menu.filler.material", "BLACK_STAINED_GLASS_PANE");
        config.set("upgrade-menu.filler.name", " ");
        config.set("upgrade-menu.filler.lore", List.of());
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
}
