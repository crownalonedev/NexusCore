package dev.alone.nexusCore.listeners;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.managers.PickaxeEnchant;
import dev.alone.nexusCore.profiles.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class PickaxeMenuPolishListener implements Listener {

    private final NexusCore plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey enchantKey;
    private final NamespacedKey polishActionKey;
    private final NamespacedKey polishEnchantKey;
    private final Set<String> sentUnlockWarnings = new HashSet<>();

    public PickaxeMenuPolishListener(NexusCore plugin) {
        this.plugin = plugin;
        this.enchantKey = new NamespacedKey(plugin, "nexus_pickaxe_enchant");
        this.polishActionKey = new NamespacedKey(plugin, "nexus_polish_action");
        this.polishEnchantKey = new NamespacedKey(plugin, "nexus_polish_enchant");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        if (profile == null) {
            return;
        }

        int level = profile.getPickaxeLevel();

        for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
            if (enchant == PickaxeEnchant.EFFICIENCY) {
                continue;
            }

            int unlockLevel = plugin.getPickaxeManager().getUnlockLevel(enchant);
            int remaining = unlockLevel - level;

            if (remaining != 20 && remaining != 10 && remaining != 5) {
                continue;
            }

            String key = player.getUniqueId() + ":" + enchant.getId() + ":" + remaining;
            if (!sentUnlockWarnings.add(key)) {
                continue;
            }

            player.sendMessage(color(gradientName(enchant) + " <dark_gray>»</dark_gray> <gray>Unlocks in <aqua>" + remaining + "</aqua><gray> pickaxe levels!"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.7f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (!isEnchantList(title) && !title.startsWith("Upgrade ")) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isEnchantList(title)) {
                polishEnchantList(player, event.getInventory());
                return;
            }

            if (title.startsWith("Upgrade ")) {
                addUpgradeToggles(player, event.getInventory());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        String action = meta.getPersistentDataContainer().get(polishActionKey, PersistentDataType.STRING);
        String enchantId = meta.getPersistentDataContainer().get(polishEnchantKey, PersistentDataType.STRING);

        if (action == null || enchantId == null) {
            return;
        }

        PickaxeEnchant enchant = PickaxeEnchant.fromInput(enchantId);
        if (enchant == null) {
            return;
        }

        event.setCancelled(true);

        if (action.equals("toggle_enchant")) {
            boolean nowEnabled = !plugin.getPickaxeManager().isEnchantEnabled(enchant);
            setEnchantConfigValue(enchant, "enabled", nowEnabled);
            player.sendMessage(color(gradientName(enchant) + " <dark_gray>»</dark_gray> <gray>Enchant is now " + formatBoolean(nowEnabled) + "<gray>."));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
            plugin.getPickaxeManager().openUpgradeMenu(player, enchant);
            return;
        }

        if (action.equals("toggle_proc_messages")) {
            boolean nowEnabled = !plugin.getPickaxeManager().getEnchantBoolean(enchant, "proc-message.enabled", true);
            setEnchantConfigValue(enchant, "proc-message.enabled", nowEnabled);
            player.sendMessage(color(gradientName(enchant) + " <dark_gray>»</dark_gray> <gray>Proc messages are now " + formatBoolean(nowEnabled) + "<gray>."));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
            plugin.getPickaxeManager().openUpgradeMenu(player, enchant);
        }
    }

    private void polishEnchantList(Player player, Inventory inventory) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) {
            return;
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            PickaxeEnchant enchant = getEnchant(item);

            if (enchant == null) {
                continue;
            }

            int level = profile.getEnchantLevel(enchant);
            int max = plugin.getPickaxeManager().getMaxLevel(enchant);

            if (profile.isHideMaxEnchants() && level >= max) {
                inventory.setItem(slot, null);
                continue;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                continue;
            }

            boolean unlocked = profile.getPickaxeLevel() >= plugin.getPickaxeManager().getUnlockLevel(enchant);
            meta.displayName(color(gradientName(enchant) + (unlocked ? "" : " <red>- Locked")));
            meta.lore(buildCleanLore(profile, enchant, unlocked));
            item.setItemMeta(meta);
        }
    }

    private List<Component> buildCleanLore(PlayerProfile profile, PickaxeEnchant enchant, boolean unlocked) {
        List<Component> lore = new ArrayList<>();
        int level = profile.getEnchantLevel(enchant);
        int max = plugin.getPickaxeManager().getMaxLevel(enchant);

        lore.add(color(unlocked ? "<dark_gray>Unlocked Enchant" : "<dark_gray>Locked Enchant"));
        lore.add(color(""));
        lore.add(color(plugin.getPickaxeManager().getEnchantString(enchant, "description", "<gray>Upgrade this enchant.").replace("</yellow>", "</yellow>").replace("<yellow/>", "")));
        lore.add(color(""));
        lore.add(color("<gradient:#FFFFFF:#BFBFBF><bold>Information</bold></gradient>"));
        lore.add(color(" <dark_gray>┃ <gray>Level: <aqua>" + level + "</aqua><dark_gray>/</dark_gray><aqua>" + max));
        lore.add(color(" <dark_gray>┃ <gray>Unlock Level: <aqua>" + plugin.getPickaxeManager().getUnlockLevel(enchant)));
        lore.add(color(" <dark_gray>┃ <gray>Currency: " + (plugin.getPickaxeManager().isGemEnchant(enchant) ? "<light_purple>Gems" : "<yellow>Tokens")));
        lore.add(color(""));

        if (unlocked) {
            lore.add(color(level >= max ? "<green><bold>MAXED</bold>" : "<yellow>Click to upgrade."));
        } else {
            lore.add(color("<gray>Unlock at Pickaxe Level <aqua>" + plugin.getPickaxeManager().getUnlockLevel(enchant)));
        }

        return lore;
    }

    private void addUpgradeToggles(Player player, Inventory inventory) {
        PickaxeEnchant enchant = null;
        for (ItemStack item : inventory.getContents()) {
            enchant = getEnchant(item);
            if (enchant != null) {
                break;
            }
        }

        if (enchant == null) {
            return;
        }

        inventory.setItem(36, createToggleItem(
                enchant,
                "toggle_enchant",
                plugin.getPickaxeManager().isEnchantEnabled(enchant),
                Material.REDSTONE_TORCH,
                "<gradient:#00CFFF:#0066FF><bold>Toggle Enchant</bold></gradient>",
                "<gray>Completely enable or disable this enchant."
        ));

        inventory.setItem(44, createToggleItem(
                enchant,
                "toggle_proc_messages",
                plugin.getPickaxeManager().getEnchantBoolean(enchant, "proc-message.enabled", true),
                Material.OAK_SIGN,
                "<gradient:#FCA800:#FCFC54><bold>Toggle Proc Messages</bold></gradient>",
                "<gray>Enable or disable chat proc messages for this enchant."
        ));
    }

    private ItemStack createToggleItem(PickaxeEnchant enchant, String action, boolean enabled, Material material, String name, String description) {
        ItemStack item = new ItemStack(enabled ? material : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.displayName(color(name));
        meta.lore(List.of(
                color(""),
                color(description),
                color(""),
                color("<gray>Status: " + formatBoolean(enabled)),
                color(""),
                color("<yellow>Click to toggle.")
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(polishActionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(polishEnchantKey, PersistentDataType.STRING, enchant.getId());
        item.setItemMeta(meta);
        return item;
    }

    private PickaxeEnchant getEnchant(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        String id = meta == null ? null : meta.getPersistentDataContainer().get(enchantKey, PersistentDataType.STRING);
        return PickaxeEnchant.fromInput(id);
    }

    private void setEnchantConfigValue(PickaxeEnchant enchant, String path, Object value) {
        YamlConfiguration config = plugin.getPickaxeManager().getEnchantConfig(enchant);
        config.set("enchants." + enchant.getId() + "." + path, value);

        File file = new File(plugin.getDataFolder(), plugin.getPickaxeManager().isGemEnchant(enchant) ? "enchants/gem-enchants.yml" : "enchants/token-enchants.yml");
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save enchant toggle for " + enchant.getId() + ": " + exception.getMessage());
        }
    }

    private boolean isEnchantList(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        return lower.contains("token enchants") || lower.contains("gem enchants") || lower.contains("pickaxe menu");
    }

    private String gradientName(PickaxeEnchant enchant) {
        return switch (enchant) {
            case EFFICIENCY -> "<gradient:#FFFFFF:#A7F3FF><bold>Efficiency</bold></gradient>";
            case HASTE -> "<gradient:#FFD86B:#FF7B00><bold>Haste</bold></gradient>";
            case SPEED -> "<gradient:#FF4FD8:#7C3AED><bold>Speed</bold></gradient>";
            case TOKEN_FINDER -> "<gradient:#FFD700:#FFF89A><bold>Token Miner</bold></gradient>";
            case JACKHAMMER -> "<gradient:#FF7B00:#FFD166><bold>Jackhammer</bold></gradient>";
            case MINE_STRIKE -> "<gradient:#FF3B3B:#FF9E9E><bold>Mine Strike</bold></gradient>";
            case GEM_FINDER -> "<gradient:#22FF88:#00CFFF><bold>Gem Finder</bold></gradient>";
            case BEACON_FINDER -> "<gradient:#00CFFF:#7DF9FF><bold>Beacon Finder</bold></gradient>";
            case VEIN_MINER -> "<gradient:#C084FC:#F0ABFC><bold>Vein Miner</bold></gradient>";
            case KEY_FINDER -> "<gradient:#FF9FF3:#F368E0><bold>Key Finder</bold></gradient>";
            case TOKEN_EXPLOSION -> "<gradient:#FF4D4D:#FFB703><bold>Token Explosion</bold></gradient>";
            case NUKE -> "<gradient:#FF0000:#7F1D1D><bold>Nuke</bold></gradient>";
            case METEOR -> "<gradient:#FF6B00:#FFD166><bold>Meteor</bold></gradient>";
            case TOKEN_MERCHANT -> "<gradient:#FDE047:#F59E0B><bold>Token Merchant</bold></gradient>";
            case SECOND_HAND -> "<gradient:#CBD5E1:#64748B><bold>Second Hand</bold></gradient>";
            case HOLY_ARROWS -> "<gradient:#F8FAFC:#FACC15><bold>Holy Arrows</bold></gradient>";
            case KEY_MERCHANT -> "<gradient:#C2410C:#FDBA74><bold>Key Merchant</bold></gradient>";
            case METEOR_STRIKE -> "<gradient:#FF4500:#FFD700><bold>Meteor Strike</bold></gradient>";
            case GEM_MERCHANT -> "<gradient:#22C55E:#86EFAC><bold>Gem Merchant</bold></gradient>";
            case SHOCKWAVE -> "<gradient:#00E5FF:#0EA5E9><bold>Shockwave</bold></gradient>";
            case CLUSTER_BOMB -> "<gradient:#84CC16:#22C55E><bold>Cluster Bomb</bold></gradient>";
            case TOKEN_GREED -> "<gradient:#FACC15:#CA8A04><bold>Token Greed</bold></gradient>";
            case WILD_BLAZE -> "<gradient:#FF6A00:#FF0000><bold>Wild Blaze</bold></gradient>";
            case DOUBLE_STRIKE -> "<gradient:#EF4444:#F97316><bold>Double Strike</bold></gradient>";
            case GANG_POINT_FINDER -> "<gradient:#22C55E:#16A34A><bold>Gang Point Finder</bold></gradient>";
            case SNOW_ARMY -> "<gradient:#E0F2FE:#FFFFFF><bold>Snow Army</bold></gradient>";
            case DRAGONS_WRATH -> "<gradient:#A855F7:#7E22CE><bold>Dragon's Wrath</bold></gradient>";
            case SCAVENGER -> "<gradient:#94A3B8:#475569><bold>Scavenger</bold></gradient>";
            case ENDERMAN_ABOMINATION -> "<gradient:#7C3AED:#111827><bold>Enderman Abomination</bold></gradient>";
            case VOLCANO -> "<gradient:#FF2400:#FFB000><bold>Volcano</bold></gradient>";
            case HYDROGEN_BOMB -> "<gradient:#DC2626:#FCA5A5><bold>Hydrogen Bomb</bold></gradient>";
            case PROPHET -> "<gradient:#FB923C:#FED7AA><bold>Prophet</bold></gradient>";
            case BLACK_HOLE -> "<gradient:#111827:#A855F7><bold>Black Hole</bold></gradient>";
            case LOTTERY -> "<gradient:#D946EF:#F0ABFC><bold>Lottery</bold></gradient>";
            case SHATTER -> "<gradient:#F8FAFC:#94A3B8><bold>Shatter</bold></gradient>";
            case JACKPOT -> "<gradient:#FDE047:#EAB308><bold>Jackpot</bold></gradient>";
            case METEORITE -> "<gradient:#64748B:#38BDF8><bold>Meteorite</bold></gradient>";
            case FIRECRACKER -> "<gradient:#EF4444:#F97316><bold>Firecracker</bold></gradient>";
            case DRAGONS_EYE -> "<gradient:#EA580C:#16A34A><bold>Dragons Eye</bold></gradient>";
            case FIRECRACKS -> "<gradient:#FB923C:#EF4444><bold>Firecracks</bold></gradient>";
            case PET_XP_FINDER -> "<gradient:#F43F5E:#FB7185><bold>Pet XP Finder</bold></gradient>";
            case CHARITY -> "<gradient:#FFFFFF:#22C55E><bold>Charity</bold></gradient>";
            case EXCAVATOR -> "<gradient:#FDE68A:#92400E><bold>Excavator</bold></gradient>";
            case ARCTIC_DESTROYER -> "<gradient:#BAE6FD:#38BDF8><bold>Arctic Destroyer</bold></gradient>";
            case DYNAMITE -> "<gradient:#DC2626:#991B1B><bold>Dynamite</bold></gradient>";
            case HIRED_HAND -> "<gradient:#B45309:#FCD34D><bold>Hired Hand</bold></gradient>";
            case SOUL_REAPER -> "<gradient:#6D28D9:#312E81><bold>Soul Reaper</bold></gradient>";
            case OVERFLOW -> "<gradient:#22D3EE:#0891B2><bold>Overflow</bold></gradient>";
            case CHUGGERNAUT -> "<gradient:#854D0E:#FACC15><bold>Chuggernaut</bold></gradient>";
            case HEROS_ASSISTANCE -> "<gradient:#FEF08A:#F59E0B><bold>Heros Assistance</bold></gradient>";
            case SNOWSTORM -> "<gradient:#E0F2FE:#7DD3FC><bold>Snowstorm</bold></gradient>";
            case INVASION -> "<gradient:#6366F1:#312E81><bold>Invasion</bold></gradient>";
            case BLESSED -> "<gradient:#EF4444:#FCA5A5><bold>Blessed</bold></gradient>";
            case WORSHIP -> "<gradient:#D946EF:#FAE8FF><bold>Worship</bold></gradient>";
            case THUNDERBIRD -> "<gradient:#A16207:#FDBA74><bold>Thunderbird</bold></gradient>";
            case SWARM -> "<gradient:#A855F7:#D8B4FE><bold>Swarm</bold></gradient>";
            case SUPERNOVA -> "<gradient:#FDE047:#FB7185><bold>Supernova</bold></gradient>";
        };
    }

    private String formatBoolean(boolean value) {
        return value ? "<green>Enabled" : "<red>Disabled";
    }

    private Component color(String message) {
        if (message == null || message.isEmpty()) {
            message = " ";
        }
        return miniMessage.deserialize(message).decoration(TextDecoration.ITALIC, false);
    }
}
