package dev.alone.nexusCore.managers;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.utils.MineItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class MineManager {

    private final NexusCore plugin;
    private final File dataFile;
    private final Random random;

    private YamlConfiguration data;
    private final Map<UUID, PrivateMine> mines;
    private final Map<UUID, Integer> runningResetTasks;

    private int upgradeWatcherTaskId = -1;

    public MineManager(NexusCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "mines/private-mines.yml");
        this.random = new Random();
        this.mines = new HashMap<>();
        this.runningResetTasks = new HashMap<>();
    }

    public void load() {
        if (!dataFile.getParentFile().exists()) {
            dataFile.getParentFile().mkdirs();
        }

        this.data = YamlConfiguration.loadConfiguration(dataFile);

        loadMineWorld();
        loadMines();
        startUpgradeWatcher();
    }

    private void loadMineWorld() {
        String worldName = getMineWorldName();

        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            WorldCreator creator = new WorldCreator(worldName);
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            world = creator.createWorld();
        }

        if (world == null) {
            plugin.getLogger().warning("Failed to load private mine world: " + worldName);
            return;
        }

        world.setDifficulty(Difficulty.PEACEFUL);
        world.setPVP(false);

        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_TILE_DROPS, false);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
    }

    private void loadMines() {
        mines.clear();

        ConfigurationSection section = data.getConfigurationSection("mines");

        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID ownerUuid = UUID.fromString(key);
                ConfigurationSection mineSection = section.getConfigurationSection(key);

                if (mineSection == null) {
                    continue;
                }

                Map<Material, Integer> palette = new LinkedHashMap<>();
                ConfigurationSection paletteSection = mineSection.getConfigurationSection("palette");

                if (paletteSection != null) {
                    for (String materialName : paletteSection.getKeys(false)) {
                        Material material = Material.matchMaterial(materialName);

                        if (material == null || !material.isBlock() || material.isAir()) {
                            continue;
                        }

                        int weight = mineSection.getInt("palette." + materialName);

                        if (weight > 0) {
                            palette.put(material, weight);
                        }
                    }
                }

                if (palette.isEmpty()) {
                    palette = getDefaultPalette();
                }

                int minY = mineSection.getInt("min.y", getConfiguredMineBaseY());

                int centerX;
                int centerZ;

                if (mineSection.contains("center.x") && mineSection.contains("center.z")) {
                    centerX = mineSection.getInt("center.x");
                    centerZ = mineSection.getInt("center.z");
                } else {
                    centerX = (int) Math.floor(mineSection.getDouble("spawn.x", 0.5));
                    centerZ = (int) Math.floor(mineSection.getDouble("spawn.z", 0.5));
                }

                int radius;

                if (mineSection.contains("radius")) {
                    radius = mineSection.getInt("radius");
                } else {
                    int minX = mineSection.getInt("min.x");
                    int maxX = mineSection.getInt("max.x");
                    radius = Math.max(1, (maxX - minX) / 2);
                }

                int height;

                if (mineSection.contains("height")) {
                    height = mineSection.getInt("height");
                } else {
                    int loadedMinY = mineSection.getInt("min.y");
                    int loadedMaxY = mineSection.getInt("max.y");
                    height = Math.max(1, loadedMaxY - loadedMinY + 1);
                }

                boolean customPalette = mineSection.getBoolean("custom-palette", false);
                int paletteTier = mineSection.getInt("palette-tier", 0);

                PrivateMine mine = new PrivateMine(
                        ownerUuid,
                        mineSection.getString("owner-name", "Unknown"),
                        mineSection.getString("world", getMineWorldName()),
                        centerX,
                        centerZ,
                        minY,
                        radius,
                        height,
                        palette,
                        customPalette,
                        paletteTier
                );

                mines.put(ownerUuid, mine);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void saveAll() {
        if (data == null) {
            data = new YamlConfiguration();
        }

        data.set("mines", null);

        for (PrivateMine mine : mines.values()) {
            String path = "mines." + mine.getOwnerUuid();

            data.set(path + ".owner-name", mine.getOwnerName());
            data.set(path + ".world", mine.getWorldName());

            data.set(path + ".center.x", mine.getCenterX());
            data.set(path + ".center.z", mine.getCenterZ());

            data.set(path + ".radius", mine.getRadius());
            data.set(path + ".height", mine.getHeight());

            data.set(path + ".custom-palette", mine.isCustomPalette());
            data.set(path + ".palette-tier", mine.getPaletteTier());

            data.set(path + ".min.x", mine.getMinX());
            data.set(path + ".min.y", mine.getMinY());
            data.set(path + ".min.z", mine.getMinZ());

            data.set(path + ".max.x", mine.getMaxX());
            data.set(path + ".max.y", mine.getMaxY());
            data.set(path + ".max.z", mine.getMaxZ());

            data.set(path + ".spawn.x", mine.getSpawnX());
            data.set(path + ".spawn.y", mine.getSpawnY());
            data.set(path + ".spawn.z", mine.getSpawnZ());

            data.set(path + ".palette", null);

            for (Map.Entry<Material, Integer> entry : mine.getPalette().entrySet()) {
                data.set(path + ".palette." + entry.getKey().name(), entry.getValue());
            }
        }

        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save private mines.yml:");
            exception.printStackTrace();
        }
    }

    public PrivateMine getOrCreateMine(Player player) {
        PrivateMine mine = mines.get(player.getUniqueId());

        if (mine != null) {
            mine.setOwnerName(player.getName());
            syncMineBaseYIfNeeded(player, mine, true);
            resizeMineIfNeeded(player, mine, true);
            return mine;
        }

        mine = createMine(player);
        mines.put(player.getUniqueId(), mine);

        prepareMineArea(mine);
        resetMine(mine);
        saveAll();

        return mine;
    }

    private PrivateMine createMine(Player player) {
        int id = data.getInt("next-id", 0);
        data.set("next-id", id + 1);

        int baseY = getConfiguredMineBaseY();
        int spacing = plugin.getConfig().getInt("private-mines.spacing", 1500);
        int minesPerRow = plugin.getConfig().getInt("private-mines.mines-per-row", 50);

        int originX = (id % minesPerRow) * spacing;
        int originZ = (id / minesPerRow) * spacing;

        MineSize mineSize = getMineSize(player);

        return new PrivateMine(
                player.getUniqueId(),
                player.getName(),
                getMineWorldName(),
                originX,
                originZ,
                baseY,
                mineSize.radius(),
                mineSize.height(),
                getRankTierPalette(mineSize.tier()),
                false,
                mineSize.tier()
        );
    }

    private void resizeMineIfNeeded(Player player, PrivateMine mine, boolean resetAfterResize) {
        if (!plugin.getConfig().getBoolean("private-mines.size-scaling.enabled", true)) {
            return;
        }

        MineSize earnedSize = getMineSize(player);

        int oldWidth = mine.getWidth();
        int oldHeight = mine.getHeight();

        int newRadius = Math.max(mine.getRadius(), earnedSize.radius());
        int newHeight = Math.max(mine.getHeight(), earnedSize.height());

        boolean expanded = mine.getRadius() != newRadius || mine.getHeight() != newHeight;
        boolean paletteTierChanged = !mine.isCustomPalette() && earnedSize.tier() > mine.getPaletteTier();

        if (!expanded && !paletteTierChanged) {
            return;
        }

        cancelResetTask(mine.getOwnerUuid());

        if (expanded) {
            clearMineArea(mine);
            mine.resize(newRadius, newHeight);
        }

        if (paletteTierChanged) {
            mine.setPalette(getRankTierPalette(earnedSize.tier()));
            mine.setPaletteTier(earnedSize.tier());
        }

        prepareMineArea(mine);

        if (resetAfterResize) {
            resetMine(mine);
        }

        saveAll();

        if (expanded) {
            player.sendMessage(MineItemUtil.prefixed(
                    "<green>Your private mine expanded from <aqua>" +
                            oldWidth + "x" + oldHeight + "x" + oldWidth +
                            "</aqua><green> to <aqua>" +
                            mine.getWidth() + "x" + mine.getHeight() + "x" + mine.getWidth() +
                            "</aqua><green>!"
            ));
        }

        if (paletteTierChanged) {
            player.sendMessage(MineItemUtil.prefixed(
                    "<green>Your mine blocks changed for tier <aqua>" +
                            earnedSize.tier() +
                            "</aqua><green> and your mine was reset."
            ));
        }

        if (resetAfterResize) {
            teleportToMine(player, mine);
        }
    }

    private boolean syncMineBaseYIfNeeded(Player player, PrivateMine mine, boolean resetAfterMove) {
        int configuredBaseY = getConfiguredMineBaseY();

        if (mine.getMinY() == configuredBaseY) {
            return false;
        }

        int oldBlockStartY = mine.getMinY() + getEmptyBottomLayers();
        int newBlockStartY = configuredBaseY + getEmptyBottomLayers();

        cancelResetTask(mine.getOwnerUuid());
        clearMineArea(mine);
        mine.setMinY(configuredBaseY);
        prepareMineArea(mine);

        if (resetAfterMove) {
            resetMine(mine);
        }

        saveAll();

        if (player != null) {
            player.sendMessage(MineItemUtil.prefixed(
                    "<green>Your private mine block layer moved from <aqua>Y " +
                            oldBlockStartY +
                            "</aqua><green> to <aqua>Y " +
                            newBlockStartY +
                            "</aqua><green>."
            ));

            if (resetAfterMove) {
                teleportToMine(player, mine);
            }
        }

        return true;
    }

    private int getConfiguredMineBaseY() {
        int emptyBottomLayers = getEmptyBottomLayers();

        if (plugin.getConfig().contains("private-mines.mine-block-start-y")) {
            int blockStartY = plugin.getConfig().getInt("private-mines.mine-block-start-y", 116);
            return Math.max(1, blockStartY - emptyBottomLayers);
        }

        return plugin.getConfig().getInt("private-mines.base-y", 114);
    }

    private void startUpgradeWatcher() {
        if (upgradeWatcherTaskId != -1) {
            Bukkit.getScheduler().cancelTask(upgradeWatcherTaskId);
            upgradeWatcherTaskId = -1;
        }

        int seconds = Math.max(1, plugin.getConfig().getInt("private-mines.upgrade-check-interval-seconds", 2));

        upgradeWatcherTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PrivateMine mine = mines.get(player.getUniqueId());

                if (mine == null) {
                    continue;
                }

                resizeMineIfNeeded(player, mine, true);
            }
        }, 20L * seconds, 20L * seconds).getTaskId();
    }

    private void prepareMineArea(PrivateMine mine) {
        World world = Bukkit.getWorld(mine.getWorldName());

        if (world == null) {
            return;
        }

        int wallMinX = mine.getMinX() - 1;
        int wallMaxX = mine.getMaxX() + 1;

        int wallMinZ = mine.getMinZ() - 1;
        int wallMaxZ = mine.getMaxZ() + 1;

        int floorY = mine.getMinY() - 1;
        int topY = mine.getMaxY() + 5;

        for (int x = wallMinX; x <= wallMaxX; x++) {
            for (int z = wallMinZ; z <= wallMaxZ; z++) {
                world.getBlockAt(x, floorY, z).setType(Material.BEDROCK, false);
            }
        }

        for (int y = mine.getMinY(); y <= topY; y++) {
            for (int x = wallMinX; x <= wallMaxX; x++) {
                world.getBlockAt(x, y, wallMinZ).setType(Material.BEDROCK, false);
                world.getBlockAt(x, y, wallMaxZ).setType(Material.BEDROCK, false);
            }

            for (int z = wallMinZ; z <= wallMaxZ; z++) {
                world.getBlockAt(wallMinX, y, z).setType(Material.BEDROCK, false);
                world.getBlockAt(wallMaxX, y, z).setType(Material.BEDROCK, false);
            }
        }

        clearNonMineableAir(mine);

        Location spawn = mine.getSpawnLocation();

        if (spawn != null) {
            spawn.getChunk().load();
        }
    }

    private void clearMineArea(PrivateMine mine) {
        World world = Bukkit.getWorld(mine.getWorldName());

        if (world == null) {
            return;
        }

        int wallMinX = mine.getMinX() - 1;
        int wallMaxX = mine.getMaxX() + 1;

        int wallMinZ = mine.getMinZ() - 1;
        int wallMaxZ = mine.getMaxZ() + 1;

        int floorY = mine.getMinY() - 1;
        int topY = mine.getMaxY() + 5;

        for (int x = wallMinX; x <= wallMaxX; x++) {
            for (int y = floorY; y <= topY; y++) {
                for (int z = wallMinZ; z <= wallMaxZ; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    public void resetMine(PrivateMine mine) {
        World world = Bukkit.getWorld(mine.getWorldName());

        if (world == null) {
            return;
        }

        prepareMineArea(mine);
        cancelResetTask(mine.getOwnerUuid());

        Map<Material, Integer> resetPalette = getPaletteForReset(mine);

        for (int x = mine.getMinX(); x <= mine.getMaxX(); x++) {
            for (int y = mine.getMinY(); y <= mine.getMaxY(); y++) {
                for (int z = mine.getMinZ(); z <= mine.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);

                    if (shouldBeMineBlock(mine, x, y, z)) {
                        block.setType(getRandomBlock(resetPalette), false);
                    } else {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }

        clearNonMineableAir(mine);
    }

    private Map<Material, Integer> getPaletteForReset(PrivateMine mine) {
        Map<Material, Integer> palette = new LinkedHashMap<>();

        for (Map.Entry<Material, Integer> entry : mine.getPalette().entrySet()) {
            Material material = entry.getKey();

            if (material == null || !material.isBlock() || material.isAir()) {
                continue;
            }

            if (!isPickaxeFriendlyPaletteMaterial(material)) {
                continue;
            }

            int weight = Math.max(1, entry.getValue());
            palette.put(material, weight);
        }

        if (palette.isEmpty()) {
            return getDefaultPalette();
        }

        return palette;
    }

    private boolean shouldBeMineBlock(PrivateMine mine, int x, int y, int z) {
        int emptyBottomLayers = getEmptyBottomLayers();
        int emptySideLayers = getEmptySideLayers();

        if (y < mine.getMinY() + emptyBottomLayers) {
            return false;
        }

        if (x < mine.getMinX() + emptySideLayers) {
            return false;
        }

        if (x > mine.getMaxX() - emptySideLayers) {
            return false;
        }

        if (z < mine.getMinZ() + emptySideLayers) {
            return false;
        }

        if (z > mine.getMaxZ() - emptySideLayers) {
            return false;
        }

        return true;
    }

    private void clearNonMineableAir(PrivateMine mine) {
        World world = Bukkit.getWorld(mine.getWorldName());

        if (world == null) {
            return;
        }

        for (int x = mine.getMinX(); x <= mine.getMaxX(); x++) {
            for (int y = mine.getMinY(); y <= mine.getMaxY(); y++) {
                for (int z = mine.getMinZ(); z <= mine.getMaxZ(); z++) {
                    if (!shouldBeMineBlock(mine, x, y, z)) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }

        for (int y = mine.getMaxY() + 1; y <= mine.getMaxY() + 5; y++) {
            for (int x = mine.getMinX(); x <= mine.getMaxX(); x++) {
                for (int z = mine.getMinZ(); z <= mine.getMaxZ(); z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private int getEmptySideLayers() {
        return Math.max(0, plugin.getConfig().getInt("private-mines.empty-side-layers", 2));
    }

    private int getEmptyBottomLayers() {
        return Math.max(0, plugin.getConfig().getInt("private-mines.empty-bottom-layers", 2));
    }

    private void cancelResetTask(UUID ownerUuid) {
        Integer taskId = runningResetTasks.remove(ownerUuid);

        if (taskId == null) {
            return;
        }

        Bukkit.getScheduler().cancelTask(taskId);
    }

    public void teleportToMine(Player player, PrivateMine mine) {
        Location location = mine.getSpawnLocation();

        if (location == null) {
            player.sendMessage(MineItemUtil.prefixed("<red>That mine world is not loaded."));
            playConfiguredSound(player, "error");
            return;
        }

        player.setFallDistance(0.0f);
        player.teleport(location);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        playConfiguredSound(player, "teleport");
    }

    public boolean canBreakBlock(Player player, Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (!isMineWorld(location.getWorld())) {
            return true;
        }

        if (player.hasPermission("nexuscore.mine.admin")) {
            return true;
        }

        Optional<PrivateMine> optionalMine = getMineAt(location);

        if (optionalMine.isEmpty()) {
            return false;
        }

        PrivateMine mine = optionalMine.get();
        return mine.isOwner(player.getUniqueId());
    }

    public Optional<PrivateMine> getMineAt(Location location) {
        for (PrivateMine mine : mines.values()) {
            if (mine.contains(location)) {
                return Optional.of(mine);
            }
        }

        return Optional.empty();
    }

    public PrivateMine getMine(UUID uuid) {
        return mines.get(uuid);
    }

    public Map<UUID, PrivateMine> getMines() {
        return mines;
    }

    public boolean isMineWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(getMineWorldName());
    }

    public void setPalette(PrivateMine mine, Map<Material, Integer> palette, boolean resetAfter) {
        mine.setPalette(palette);
        mine.setCustomPalette(true);

        if (resetAfter) {
            resetMine(mine);
        }

        saveAll();
    }

    public String getMineWorldName() {
        return plugin.getConfig().getString("private-mines.world", "nexus_private_mines");
    }

    public int getPlayerRankLevel(Player player) {
        int rank = getRankFromProfile(player);

        if (rank <= 0) {
            rank = 1;
        }

        int rankCap = plugin.getConfig().getInt("private-mines.size-scaling.rank-cap", 1000);

        return Math.min(rank, rankCap);
    }

    public MineSize getMineSize(Player player) {
        int rank = getPlayerRankLevel(player);

        int levelsPerUpgrade = plugin.getConfig().getInt("private-mines.size-scaling.levels-per-upgrade", 25);
        int rankCap = plugin.getConfig().getInt("private-mines.size-scaling.rank-cap", 1000);

        levelsPerUpgrade = Math.max(1, levelsPerUpgrade);
        rankCap = Math.max(levelsPerUpgrade, rankCap);

        int baseRadius = plugin.getConfig().getInt(
                "private-mines.size-scaling.base-radius",
                plugin.getConfig().getInt("private-mines.radius", 12)
        );

        int baseHeight = plugin.getConfig().getInt(
                "private-mines.size-scaling.base-height",
                plugin.getConfig().getInt("private-mines.height", 30)
        );

        int maxRadius = plugin.getConfig().getInt("private-mines.size-scaling.max-radius", 55);
        int maxHeight = plugin.getConfig().getInt("private-mines.size-scaling.max-height", 75);

        int maxTier = Math.max(1, rankCap / levelsPerUpgrade);
        int tier = Math.min(maxTier, rank / levelsPerUpgrade);

        double progress = tier / (double) maxTier;

        int radius = baseRadius + (int) Math.round((maxRadius - baseRadius) * progress);
        int height = baseHeight + (int) Math.round((maxHeight - baseHeight) * progress);

        return new MineSize(radius, height, tier, maxTier);
    }

    private int getRankFromProfile(Player player) {
        Object profileManager = plugin.getProfileManager();

        if (profileManager == null) {
            return 1;
        }

        Object profile = findProfileObject(profileManager, player);

        if (profile == null) {
            return 1;
        }

        String[] rankMethodNames = {
                "getRank",
                "getRankLevel",
                "getCurrentRank",
                "getLevel",
                "getMineRank",
                "getPrisonRank"
        };

        Integer methodRank = findIntByMethods(profile, rankMethodNames);

        if (methodRank != null && methodRank > 0) {
            return methodRank;
        }

        String[] rankFieldNames = {
                "rank",
                "rankLevel",
                "currentRank",
                "level",
                "mineRank",
                "prisonRank"
        };

        Integer fieldRank = findIntByFields(profile, rankFieldNames);

        if (fieldRank != null && fieldRank > 0) {
            return fieldRank;
        }

        return 1;
    }

    private Object findProfileObject(Object profileManager, Player player) {
        String[] profileMethodNames = {
                "getProfile",
                "getPlayerProfile",
                "loadProfile",
                "getOrCreateProfile"
        };

        for (Method method : profileManager.getClass().getMethods()) {
            boolean validName = false;

            for (String name : profileMethodNames) {
                if (method.getName().equalsIgnoreCase(name)) {
                    validName = true;
                    break;
                }
            }

            if (!validName || method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];

            try {
                if (parameterType == UUID.class) {
                    return method.invoke(profileManager, player.getUniqueId());
                }

                if (parameterType == Player.class) {
                    return method.invoke(profileManager, player);
                }

                if (parameterType == String.class) {
                    return method.invoke(profileManager, player.getName());
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private Integer findIntByMethods(Object object, String[] methodNames) {
        for (String methodName : methodNames) {
            for (Method method : object.getClass().getMethods()) {
                if (!method.getName().equalsIgnoreCase(methodName) || method.getParameterCount() != 0) {
                    continue;
                }

                try {
                    Object value = method.invoke(object);
                    Integer parsed = parseInteger(value);

                    if (parsed != null) {
                        return parsed;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return null;
    }

    private Integer findIntByFields(Object object, String[] fieldNames) {
        for (String fieldName : fieldNames) {
            Class<?> current = object.getClass();

            while (current != null) {
                try {
                    Field field = current.getDeclaredField(fieldName);
                    field.setAccessible(true);

                    Object value = field.get(object);
                    Integer parsed = parseInteger(value);

                    if (parsed != null) {
                        return parsed;
                    }
                } catch (Exception ignored) {
                }

                current = current.getSuperclass();
            }
        }

        return null;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        String text = String.valueOf(value).trim();

        if (text.isEmpty()) {
            return null;
        }

        StringBuilder digits = new StringBuilder();
        boolean started = false;

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);

            if (Character.isDigit(character)) {
                digits.append(character);
                started = true;
                continue;
            }

            if (started) {
                break;
            }
        }

        if (digits.isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Material getRandomBlock(Map<Material, Integer> palette) {
        int total = 0;

        for (int weight : palette.values()) {
            total += weight;
        }

        if (total <= 0) {
            return Material.STONE;
        }

        int roll = random.nextInt(total) + 1;
        int current = 0;

        for (Map.Entry<Material, Integer> entry : palette.entrySet()) {
            current += entry.getValue();

            if (roll <= current) {
                return entry.getKey();
            }
        }

        return Material.STONE;
    }

    private Map<Material, Integer> getRankTierPalette(int tier) {
        if (tier <= 0) {
            return getDefaultPalette();
        }

        List<Material> blocks = getAutoPaletteBlocks();

        if (blocks.isEmpty()) {
            return getDefaultPalette();
        }

        Collections.shuffle(blocks, new Random(918273645L + ((long) tier * 7291L)));

        int blocksPerTier = plugin.getConfig().getInt("private-mines.auto-palettes.blocks-per-tier", 4);
        blocksPerTier = Math.max(1, Math.min(4, blocksPerTier));

        Map<Material, Integer> palette = new LinkedHashMap<>();

        for (Material material : blocks) {
            if (material == null || !material.isBlock() || material.isAir()) {
                continue;
            }

            if (!isPickaxeFriendlyPaletteMaterial(material)) {
                continue;
            }

            palette.put(material, 100);

            if (palette.size() >= blocksPerTier) {
                break;
            }
        }

        if (palette.isEmpty()) {
            return getDefaultPalette();
        }

        return palette;
    }

    private boolean isPickaxeFriendlyPaletteMaterial(Material material) {
        String name = material.name();

        if (name.contains("NYLIUM")
                || name.contains("WART_BLOCK")
                || name.endsWith("_PLANKS")
                || name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.equals("BAMBOO_BLOCK")
                || name.equals("HONEYCOMB_BLOCK")
                || name.equals("MOSS_BLOCK")
                || name.equals("SHROOMLIGHT")) {
            return false;
        }

        if (name.endsWith("_ORE")
                || name.endsWith("_CONCRETE")
                || name.endsWith("_TERRACOTTA")
                || name.endsWith("_GLAZED_TERRACOTTA")
                || name.endsWith("_BLOCK")) {
            return true;
        }

        return switch (material) {
            case STONE,
                 COBBLESTONE,
                 ANDESITE,
                 DIORITE,
                 GRANITE,
                 CALCITE,
                 TUFF,
                 POLISHED_TUFF,
                 TUFF_BRICKS,
                 DEEPSLATE,
                 COBBLED_DEEPSLATE,
                 POLISHED_DEEPSLATE,
                 DEEPSLATE_TILES,
                 DEEPSLATE_BRICKS,
                 BLACKSTONE,
                 POLISHED_BLACKSTONE,
                 POLISHED_BLACKSTONE_BRICKS,
                 GILDED_BLACKSTONE,
                 END_STONE,
                 END_STONE_BRICKS,
                 PRISMARINE,
                 PRISMARINE_BRICKS,
                 DARK_PRISMARINE,
                 SEA_LANTERN,
                 SMOOTH_QUARTZ,
                 QUARTZ_BLOCK,
                 PURPUR_BLOCK,
                 PACKED_ICE,
                 BLUE_ICE -> true;
            default -> false;
        };
    }

    private List<Material> getAutoPaletteBlocks() {
        List<Material> blocks = new ArrayList<>();

        for (String materialName : plugin.getConfig().getStringList("private-mines.auto-palettes.blocks")) {
            Material material = Material.matchMaterial(materialName);

            if (material == null || !material.isBlock() || material.isAir()) {
                continue;
            }

            if (!isPickaxeFriendlyPaletteMaterial(material)) {
                continue;
            }

            blocks.add(material);
        }

        if (!blocks.isEmpty()) {
            return blocks;
        }

        return new ArrayList<>(List.of(
                Material.CALCITE,
                Material.SMOOTH_QUARTZ,
                Material.QUARTZ_BLOCK,
                Material.SEA_LANTERN,
                Material.PRISMARINE,
                Material.DARK_PRISMARINE,
                Material.AMETHYST_BLOCK,
                Material.PURPUR_BLOCK,
                Material.BLUE_ICE,
                Material.PACKED_ICE,
                Material.COPPER_BLOCK,
                Material.CUT_COPPER,
                Material.EXPOSED_COPPER,
                Material.WEATHERED_COPPER,
                Material.OXIDIZED_COPPER,
                Material.RAW_COPPER_BLOCK,
                Material.RAW_IRON_BLOCK,
                Material.RAW_GOLD_BLOCK,
                Material.COAL_BLOCK,
                Material.IRON_BLOCK,
                Material.GOLD_BLOCK,
                Material.REDSTONE_BLOCK,
                Material.LAPIS_BLOCK,
                Material.DIAMOND_BLOCK,
                Material.EMERALD_BLOCK,
                Material.LIGHT_BLUE_CONCRETE,
                Material.CYAN_CONCRETE,
                Material.BLUE_CONCRETE,
                Material.PURPLE_CONCRETE,
                Material.MAGENTA_CONCRETE,
                Material.LIME_CONCRETE,
                Material.ORANGE_CONCRETE,
                Material.WHITE_CONCRETE,
                Material.BLACK_CONCRETE,
                Material.DEEPSLATE_TILES,
                Material.DEEPSLATE_BRICKS,
                Material.POLISHED_DEEPSLATE,
                Material.POLISHED_BLACKSTONE_BRICKS,
                Material.GILDED_BLACKSTONE,
                Material.END_STONE_BRICKS,
                Material.TUFF,
                Material.POLISHED_TUFF,
                Material.TUFF_BRICKS
        ));
    }

    private Map<Material, Integer> getDefaultPalette() {
        Map<Material, Integer> palette = new LinkedHashMap<>();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("private-mines.default-palette");

        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);

                if (material == null || !material.isBlock() || material.isAir()) {
                    continue;
                }

                if (!isPickaxeFriendlyPaletteMaterial(material)) {
                    continue;
                }

                int weight = section.getInt(key);

                if (weight > 0) {
                    palette.put(material, weight);
                }
            }
        }

        if (palette.isEmpty()) {
            palette.put(Material.STONE, 80);
            palette.put(Material.COAL_ORE, 12);
            palette.put(Material.IRON_ORE, 8);
        }

        return palette;
    }

    public void playConfiguredSound(Player player, String soundKey) {
        String soundName = plugin.getConfig().getString("private-mines.sounds." + soundKey);

        if (soundName == null || soundName.isBlank()) {
            return;
        }

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public record MineSize(int radius, int height, int tier, int maxTier) {
    }
}