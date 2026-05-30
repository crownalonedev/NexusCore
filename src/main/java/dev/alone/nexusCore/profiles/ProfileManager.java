package dev.alone.nexusCore.profiles;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.managers.PickaxeEnchant;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfileManager {

    private final NexusCore plugin;
    private final File profilesFolder;
    private final Map<UUID, PlayerProfile> profiles = new HashMap<>();

    public ProfileManager(NexusCore plugin) {
        this.plugin = plugin;
        this.profilesFolder = new File(plugin.getDataFolder(), "profiles");

        if (!profilesFolder.exists()) {
            profilesFolder.mkdirs();
        }
    }

    public PlayerProfile getProfile(Player player) {
        PlayerProfile profile = profiles.get(player.getUniqueId());

        if (profile == null) {
            profile = loadProfile(player);
        }

        profile.setUsername(player.getName());
        return profile;
    }

    public PlayerProfile getProfile(UUID uuid) {
        PlayerProfile profile = profiles.get(uuid);

        if (profile != null) {
            return profile;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String username = offlinePlayer.getName() == null ? "Unknown" : offlinePlayer.getName();

        profile = loadProfile(uuid, username);
        profiles.put(uuid, profile);

        return profile;
    }

    public PlayerProfile getLoadedProfile(UUID uuid) {
        return profiles.get(uuid);
    }

    public PlayerProfile loadProfile(Player player) {
        PlayerProfile profile = loadProfile(player.getUniqueId(), player.getName());
        profiles.put(player.getUniqueId(), profile);
        return profile;
    }

    private PlayerProfile loadProfile(UUID uuid, String username) {
        File file = getProfileFile(uuid);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String savedUsername = getString(config, username, "username", "name");
        PlayerProfile profile = new PlayerProfile(uuid, savedUsername, getStartingRank());

        int maxRank = getMaxRank();
        int savedRank = getInt(config, getStartingRank(), "progression.rank", "prison.rank", "rank");
        int savedRebirth = getInt(config, 0, "progression.rebirth", "prison.rebirths", "rebirth");
        int savedAscension = getInt(config, 0, "progression.ascension", "prison.ascension", "ascension");

        profile.setRank(savedRank, maxRank);
        profile.setPrestige(getInt(config, 0, "progression.prestige", "prison.prestige", "prestige"));
        profile.setAscension(savedAscension);
        profile.setRebirth(savedRebirth);

        if ((profile.getRebirth() > 0 || profile.getAscension() > 0) && profile.getRank() < maxRank) {
            profile.setRank(maxRank, maxRank);
        }

        profile.setMoney(parseBigDecimal(getString(config, "0", "economy.money", "money")));
        profile.setTokens(parseBigInteger(getString(config, "0", "economy.tokens", "tokens")));
        profile.setGems(parseBigInteger(getString(config, "0", "economy.gems", "gems")));
        profile.setBeacons(parseBigInteger(getString(config, "0", "economy.beacons", "beacons")));

        profile.setBlocksMined(getLong(config, 0L, "mining.blocks-mined", "prison.blocks-mined", "blocks-mined"));
        profile.setRawBlocksMined(getLong(config, 0L, "mining.raw-blocks-mined", "raw-blocks-mined"));
        profile.setRankProgressBlocks(getLong(config, 0L, "mining.rank-progress-blocks", "rank-progress-blocks"));

        profile.setBackpackSize(getInt(config, 5000, "settings.backpack-size", "backpack-size"));
        profile.setAutoSell(getBoolean(config, true, "settings.auto-sell", "auto-sell"));
        profile.setAutoPickup(getBoolean(config, true, "settings.auto-pickup", "auto-pickup"));
        profile.setAutoRebirth(getBoolean(config, false, "settings.auto-rebirth", "auto-rebirth"));
        profile.setAutoAscension(getBoolean(config, false, "settings.auto-ascension", "auto-ascension"));
        profile.setHideMaxEnchants(getBoolean(config, false, "settings.hide-max-enchants", "hide-max-enchants"));

        profile.setPickaxeLevel(getInt(config, 1, "pickaxe.level"));
        profile.setPickaxeXp(getLong(config, 0L, "pickaxe.xp"));
        profile.setPickaxeSlot(getInt(config, plugin.getConfig().getInt("pickaxe.default-slot", 0), "pickaxe.slot"));
        profile.setReceivedPickaxe(getBoolean(config, false, "pickaxe.received"));

        profile.clearPickaxeEnchants();

        ConfigurationSection enchantsSection = config.getConfigurationSection("pickaxe.enchants");

        if (enchantsSection != null) {
            for (String key : enchantsSection.getKeys(false)) {
                PickaxeEnchant enchant = PickaxeEnchant.fromInput(key);

                if (enchant != null) {
                    profile.setEnchantLevel(enchant, enchantsSection.getInt(key, 0));
                }
            }
        }

        profile.setCurrentMine(getString(config, String.valueOf(profile.getRank()), "mine.current", "current-mine"));

        return profile;
    }

    public void saveProfile(PlayerProfile profile) {
        if (profile == null) {
            return;
        }

        File file = getProfileFile(profile.getUuid());
        YamlConfiguration config = new YamlConfiguration();

        config.set("username", profile.getUsername());

        config.set("progression.rank", profile.getRank());
        config.set("progression.prestige", profile.getPrestige());
        config.set("progression.ascension", profile.getAscension());
        config.set("progression.rebirth", profile.getRebirth());

        config.set("economy.money", profile.getMoney().toPlainString());
        config.set("economy.tokens", profile.getTokens().toString());
        config.set("economy.gems", profile.getGems().toString());
        config.set("economy.beacons", profile.getBeacons().toString());

        config.set("mining.blocks-mined", profile.getBlocksMined());
        config.set("mining.raw-blocks-mined", profile.getRawBlocksMined());
        config.set("mining.rank-progress-blocks", profile.getRankProgressBlocks());

        config.set("settings.backpack-size", profile.getBackpackSize());
        config.set("settings.auto-sell", profile.isAutoSell());
        config.set("settings.auto-pickup", profile.isAutoPickup());
        config.set("settings.auto-rebirth", profile.isAutoRebirth());
        config.set("settings.auto-ascension", profile.isAutoAscension());
        config.set("settings.hide-max-enchants", profile.isHideMaxEnchants());

        config.set("pickaxe.level", profile.getPickaxeLevel());
        config.set("pickaxe.xp", profile.getPickaxeXp());
        config.set("pickaxe.slot", profile.getPickaxeSlot());
        config.set("pickaxe.received", profile.hasReceivedPickaxe());

        for (Map.Entry<String, Integer> entry : profile.getPickaxeEnchants().entrySet()) {
            config.set("pickaxe.enchants." + entry.getKey(), entry.getValue());
        }

        config.set("mine.current", profile.getCurrentMine());

        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save profile for " + profile.getUsername() + ".");
            exception.printStackTrace();
        }
    }

    public void savePlayer(Player player) {
        if (player == null) {
            return;
        }

        PlayerProfile profile = profiles.get(player.getUniqueId());

        if (profile != null) {
            saveProfile(profile);
        }
    }

    public void saveAndUnloadProfile(UUID uuid) {
        PlayerProfile profile = profiles.remove(uuid);

        if (profile != null) {
            saveProfile(profile);
        }
    }

    public void saveAllProfiles() {
        for (PlayerProfile profile : profiles.values()) {
            saveProfile(profile);
        }
    }

    public int getStartingRank() {
        return getConfigInt(1,
                "progression.starting-rank",
                "ranks.starting-rank",
                "prison.starting-rank"
        );
    }

    public int getMaxRank() {
        return getConfigInt(100,
                "progression.max-rank",
                "ranks.max-rank",
                "prison.max-rank"
        );
    }

    public int getRebirthsRequiredToAscend() {
        return getConfigInt(10,
                "ascension.rebirths-required",
                "ascension.rebirths-required-to-ascend",
                "progression.rebirths-required-to-ascend"
        );
    }

    private File getProfileFile(UUID uuid) {
        return new File(profilesFolder, uuid + ".yml");
    }

    private int getConfigInt(int fallback, String... paths) {
        for (String path : paths) {
            if (plugin.getConfig().contains(path)) {
                return plugin.getConfig().getInt(path, fallback);
            }
        }

        return fallback;
    }

    private String getString(YamlConfiguration config, String fallback, String... paths) {
        for (String path : paths) {
            if (config.contains(path)) {
                return config.getString(path, fallback);
            }
        }

        return fallback;
    }

    private int getInt(YamlConfiguration config, int fallback, String... paths) {
        for (String path : paths) {
            if (config.contains(path)) {
                return config.getInt(path, fallback);
            }
        }

        return fallback;
    }

    private long getLong(YamlConfiguration config, long fallback, String... paths) {
        for (String path : paths) {
            if (config.contains(path)) {
                return config.getLong(path, fallback);
            }
        }

        return fallback;
    }

    private boolean getBoolean(YamlConfiguration config, boolean fallback, String... paths) {
        for (String path : paths) {
            if (config.contains(path)) {
                return config.getBoolean(path, fallback);
            }
        }

        return fallback;
    }

    private BigInteger parseBigInteger(String input) {
        try {
            return new BigInteger(input);
        } catch (Exception ignored) {
            return BigInteger.ZERO;
        }
    }

    private BigDecimal parseBigDecimal(String input) {
        try {
            return new BigDecimal(input);
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    public void saveProfile(UUID uuid) {
        if (uuid == null) {
            return;
        }

        PlayerProfile profile = profiles.get(uuid);

        if (profile == null) {
            profile = getProfile(uuid);
        }

        saveProfile(profile);
    }
}
