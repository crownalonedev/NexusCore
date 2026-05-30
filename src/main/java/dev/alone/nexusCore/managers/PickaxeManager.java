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
import java.util.List;
import java.util.Locale;

public class PickaxeManager {

    private static final int[] ENCHANT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final NexusCore plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final NamespacedKey pickaxeKey;
    private final NamespacedKey menuActionKey;
    private final NamespacedKey enchantKey;
    private final NamespacedKey slotKey;
    private final NamespacedKey upgradeAmountKey;

    private final File menuFile;
    private final File enchantsFolder;
    private final File tokenEnchantsFile;
    private final File gemEnchantsFile;

    private YamlConfiguration menuConfig;
    private YamlConfiguration tokenEnchantsConfig;
    private YamlConfiguration gemEnchantsConfig;
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
        this.enchantsFolder = new File(plugin.getDataFolder(), "enchants");
        this.tokenEnchantsFile = new File(enchantsFolder, "token-enchants.yml");
        this.gemEnchantsFile = new File(enchantsFolder, "gem-enchants.yml");

        if (!enchantsFolder.exists()) {
            enchantsFolder.mkdirs();
        }

        createDefaultMenuFile();
        createGroupedEnchantFiles();
        reload();
    }

    public void reload() {
        this.menuConfig = YamlConfiguration.loadConfiguration(menuFile);
        this.tokenEnchantsConfig = YamlConfiguration.loadConfiguration(tokenEnchantsFile);
        this.gemEnchantsConfig = YamlConfiguration.loadConfiguration(gemEnchantsFile);
        fillMissingGroupedEnchantDefaults();
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
                ensureStarterEnchant(profile);
                updateExperienceBar(player, profile);

                if (!isNexusPickaxe(player.getInventory().getItemInMainHand())) {
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
        ensureStarterEnchant(profile);

        boolean autoGiveFirstJoin = plugin.getConfig().getBoolean("pickaxe.auto-give-on-first-join", true);
        boolean giveIfMissing = plugin.getConfig().getBoolean("pickaxe.give-if-missing-on-join", true);
        boolean firstJoinPickaxe = autoGiveFirstJoin && !profile.hasReceivedPickaxe();
        boolean missingPickaxe = giveIfMissing && !hasPickaxe(player);

        if (firstJoinPickaxe || missingPickaxe) {
            syncPickaxe(player);
            profile.setReceivedPickaxe(true);
            plugin.getProfileManager().saveProfile(profile);
            return;
        }

        syncPickaxe(player);
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
        return base + ((long) Math.max(1, level) - 1L) * increase;
    }

    public int getMaxPickaxeLevel() {
        return Math.max(1, plugin.getConfig().getInt("pickaxe.leveling.max-level", 950));
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
        ensureStarterEnchant(profile);

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
        ensureStarterEnchant(profile);

        Material material = getMaterial(plugin.getConfig().getString("pickaxe.material", "NETHERITE_PICKAXE"), Material.NETHERITE_PICKAXE);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.displayName(color(plugin.getConfig().getString("pickaxe.name", "<gradient:#00CFFF:#0066FF><bold>%player%'s Pickaxe</bold></gradient>").replace("%player%", player.getName())));
        meta.lore(buildPickaxeLore(player, profile));
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

    private List<Component> buildPickaxeLore(Player player, PlayerProfile profile) {
        List<Component> lore = new ArrayList<>();
        lore.add(color("<dark_gray>Mining Tool"));
        lore.add(color(""));
        lore.add(color("<gradient:#00CFFF:#0066FF><bold>Owner</bold></gradient> <dark_gray>»</dark_gray> <white>" + player.getName()));
        lore.add(color("<gradient:#00CFFF:#0066FF><bold>Level</bold></gradient> <dark_gray>»</dark_gray> <aqua>" + profile.getPickaxeLevel() + " <dark_gray>(</dark_gray><gray>" + profile.getPickaxeXp() + " / " + getXpRequiredForNextPickaxeLevel(profile.getPickaxeLevel()) + " XP</gray><dark_gray>)"));
        lore.add(color("<gradient:#00CFFF:#0066FF><bold>Slot</bold></gradient> <dark_gray>»</dark_gray> <aqua>" + (profile.getPickaxeSlot() + 1)));
        lore.add(color(""));
        lore.add(color("<gradient:#FCA800:#FCFC54><bold>Token Enchants</bold></gradient>"));
        addEnchantLoreLine(lore, profile, PickaxeEnchant.JACKHAMMER, "<gold>");
        addEnchantLoreLine(lore, profile, PickaxeEnchant.TOKEN_FINDER, "<yellow>");
        addUnlockedCategoryLines(lore, profile, false, List.of(PickaxeEnchant.EFFICIENCY, PickaxeEnchant.JACKHAMMER, PickaxeEnchant.TOKEN_FINDER));
        lore.add(color(""));
        lore.add(color("<gradient:#32FF7E:#00CFFF><bold>Gem Enchants</bold></gradient>"));
        addUnlockedCategoryLines(lore, profile, true, List.of());
        lore.add(color(""));
        lore.add(color("<gradient:#00CFFF:#0066FF><bold>Statistics</bold></gradient>"));
        lore.add(color(" <dark_gray>┃ <gray>Prestige: <aqua>" + profile.getPrestige()));
        lore.add(color(" <dark_gray>┃ <gray>Blocks Mined: <green>" + format(BigInteger.valueOf(profile.getBlocksMined()))));
        lore.add(color(" <dark_gray>┃ <gray>Tokens: <yellow>" + format(profile.getTokens())));
        lore.add(color(" <dark_gray>┃ <gray>Gems: <light_purple>" + format(profile.getGems())));
        lore.add(color(""));
        lore.add(color("<yellow>Right-click to open enchants."));
        return lore;
    }

    private void addUnlockedCategoryLines(List<Component> lore, PlayerProfile profile, boolean gem, List<PickaxeEnchant> skip) {
        boolean added = false;
        for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
            if (skip.contains(enchant) || enchant == PickaxeEnchant.EFFICIENCY || isGemEnchant(enchant) != gem) {
                continue;
            }
            int level = profile.getEnchantLevel(enchant);
            if (level <= 0) {
                continue;
            }
            addEnchantLoreLine(lore, profile, enchant, gem ? "<light_purple>" : "<yellow>");
            added = true;
        }
        if (!added && gem) {
            lore.add(color(" <dark_gray>┃ <gray>None"));
        }
    }

    private void addEnchantLoreLine(List<Component> lore, PlayerProfile profile, PickaxeEnchant enchant, String levelColor) {
        int level = enchant == PickaxeEnchant.EFFICIENCY ? getMaxLevel(PickaxeEnchant.EFFICIENCY) : profile.getEnchantLevel(enchant);
        if (level <= 0) {
            return;
        }
        lore.add(color(" <dark_gray>┃ <gray>" + enchant.getDisplayName() + ": " + levelColor + level));
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

    private void ensureStarterEnchant(PlayerProfile profile) {
        if (profile == null) {
            return;
        }
        int starterLevel = Math.max(0, plugin.getConfig().getInt("pickaxe.starter-token-miner-level", 10));
        if (profile.getEnchantLevel(PickaxeEnchant.TOKEN_FINDER) < starterLevel) {
            profile.setEnchantLevel(PickaxeEnchant.TOKEN_FINDER, starterLevel);
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
        openEnchantCategoryMenu(player, false);
    }

    public void openGemEnchantMenu(Player player) {
        openEnchantCategoryMenu(player, true);
    }

    private void openEnchantCategoryMenu(Player player, boolean gemMenu) {
        String title = gemMenu ? getGemMenuTitle() : getEnchantMenuTitle();
        int rows = Math.max(1, Math.min(6, menuConfig.getInt("enchants-menu.rows", 6)));
        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);
        fillMenu(inventory, "enchants-menu.filler");

        PlayerProfile profile = plugin.getProfileManager().getProfile(player);
        updateExperienceBar(player, profile);

        inventory.setItem(4, createActionItem(
                gemMenu ? Material.EMERALD : Material.GOLD_INGOT,
                gemMenu ? "<gradient:#32FF7E:#00CFFF><bold>Gem Enchants</bold></gradient>" : "<gradient:#FCA800:#FCFC54><bold>Token Enchants</bold></gradient>",
                List.of("", "<gray>Pickaxe Level: <aqua>" + profile.getPickaxeLevel(), "<gray>Tokens: <yellow>" + format(profile.getTokens()), "<gray>Gems: <light_purple>" + format(profile.getGems()), "", "<dark_gray>Click enchants below to upgrade."),
                "info"
        ));

        int index = 0;
        for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
            if (!isEnchantEnabled(enchant) || isGemEnchant(enchant) != gemMenu) {
                continue;
            }
            if (index >= ENCHANT_SLOTS.length) {
                break;
            }
            inventory.setItem(ENCHANT_SLOTS[index], createEnchantItem(player, enchant));
            index++;
        }

        inventory.setItem(45, createActionItem(
                Material.GOLD_INGOT,
                gemMenu ? "<yellow><bold>Token Enchants</bold>" : "<yellow><bold>Token Enchants <green>(SELECTED)</green></bold>",
                gemMenu ? List.of("", "<gray>View upgrades that cost <yellow>Tokens</yellow><gray>.", "", "<yellow>Click to switch.") : List.of("", "<gray>Currently viewing this category."),
                gemMenu ? "open_token_enchants" : "info"
        ));

        inventory.setItem(49, createActionItem(
                Material.NETHERITE_PICKAXE,
                "<gradient:#00CFFF:#0066FF><bold>Change Pickaxe Slot</bold></gradient>",
                List.of("", "<gray>Current Slot: <aqua>" + (profile.getPickaxeSlot() + 1), "", "<yellow>Click to choose slots 1-9."),
                "slot_selector"
        ));

        inventory.setItem(53, createActionItem(
                Material.EMERALD,
                gemMenu ? "<green><bold>Gem Enchants <green>(SELECTED)</green></bold>" : "<green><bold>Gem Enchants</bold>",
                gemMenu ? List.of("", "<gray>Currently viewing this category.") : List.of("", "<gray>View upgrades that cost <light_purple>Gems</light_purple><gray>.", "", "<yellow>Click to switch."),
                gemMenu ? "info" : "open_gem_enchants"
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
        inventory.setItem(40, createActionItem(Material.ARROW, "<yellow><bold>Back</bold>", List.of("", "<gray>Return to the enchant list."), isGemEnchant(enchant) ? "open_gem_enchants" : "open_token_enchants"));

        player.openInventory(inventory);
        playMenuSound(player, "sounds.menus.open");
    }

    private void setUpgradeButton(Inventory inventory, int slot, Player player, PlayerProfile profile, PickaxeEnchant enchant, int amount, String name) {
        int realAmount = amount == -1 ? getMaxAffordableAmount(player, enchant) : amount;
        BigInteger cost = getTotalUpgradeCost(enchant, profile.getEnchantLevel(enchant), realAmount);
        String currencyColor = isGemEnchant(enchant) ? "<light_purple>" : "<yellow>";

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Enchant: <aqua>" + enchant.getDisplayName());
        lore.add("<gray>Current Level: <aqua>" + profile.getEnchantLevel(enchant) + "</aqua><gray>/<aqua>" + getMaxLevel(enchant));
        lore.add("<gray>Upgrade Amount: <aqua>" + Math.max(0, realAmount));
        lore.add("<gray>Cost: " + currencyColor + format(cost) + " <gray>" + getCurrencyDisplayName(enchant));
        lore.add("");
        lore.add(realAmount <= 0 ? "<red>No affordable levels available." : "<green>[!] Click To Upgrade");

        ItemStack item = createActionItem(isGemEnchant(enchant) ? Material.EMERALD : Material.LIME_DYE, name, lore, "buy_upgrade");
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(enchantKey, PersistentDataType.STRING, enchant.getId());
            meta.getPersistentDataContainer().set(upgradeAmountKey, PersistentDataType.INTEGER, realAmount);
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    public void openSlotMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, getSlotMenuTitle());
        fillMenu(inventory, "slot-menu.filler");

        PlayerProfile profile = plugin.getProfileManager().getProfile(player);
        int currentSlot = profile.getPickaxeSlot();
        int startSlot = 9;

        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            boolean current = hotbarSlot == currentSlot;
            ItemStack item = createActionItem(
                    current ? Material.LIME_STAINED_GLASS_PANE : Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                    current ? "<green><bold>Slot " + (hotbarSlot + 1) + "</bold></green>" : "<aqua><bold>Slot " + (hotbarSlot + 1) + "</bold></aqua>",
                    current ? List.of("", "<green>Your pickaxe is currently here.") : List.of("", "<gray>Click to move your pickaxe here."),
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
        if (!title.equals(getEnchantMenuTitle()) && !title.equals(getGemMenuTitle()) && !title.equals(getSlotMenuTitle()) && !title.startsWith("Upgrade ")) {
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
        if ("open_gem_enchants".equalsIgnoreCase(action)) {
            openGemEnchantMenu(player);
            return true;
        }
        if ("open_token_enchants".equalsIgnoreCase(action)) {
            openEnchantMenu(player);
            return true;
        }
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
            BigInteger cost = getUpgradeCost(enchant, currentLevel);
            if (!hasCurrency(profile, enchant, cost)) {
                break;
            }
            removeCurrency(profile, enchant, cost);
            profile.addEnchantLevel(enchant, 1);
            totalCost = totalCost.add(cost);
            upgraded++;
        }

        if (upgraded <= 0) {
            BigInteger nextCost = getUpgradeCost(enchant, profile.getEnchantLevel(enchant));
            send(player, "<red>You need " + getCurrencyColor(enchant) + format(nextCost) + " <red>" + getCurrencyDisplayName(enchant) + " to upgrade <white>" + enchant.getDisplayName() + "</white><red>.");
            return;
        }

        plugin.getProfileManager().saveProfile(profile);
        syncPickaxe(player);
        send(player, "<gradient:#00CFFF:#0066FF><bold>ENCHANT UPGRADED!</bold></gradient> <gray>" + enchant.getDisplayName() + " <dark_gray>»</dark_gray> <aqua>+" + upgraded + "</aqua> <gray>level(s)");
        send(player, "<gray>Cost: " + getCurrencyColor(enchant) + format(totalCost) + " <gray>" + getCurrencyDisplayName(enchant) + ".");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
    }

    public YamlConfiguration getEnchantConfig(PickaxeEnchant enchant) {
        return getGroupedConfig(enchant);
    }

    public boolean getEnchantBoolean(PickaxeEnchant enchant, String path, boolean fallback) {
        return getGroupedConfig(enchant).getBoolean("enchants." + enchant.getId() + "." + path, fallback);
    }

    public int getEnchantInt(PickaxeEnchant enchant, String path, int fallback) {
        return getGroupedConfig(enchant).getInt("enchants." + enchant.getId() + "." + path, fallback);
    }

    public double getEnchantDouble(PickaxeEnchant enchant, String path, double fallback) {
        return getGroupedConfig(enchant).getDouble("enchants." + enchant.getId() + "." + path, fallback);
    }

    public String getEnchantString(PickaxeEnchant enchant, String path, String fallback) {
        return getGroupedConfig(enchant).getString("enchants." + enchant.getId() + "." + path, fallback);
    }

    public List<String> getEnchantStringList(PickaxeEnchant enchant, String path) {
        return getGroupedConfig(enchant).getStringList("enchants." + enchant.getId() + "." + path);
    }

    public BigInteger getEnchantBigInteger(PickaxeEnchant enchant, String path, BigInteger fallback) {
        String value = getEnchantString(enchant, path, null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return new BigInteger(value.replace(",", "").replace("_", "").trim()).max(BigInteger.ZERO);
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning("Invalid number in " + (isGemEnchant(enchant) ? "gem" : "token") + " enchant config for " + enchant.getId() + " at " + path + ": " + value);
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
        return getUpgradeCost(enchant, currentLevel);
    }

    public BigInteger getUpgradeCost(PickaxeEnchant enchant, int currentLevel) {
        if (isGemEnchant(enchant)) {
            BigInteger base = getEnchantBigInteger(enchant, "base-gem-cost", BigInteger.valueOf(25));
            BigInteger increase = getEnchantBigInteger(enchant, "gem-cost-increase", BigInteger.valueOf(10));
            return base.add(increase.multiply(BigInteger.valueOf(Math.max(0, currentLevel))));
        }

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
            total = total.add(getUpgradeCost(enchant, currentLevel + i));
        }
        return total;
    }

    private ItemStack createEnchantItem(Player player, PickaxeEnchant enchant) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);
        int level = profile.getEnchantLevel(enchant);
        int maxLevel = getMaxLevel(enchant);
        BigInteger nextCost = getUpgradeCost(enchant, level);
        boolean unlocked = isUnlocked(player, enchant);
        Material material = getMaterial(getEnchantString(enchant, "material", defaultMaterial(enchant).name()), defaultMaterial(enchant));
        String name = unlocked
                ? getEnchantString(enchant, "menu-name", colorName(enchant))
                : getEnchantString(enchant, "locked-menu-name", colorName(enchant) + " <red>- Locked");

        List<String> lore = new ArrayList<>();
        lore.add(unlocked ? "<dark_gray>Unlocked Enchant" : "<dark_gray>Locked Enchant");
        lore.add("");
        lore.add(getEnchantString(enchant, "description", "<gray>Upgrade this enchant."));
        lore.add("");
        lore.add("<gradient:#FFFFFF:#BFBFBF><bold>Information</bold></gradient>");
        lore.add(" <dark_gray>┃ <gray>Level: <aqua>" + level + "</aqua><dark_gray>/</dark_gray><aqua>" + maxLevel);
        lore.add(" <dark_gray>┃ <gray>Your Activation Chance: " + formatChance(enchant, level));
        lore.add(" <dark_gray>┃ <gray>Total Activations: <aqua>0");
        lore.add("");
        lore.add("<gradient:#FFFFFF:#BFBFBF><bold>Upgrades</bold></gradient>");
        lore.add(" <dark_gray>┃ <gray>1 Level: " + getCurrencyColor(enchant) + format(nextCost));
        lore.add(" <dark_gray>┃ <gray>Next Level: " + getCurrencyColor(enchant) + format(getTotalUpgradeCost(enchant, level, Math.max(1, Math.min(499, maxLevel - level)))));
        lore.add("");

        if (unlocked) {
            lore.add(level >= maxLevel ? "<green><bold>MAXED</bold>" : "<green>[!] Click To Upgrade");
        } else {
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

    private String formatChance(PickaxeEnchant enchant, int level) {
        double base = getEnchantDouble(enchant, "base-chance", 0.0);
        double per = getEnchantDouble(enchant, "chance-per-level", 0.0);
        double max = getEnchantDouble(enchant, "max-chance", 100.0);
        double chance = Math.max(0.0, Math.min(max, base + (level * per)));
        if (chance >= 100.0) {
            return "<red>100% <dark_gray>(</dark_gray><red>1 in 1</red><dark_gray>)";
        }
        int oneIn = chance <= 0.0 ? 0 : (int) Math.max(1, Math.round(100.0 / chance));
        return "<aqua>" + String.format(Locale.US, "%.2f", chance) + "% <dark_gray>(</dark_gray><aqua>1 in " + oneIn + "</aqua><dark_gray>)";
    }

    private String colorName(PickaxeEnchant enchant) {
        if (isGemEnchant(enchant)) {
            return "<gradient:#32FF7E:#00CFFF><bold>" + enchant.getDisplayName() + "</bold></gradient>";
        }
        return "<gradient:#FCA800:#FCFC54><bold>" + enchant.getDisplayName() + "</bold></gradient>";
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
        BigInteger balance = isGemEnchant(enchant) ? profile.getGems() : profile.getTokens();
        while (level < maxLevel) {
            BigInteger cost = getUpgradeCost(enchant, level);
            if (balance.compareTo(cost) < 0) {
                break;
            }
            balance = balance.subtract(cost);
            amount++;
            level++;
        }
        return amount;
    }

    private boolean hasCurrency(PlayerProfile profile, PickaxeEnchant enchant, BigInteger cost) {
        return isGemEnchant(enchant) ? profile.getGems().compareTo(cost) >= 0 : profile.getTokens().compareTo(cost) >= 0;
    }

    private void removeCurrency(PlayerProfile profile, PickaxeEnchant enchant, BigInteger cost) {
        if (isGemEnchant(enchant)) {
            profile.removeGems(cost);
        } else {
            profile.removeTokens(cost);
        }
    }

    private String getCurrencyDisplayName(PickaxeEnchant enchant) {
        return isGemEnchant(enchant) ? "gems" : "tokens";
    }

    private String getCurrencyColor(PickaxeEnchant enchant) {
        return isGemEnchant(enchant) ? "<light_purple>" : "<yellow>";
    }

    private YamlConfiguration getGroupedConfig(PickaxeEnchant enchant) {
        return isGemEnchant(enchant) ? gemEnchantsConfig : tokenEnchantsConfig;
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
            if (slot != excludedSlot && isAir(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private String getEnchantMenuTitle() {
        return menuConfig.getString("enchants-menu.title", "Nexus Token Enchants");
    }

    private String getGemMenuTitle() {
        return menuConfig.getString("gem-enchants-menu.title", "Nexus Gem Enchants");
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
        return NumberFormat.getNumberInstance(Locale.US).format(number == null ? BigInteger.ZERO : number);
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

    private void createGroupedEnchantFiles() {
        fillMissingGroupedEnchantDefaults();
    }

    private void fillMissingGroupedEnchantDefaults() {
        tokenEnchantsConfig = tokenEnchantsFile.exists() ? YamlConfiguration.loadConfiguration(tokenEnchantsFile) : new YamlConfiguration();
        gemEnchantsConfig = gemEnchantsFile.exists() ? YamlConfiguration.loadConfiguration(gemEnchantsFile) : new YamlConfiguration();

        boolean changedTokens = false;
        boolean changedGems = false;
        for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
            YamlConfiguration config = getGroupedConfig(enchant);
            String basePath = "enchants." + enchant.getId();
            if (!config.contains(basePath)) {
                applyEnchantDefaults(config, basePath, enchant);
                if (isGemEnchant(enchant)) {
                    changedGems = true;
                } else {
                    changedTokens = true;
                }
            }
        }

        if (changedTokens || !tokenEnchantsFile.exists()) {
            saveYaml(tokenEnchantsConfig, tokenEnchantsFile);
        }
        if (changedGems || !gemEnchantsFile.exists()) {
            saveYaml(gemEnchantsConfig, gemEnchantsFile);
        }
    }

    private void applyEnchantDefaults(YamlConfiguration config, String path, PickaxeEnchant enchant) {
        config.set(path + ".enabled", true);
        config.set(path + ".display-name", enchant.getDisplayName());
        config.set(path + ".unlock-pickaxe-level", enchant.getUnlockLevel());
        config.set(path + ".material", defaultMaterial(enchant).name());
        config.set(path + ".menu-name", colorName(enchant));
        config.set(path + ".locked-menu-name", colorName(enchant) + " <red>- Locked");
        config.set(path + ".description", defaultDescription(enchant));
        config.set(path + ".currency", isGemEnchant(enchant) ? "GEMS" : "TOKENS");
        config.set(path + ".proc-message.enabled", true);
        config.set(path + ".proc-message.text", colorName(enchant) + " <dark_gray>»</dark_gray> <gray>Activated on <aqua>%blocks%</aqua><gray> block(s)!");
        config.set(path + ".max-level", enchant == PickaxeEnchant.EFFICIENCY ? 255 : 500);
        config.set(path + ".base-token-cost", enchant == PickaxeEnchant.TOKEN_FINDER ? "45" : "100000");
        config.set(path + ".token-cost-increase", enchant == PickaxeEnchant.TOKEN_FINDER ? "2500" : "10000");
        config.set(path + ".base-gem-cost", "25");
        config.set(path + ".gem-cost-increase", "10");
        config.set(path + ".base-chance", enchant == PickaxeEnchant.TOKEN_FINDER ? 100.0 : 0.0);
        config.set(path + ".chance-per-level", enchant == PickaxeEnchant.TOKEN_FINDER ? 0.0 : 0.02);
        config.set(path + ".max-chance", enchant == PickaxeEnchant.TOKEN_FINDER ? 100.0 : 35.0);
        config.set(path + ".min-reward", enchant == PickaxeEnchant.TOKEN_FINDER ? "50" : "1");
        config.set(path + ".max-reward", enchant == PickaxeEnchant.TOKEN_FINDER ? "250" : "3");
        config.set(path + ".reward-per-level", enchant == PickaxeEnchant.TOKEN_FINDER ? "8" : "1");
        config.set(path + ".levels-per-extra-reward", 25);
        config.set(path + ".extra-reward", "5");
        config.set(path + ".reward-multiplier", 1.0);
        config.set(path + ".max-reward-rolls-per-break", 350);

        if (enchant == PickaxeEnchant.EFFICIENCY) {
            config.set(path + ".base-token-cost", "0");
            config.set(path + ".token-cost-increase", "0");
        }

        if (enchant == PickaxeEnchant.HASTE || enchant == PickaxeEnchant.SPEED) {
            config.set(path + ".max-level", 10);
            config.set(path + ".base-token-cost", "200000");
            config.set(path + ".token-cost-increase", "6500000");
        }

        if (enchant == PickaxeEnchant.GANG_POINT_FINDER) {
            config.set(path + ".commands", List.of("gang points give %player% %amount%"));
        }

        if (enchant == PickaxeEnchant.KEY_FINDER) {
            config.set(path + ".commands", List.of("crate key give %player% mine %amount%"));
        }

        if (enchant == PickaxeEnchant.PET_XP_FINDER) {
            config.set(path + ".commands", List.of("pets xp give %player% %amount%"));
        }
    }

    public boolean isGemEnchant(PickaxeEnchant enchant) {
        return switch (enchant) {
            case LOTTERY, SHATTER, JACKPOT, METEORITE, FIRECRACKER, DRAGONS_EYE, FIRECRACKS,
                    PET_XP_FINDER, CHARITY, EXCAVATOR, ARCTIC_DESTROYER, DYNAMITE, HIRED_HAND,
                    SOUL_REAPER, OVERFLOW, CHUGGERNAUT, HEROS_ASSISTANCE, SNOWSTORM, INVASION,
                    BLESSED, WORSHIP, THUNDERBIRD, SWARM, SUPERNOVA -> true;
            default -> false;
        };
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
            case LOTTERY -> Material.GOLD_INGOT;
            case SHATTER -> Material.QUARTZ;
            case JACKPOT -> Material.CHEST;
            case METEORITE -> Material.NETHERITE_SCRAP;
            case FIRECRACKER -> Material.FIREWORK_ROCKET;
            case DRAGONS_EYE -> Material.ENDER_EYE;
            case FIRECRACKS -> Material.FIRE_CHARGE;
            case PET_XP_FINDER -> Material.EXPERIENCE_BOTTLE;
            case CHARITY -> Material.EMERALD;
            case EXCAVATOR -> Material.GOLDEN_PICKAXE;
            case ARCTIC_DESTROYER -> Material.ICE;
            case DYNAMITE -> Material.TNT;
            case HIRED_HAND -> Material.ARMOR_STAND;
            case SOUL_REAPER -> Material.SOUL_LANTERN;
            case OVERFLOW -> Material.WATER_BUCKET;
            case CHUGGERNAUT -> Material.TURTLE_HELMET;
            case HEROS_ASSISTANCE -> Material.NETHER_STAR;
            case SNOWSTORM -> Material.SNOW_BLOCK;
            case INVASION -> Material.ZOMBIE_HEAD;
            case BLESSED -> Material.BEACON;
            case WORSHIP -> Material.END_CRYSTAL;
            case THUNDERBIRD -> Material.LIGHTNING_ROD;
            case SWARM -> Material.HONEYCOMB;
            case SUPERNOVA -> Material.NETHER_STAR;
        };
    }

    private String defaultDescription(PickaxeEnchant enchant) {
        return switch (enchant) {
            case LOTTERY -> "<light_purple>An enchantment that may surprise you with legendary prizes.";
            case SHATTER -> "<white>Has a chance to mine one vertical layer down across the mine rewarding Tokens, Gems and Rank XP.";
            case JACKPOT -> "<yellow>Has a chance to find crazy rewards in the mine!";
            case METEORITE -> "<dark_purple>Rains meteors onto your mine. Grants Tokens & Gems.";
            case FIRECRACKER -> "<red>Launches firecrackers across your mine. Grants Tokens & Gems.";
            case DRAGONS_EYE -> "<red>Virtually mines blocks broken by Jackhammer as raw blocks broken by the player.";
            case FIRECRACKS -> "<gold>Creates spreading fire explosions. Grants Tokens & Gems.";
            case PET_XP_FINDER -> "<red>Has a chance to find Pet XP.";
            case CHARITY -> "<white>Shares a small percentage of your token earnings with all online players. You receive 3 times the amount given to others as a bonus.";
            case EXCAVATOR -> "<yellow>Creates excavator drills that dig down through the mine. Higher levels create more drills, rewarding Tokens and Gems.";
            case ARCTIC_DESTROYER -> "<aqua>Drops ice blocks from the sky that create explosions when they land. Snowballs create additional explosions. Rewards Tokens and Gems based on your enchant level.";
            case DYNAMITE -> "<red>Creates a sphere pattern and rewards Tokens and Gems based on your enchant level.";
            case HIRED_HAND -> "<gold>Creates a clone of your player that mines blocks for you. The clone can trigger other enchants and helps you mine faster.";
            case SOUL_REAPER -> "<dark_purple>Summons spirits that harvest blocks. Grants Tokens & Gems.";
            case OVERFLOW -> "<aqua>Randomly activates 3 other enchants that you have when it procs. A powerful enchant that can trigger multiple effects at once.";
            case CHUGGERNAUT -> "<gold>Gives boost potions that provide multipliers to currencies. Higher enchant levels unlock more powerful potions.";
            case HEROS_ASSISTANCE -> "<yellow>Increase your hero pet ability per level.";
            case SNOWSTORM -> "<white>Snow falls onto the mine destroying blocks. Rewarding you with Tokens and Gems.";
            case INVASION -> "<blue>Summons a horde of exploding zombies, which explode upon landing giving you Tokens and Gems.";
            case BLESSED -> "<red>An enchantment that may surprise you with legendary OP prizes.";
            case WORSHIP -> "<light_purple>Summons End Crystals on your mine that gives you Tokens and Gems.";
            case THUNDERBIRD -> "<gold>Spawns a powerful plane that drops payloads on your mine. This rewards a massive amount of Tokens and Gems.";
            case SWARM -> "<light_purple>Summons a swarm of silverfish that spread through your mine tearing through blocks. This rewards you tokens and gems.";
            case SUPERNOVA -> "<yellow>Detonates a massive stellar explosion. Grants Tokens & Gems.";
            default -> "<gray>Upgrade this enchant to improve your prison mining progress.";
        };
    }

    private void saveYaml(YamlConfiguration config, File file) {
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save " + file.getName() + ".");
            exception.printStackTrace();
        }
    }

    private void createDefaultMenuFile() {
        if (menuFile.exists()) {
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("enchants-menu.title", "Nexus Token Enchants");
        config.set("gem-enchants-menu.title", "Nexus Gem Enchants");
        config.set("enchants-menu.rows", 6);
        config.set("enchants-menu.filler.enabled", true);
        config.set("enchants-menu.filler.material", "BLACK_STAINED_GLASS_PANE");
        config.set("enchants-menu.filler.name", " ");
        config.set("enchants-menu.filler.lore", List.of());
        config.set("upgrade-menu.filler.enabled", true);
        config.set("upgrade-menu.filler.material", "BLACK_STAINED_GLASS_PANE");
        config.set("upgrade-menu.filler.name", " ");
        config.set("upgrade-menu.filler.lore", List.of());
        config.set("slot-menu.title", "Nexus Pickaxe Slot");
        config.set("slot-menu.filler.enabled", true);
        config.set("slot-menu.filler.material", "BLACK_STAINED_GLASS_PANE");
        config.set("slot-menu.filler.name", " ");
        config.set("slot-menu.filler.lore", List.of());
        saveYaml(config, menuFile);
    }
}
