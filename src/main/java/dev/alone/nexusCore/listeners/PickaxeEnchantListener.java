package dev.alone.nexusCore.listeners;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.managers.PickaxeEnchant;
import dev.alone.nexusCore.managers.PrivateMine;
import dev.alone.nexusCore.profiles.PlayerProfile;
import dev.alone.nexusCore.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class PickaxeEnchantListener implements Listener {

    private final NexusCore plugin;
    private final Map<UUID, EarningsWindow> earningsWindows = new HashMap<>();

    public PickaxeEnchantListener(NexusCore plugin) {
        this.plugin = plugin;
        startEarningsSummaryTask();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getPickaxeManager().isNexusPickaxe(player.getInventory().getItemInMainHand())) {
            return;
        }

        if (!plugin.getConfig().getBoolean("pickaxe.enchant-processing.enabled", true)) {
            return;
        }

        if (!plugin.getConfig().getBoolean("pickaxe.enchant-processing.allow-creative", false)
                && player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Block block = event.getBlock();

        if (plugin.getMineManager() != null && plugin.getMineManager().isMineWorld(block.getWorld())) {
            event.setDropItems(false);
            event.setExpToDrop(0);

            if (!plugin.getMineManager().canBreakBlock(player, block.getLocation())) {
                event.setCancelled(true);
                player.sendMessage(MessageUtil.color("<red>You can only mine inside your own private mine."));
                return;
            }
        }

        if (!isValidMineBlock(player, block)) {
            return;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        if (profile == null) {
            return;
        }

        int destructiveBlocks = processDestructiveEnchant(player, block, profile);
        int affectedBlocks = 1 + Math.max(0, destructiveBlocks);

        RewardResult rewards = rollRewards(player, profile, affectedBlocks);

        if (shouldProc(PickaxeEnchant.DOUBLE_STRIKE, profile)) {
            RewardResult secondRewards = rollRewards(player, profile, affectedBlocks);
            rewards.add(secondRewards);
            sendProcMessage(player, PickaxeEnchant.DOUBLE_STRIKE, affectedBlocks);
        }

        if (rewards.hasAnyReward()) {
            giveRewards(player, profile, rewards);
        }

        maybeAutoResetMine(player);
        plugin.getProfileManager().saveProfile(profile);
        plugin.getPickaxeManager().syncPickaxe(player);
    }

    private int processDestructiveEnchant(Player player, Block block, PlayerProfile profile) {
        if (shouldProc(PickaxeEnchant.HYDROGEN_BOMB, profile)) {
            int broken = breakCube(player, block, 22, 6000);
            sendProcMessage(player, PickaxeEnchant.HYDROGEN_BOMB, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.BLACK_HOLE, profile)) {
            int broken = breakSphere(player, block, 14, 4500);
            sendProcMessage(player, PickaxeEnchant.BLACK_HOLE, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.DRAGONS_WRATH, profile)) {
            int broken = breakColumns(player, block, 14, 2400);
            sendProcMessage(player, PickaxeEnchant.DRAGONS_WRATH, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.PROPHET, profile)) {
            int broken = breakColumns(player, block, 16, 3000);
            sendProcMessage(player, PickaxeEnchant.PROPHET, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.VOLCANO, profile)) {
            int broken = breakCube(player, block, 9, 2200);
            sendProcMessage(player, PickaxeEnchant.VOLCANO, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.NUKE, profile)) {
            int broken = breakCube(player, block, 16, 2500);
            sendProcMessage(player, PickaxeEnchant.NUKE, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.SNOW_ARMY, profile)) {
            int broken = breakLayer(player, block, 46, 3200);
            sendProcMessage(player, PickaxeEnchant.SNOW_ARMY, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.ENDERMAN_ABOMINATION, profile)) {
            int broken = breakRandomAround(player, block, 12, 1400);
            sendProcMessage(player, PickaxeEnchant.ENDERMAN_ABOMINATION, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.WILD_BLAZE, profile)) {
            int broken = breakColumns(player, block, 10, 1200);
            sendProcMessage(player, PickaxeEnchant.WILD_BLAZE, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.JACKHAMMER, profile)) {
            int broken = breakMineLayer(player, block);
            sendProcMessage(player, PickaxeEnchant.JACKHAMMER, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.METEOR_STRIKE, profile)) {
            int broken = breakCube(player, block, 7, 1000);
            sendProcMessage(player, PickaxeEnchant.METEOR_STRIKE, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.METEOR, profile)) {
            int broken = breakCube(player, block, 4, 450);
            sendProcMessage(player, PickaxeEnchant.METEOR, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.MINE_STRIKE, profile)) {
            int broken = breakCube(player, block, 3, 320);
            sendProcMessage(player, PickaxeEnchant.MINE_STRIKE, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.SHOCKWAVE, profile)) {
            int broken = breakRing(player, block, 7, 600);
            sendProcMessage(player, PickaxeEnchant.SHOCKWAVE, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.CLUSTER_BOMB, profile)) {
            int broken = breakCube(player, block, 5, 650);
            sendProcMessage(player, PickaxeEnchant.CLUSTER_BOMB, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.HOLY_ARROWS, profile)) {
            int broken = breakColumns(player, block, 8, 500);
            sendProcMessage(player, PickaxeEnchant.HOLY_ARROWS, broken);
            return broken;
        }

        if (shouldProc(PickaxeEnchant.VEIN_MINER, profile)) {
            int broken = breakVein(player, block, 5, 220);
            sendProcMessage(player, PickaxeEnchant.VEIN_MINER, broken);
            return broken;
        }

        return 0;
    }

    private int breakMineLayer(Player player, Block origin) {
        if (plugin.getMineManager() == null) {
            return breakLayer(player, origin, 64, 5000);
        }

        PrivateMine mine = plugin.getMineManager().getMine(player.getUniqueId());
        if (mine == null || !mine.contains(origin.getLocation())) {
            return breakLayer(player, origin, 64, 5000);
        }

        int broken = 0;
        int y = origin.getY();

        for (int x = mine.getMinX(); x <= mine.getMaxX(); x++) {
            for (int z = mine.getMinZ(); z <= mine.getMaxZ(); z++) {
                Block block = origin.getWorld().getBlockAt(x, y, z);
                if (breakExtraBlock(player, origin, block)) {
                    broken++;
                }
            }
        }

        return broken;
    }

    private int breakLayer(Player player, Block origin, int radius, int maxBlocks) {
        int broken = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (broken >= maxBlocks) return broken;
                if (breakExtraBlock(player, origin, origin.getRelative(x, 0, z))) broken++;
            }
        }
        return broken;
    }

    private int breakCube(Player player, Block origin, int radius, int maxBlocks) {
        int broken = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (broken >= maxBlocks) return broken;
                    if (x == 0 && y == 0 && z == 0) continue;
                    if (breakExtraBlock(player, origin, origin.getRelative(x, y, z))) broken++;
                }
            }
        }
        return broken;
    }

    private int breakSphere(Player player, Block origin, int radius, int maxBlocks) {
        int broken = 0;
        int radiusSquared = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (broken >= maxBlocks) return broken;
                    if (x == 0 && y == 0 && z == 0) continue;
                    if ((x * x) + (y * y) + (z * z) > radiusSquared) continue;
                    if (breakExtraBlock(player, origin, origin.getRelative(x, y, z))) broken++;
                }
            }
        }
        return broken;
    }

    private int breakVein(Player player, Block origin, int radius, int maxBlocks) {
        int broken = 0;
        for (int distance = 1; distance <= radius; distance++) {
            Block[] blocks = {
                    origin.getRelative(distance, 0, 0), origin.getRelative(-distance, 0, 0),
                    origin.getRelative(0, distance, 0), origin.getRelative(0, -distance, 0),
                    origin.getRelative(0, 0, distance), origin.getRelative(0, 0, -distance)
            };
            for (Block block : blocks) {
                if (broken >= maxBlocks) return broken;
                if (breakExtraBlock(player, origin, block)) broken++;
            }
        }
        return broken;
    }

    private int breakRing(Player player, Block origin, int radius, int maxBlocks) {
        int broken = 0;
        for (int r = 1; r <= radius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.abs(x) != r && Math.abs(z) != r) continue;
                    if (broken >= maxBlocks) return broken;
                    if (breakExtraBlock(player, origin, origin.getRelative(x, 0, z))) broken++;
                }
            }
        }
        return broken;
    }

    private int breakColumns(Player player, Block origin, int radius, int maxBlocks) {
        int broken = 0;
        for (int x = -radius; x <= radius; x += 2) {
            for (int z = -radius; z <= radius; z += 2) {
                for (int y = 0; y >= -6; y--) {
                    if (broken >= maxBlocks) return broken;
                    if (breakExtraBlock(player, origin, origin.getRelative(x, y, z))) broken++;
                }
            }
        }
        return broken;
    }

    private int breakRandomAround(Player player, Block origin, int radius, int maxBlocks) {
        int broken = 0;
        int attempts = Math.max(maxBlocks * 3, 100);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < attempts && broken < maxBlocks; i++) {
            int x = random.nextInt(-radius, radius + 1);
            int y = random.nextInt(-radius, radius + 1);
            int z = random.nextInt(-radius, radius + 1);
            if (x == 0 && y == 0 && z == 0) continue;
            if (breakExtraBlock(player, origin, origin.getRelative(x, y, z))) broken++;
        }
        return broken;
    }

    private boolean breakExtraBlock(Player player, Block origin, Block block) {
        if (block == null || block.equals(origin) || !block.getWorld().equals(origin.getWorld())) {
            return false;
        }

        if (plugin.getMineManager() != null && plugin.getMineManager().isMineWorld(block.getWorld())) {
            if (!plugin.getMineManager().canBreakBlock(player, block.getLocation())) {
                return false;
            }
        }

        if (!isValidMineBlock(player, block)) {
            return false;
        }

        block.setType(Material.AIR, false);
        return true;
    }

    private void maybeAutoResetMine(Player player) {
        if (plugin.getMineManager() == null) return;
        if (!plugin.getConfig().getBoolean("private-mines.auto-reset.enabled", true)) return;

        PrivateMine mine = plugin.getMineManager().getMine(player.getUniqueId());
        if (mine == null) return;

        double resetAtPercentBroken = plugin.getConfig().getDouble("private-mines.auto-reset.reset-at-percent-broken", 75.0);
        int total = 0;
        int air = 0;

        for (int x = mine.getMinX(); x <= mine.getMaxX(); x++) {
            for (int y = mine.getMinY(); y <= mine.getMaxY(); y++) {
                for (int z = mine.getMinZ(); z <= mine.getMaxZ(); z++) {
                    Block block = Bukkit.getWorld(mine.getWorldName()).getBlockAt(x, y, z);
                    if (!isValidMineBlock(player, block) && !block.getType().isAir()) continue;
                    total++;
                    if (block.getType().isAir()) air++;
                }
            }
        }

        if (total <= 0) return;
        double brokenPercent = (air * 100.0) / total;
        if (brokenPercent >= resetAtPercentBroken) {
            plugin.getMineManager().resetMine(mine);
            player.sendMessage(MessageUtil.color("<gradient:#00CFFF:#0066FF><bold>Nexus Mine</bold></gradient> <dark_gray>»</dark_gray> <gray>Your mine was automatically reset at <aqua>" + String.format(Locale.US, "%.1f", brokenPercent) + "%</aqua><gray> broken."));
        }
    }

    private RewardResult rollRewards(Player player, PlayerProfile profile, int affectedBlocks) {
        RewardResult result = new RewardResult();
        for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
            if (enchant == PickaxeEnchant.EFFICIENCY) continue;
            if (isTokenRewardEnchant(enchant)) result.tokens = result.tokens.add(rollCurrencyReward(enchant, profile, affectedBlocks, result));
            if (isGemRewardEnchant(enchant)) result.gems = result.gems.add(rollCurrencyReward(enchant, profile, affectedBlocks, result));
            if (enchant == PickaxeEnchant.BEACON_FINDER) result.beacons = result.beacons.add(rollCurrencyReward(enchant, profile, affectedBlocks, result));
            if (enchant == PickaxeEnchant.GANG_POINT_FINDER) result.gangPoints = result.gangPoints.add(rollCurrencyReward(enchant, profile, affectedBlocks, result));
        }
        result.keys = rollKeyReward(profile, affectedBlocks);
        return result;
    }

    private BigInteger rollCurrencyReward(PickaxeEnchant enchant, PlayerProfile profile, int affectedBlocks, RewardResult result) {
        if (!plugin.getPickaxeManager().isEnchantEnabled(enchant)) return BigInteger.ZERO;
        int level = profile.getEnchantLevel(enchant);
        if (level <= 0 || affectedBlocks <= 0) return BigInteger.ZERO;

        int maxRolls = Math.max(1, plugin.getPickaxeManager().getEnchantInt(enchant, "max-reward-rolls-per-break", 350));
        int rolls = Math.min(affectedBlocks, maxRolls);
        BigInteger total = BigInteger.ZERO;

        for (int i = 0; i < rolls; i++) {
            if (!rollChance(enchant, profile)) continue;
            total = total.add(rollRewardAmount(enchant, profile));
        }

        if (total.compareTo(BigInteger.ZERO) > 0) {
            result.addEnchantTotal(enchant, total);
        }
        return total;
    }

    private int rollKeyReward(PlayerProfile profile, int affectedBlocks) {
        PickaxeEnchant enchant = PickaxeEnchant.KEY_FINDER;
        if (!plugin.getPickaxeManager().isEnchantEnabled(enchant)) return 0;
        int level = profile.getEnchantLevel(enchant);
        if (level <= 0 || affectedBlocks <= 0) return 0;

        int rolls = Math.min(affectedBlocks, Math.max(1, plugin.getPickaxeManager().getEnchantInt(enchant, "max-reward-rolls-per-break", 350)));
        int total = 0;
        for (int i = 0; i < rolls; i++) {
            if (!rollChance(enchant, profile)) continue;
            total += rollRewardAmount(enchant, profile).min(BigInteger.valueOf(Integer.MAX_VALUE - (long) total)).intValue();
        }
        return total;
    }

    private BigInteger rollRewardAmount(PickaxeEnchant enchant, PlayerProfile profile) {
        int level = profile.getEnchantLevel(enchant);
        BigInteger minimum = plugin.getPickaxeManager().getEnchantBigInteger(enchant, "min-reward", BigInteger.ONE).max(BigInteger.ONE);
        BigInteger maximum = plugin.getPickaxeManager().getEnchantBigInteger(enchant, "max-reward", minimum).max(minimum);
        BigInteger amount = randomBigInteger(minimum, maximum);
        amount = amount.add(plugin.getPickaxeManager().getEnchantBigInteger(enchant, "reward-per-level", BigInteger.ZERO).multiply(BigInteger.valueOf(level)));
        amount = amount.add(plugin.getPickaxeManager().getEnchantBigInteger(enchant, "extra-reward", BigInteger.ZERO).multiply(BigInteger.valueOf(level / Math.max(1, plugin.getPickaxeManager().getEnchantInt(enchant, "levels-per-extra-reward", 25)))));
        return multiplyBigInteger(amount, Math.max(0.0, plugin.getPickaxeManager().getEnchantDouble(enchant, "reward-multiplier", 1.0))).max(BigInteger.ONE);
    }

    private boolean shouldProc(PickaxeEnchant enchant, PlayerProfile profile) {
        return plugin.getPickaxeManager().isEnchantEnabled(enchant)
                && profile.getEnchantLevel(enchant) > 0
                && rollChance(enchant, profile);
    }

    private boolean rollChance(PickaxeEnchant enchant, PlayerProfile profile) {
        int level = profile.getEnchantLevel(enchant);
        double baseChance = plugin.getPickaxeManager().getEnchantDouble(enchant, "base-chance", 0.0);
        double chancePerLevel = plugin.getPickaxeManager().getEnchantDouble(enchant, "chance-per-level", 0.0);
        double maxChance = plugin.getPickaxeManager().getEnchantDouble(enchant, "max-chance", 100.0);
        double chance = Math.max(0.0, Math.min(maxChance, baseChance + (level * chancePerLevel)));
        return chance >= 100.0 || ThreadLocalRandom.current().nextDouble(100.0) < chance;
    }

    private void giveRewards(Player player, PlayerProfile profile, RewardResult rewards) {
        profile.addTokens(rewards.tokens);
        profile.addGems(rewards.gems);
        profile.addBeacons(rewards.beacons);
        recordEarnings(player.getUniqueId(), rewards);
    }

    private void sendProcMessage(Player player, PickaxeEnchant enchant, int blocks) {
        if (blocks <= 0 || !plugin.getPickaxeManager().isProcMessagesEnabled(enchant)) return;
        String message = plugin.getPickaxeManager().getProcMessage(enchant)
                .replace("%blocks%", format(BigInteger.valueOf(blocks)))
                .replace("%enchant%", enchant.getDisplayName());
        player.sendMessage(MessageUtil.color(message));
    }

    private void recordEarnings(UUID uuid, RewardResult rewards) {
        EarningsWindow window = earningsWindows.computeIfAbsent(uuid, ignored -> new EarningsWindow());
        window.tokens = window.tokens.add(rewards.tokens);
        window.gems = window.gems.add(rewards.gems);
        window.beacons = window.beacons.add(rewards.beacons);
        rewards.enchantTotals.forEach((enchant, amount) -> window.enchantTotals.merge(enchant, amount, BigInteger::add));
    }

    private void startEarningsSummaryTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                EarningsWindow window = earningsWindows.remove(player.getUniqueId());
                if (window == null || !window.hasAny()) continue;

                Component hover = MessageUtil.color(buildEarningsHover(window));
                Component message = MessageUtil.color("<gradient:#00CFFF:#0066FF><bold>60s Earnings</bold></gradient> <dark_gray>»</dark_gray> <yellow>" + format(window.tokens) + " Tokens</yellow> <dark_gray>┃</dark_gray> <light_purple>" + format(window.gems) + " Gems</light_purple> <dark_gray>┃</dark_gray> <aqua>" + format(window.beacons) + " Beacons</aqua> <gray>(hover for top enchants)</gray>")
                        .hoverEvent(HoverEvent.showText(hover));
                player.sendMessage(message);
            }
        }, 20L * 60L, 20L * 60L);
    }

    private String buildEarningsHover(EarningsWindow window) {
        StringBuilder builder = new StringBuilder("<gradient:#00CFFF:#0066FF><bold>Top Enchant Earnings</bold></gradient>\n");
        if (window.enchantTotals.isEmpty()) {
            return builder.append("<gray>No enchant rewards this minute.").toString();
        }
        window.enchantTotals.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(8)
                .forEach(entry -> builder.append("<gray>• <aqua>").append(entry.getKey().getDisplayName()).append("</aqua><gray>: <yellow>").append(format(entry.getValue())).append("</yellow>\n"));
        return builder.toString();
    }

    private boolean isTokenRewardEnchant(PickaxeEnchant enchant) {
        return switch (enchant) {
            case TOKEN_FINDER, TOKEN_MERCHANT, TOKEN_EXPLOSION, TOKEN_GREED, WILD_BLAZE, SNOW_ARMY, DRAGONS_WRATH,
                    SCAVENGER, ENDERMAN_ABOMINATION, VOLCANO, HYDROGEN_BOMB, PROPHET, BLACK_HOLE -> true;
            default -> false;
        };
    }

    private boolean isGemRewardEnchant(PickaxeEnchant enchant) {
        return switch (enchant) {
            case GEM_FINDER, GEM_MERCHANT, METEOR, METEOR_STRIKE, WILD_BLAZE, SNOW_ARMY, DRAGONS_WRATH,
                    SCAVENGER, ENDERMAN_ABOMINATION, VOLCANO, HYDROGEN_BOMB, PROPHET, BLACK_HOLE, LOTTERY, SHATTER,
                    JACKPOT, METEORITE, FIRECRACKER, DRAGONS_EYE, FIRECRACKS, CHARITY, EXCAVATOR, ARCTIC_DESTROYER,
                    DYNAMITE, HIRED_HAND, SOUL_REAPER, OVERFLOW, CHUGGERNAUT, HEROS_ASSISTANCE, SNOWSTORM,
                    INVASION, BLESSED, WORSHIP, THUNDERBIRD, SWARM, SUPERNOVA -> true;
            default -> false;
        };
    }

    private boolean isValidMineBlock(Player player, Block block) {
        if (block == null || block.getType().isAir()) return false;
        Material material = block.getType();
        if (!material.isBlock()) return false;
        if (!plugin.getMineManager().canBreakBlock(player, block.getLocation())) return false;
        return !material.name().contains("BEDROCK") && material != Material.BARRIER;
    }

    private BigInteger randomBigInteger(BigInteger minimum, BigInteger maximum) {
        if (maximum.compareTo(minimum) <= 0) return minimum;
        BigInteger range = maximum.subtract(minimum).add(BigInteger.ONE);
        if (range.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
            return minimum.add(BigInteger.valueOf(ThreadLocalRandom.current().nextLong(range.longValue())));
        }
        BigInteger candidate;
        do {
            byte[] bytes = new byte[Math.max(1, (range.bitLength() + 7) / 8)];
            ThreadLocalRandom.current().nextBytes(bytes);
            candidate = new BigInteger(1, bytes);
        } while (candidate.compareTo(range) >= 0);
        return minimum.add(candidate);
    }

    private BigInteger multiplyBigInteger(BigInteger amount, double multiplier) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0 || multiplier <= 0.0) return BigInteger.ZERO;
        return new BigDecimal(amount).multiply(BigDecimal.valueOf(multiplier)).setScale(0, RoundingMode.HALF_UP).toBigInteger().max(BigInteger.ONE);
    }

    private String format(BigInteger number) {
        return NumberFormat.getNumberInstance(Locale.US).format(number == null ? BigInteger.ZERO : number);
    }

    private static final class RewardResult {
        private BigInteger tokens = BigInteger.ZERO;
        private BigInteger gems = BigInteger.ZERO;
        private BigInteger beacons = BigInteger.ZERO;
        private BigInteger gangPoints = BigInteger.ZERO;
        private int keys = 0;
        private final Map<PickaxeEnchant, BigInteger> enchantTotals = new HashMap<>();

        private boolean hasAnyReward() {
            return tokens.compareTo(BigInteger.ZERO) > 0 || gems.compareTo(BigInteger.ZERO) > 0 || beacons.compareTo(BigInteger.ZERO) > 0 || gangPoints.compareTo(BigInteger.ZERO) > 0 || keys > 0;
        }

        private void add(RewardResult other) {
            tokens = tokens.add(other.tokens);
            gems = gems.add(other.gems);
            beacons = beacons.add(other.beacons);
            gangPoints = gangPoints.add(other.gangPoints);
            keys += other.keys;
            other.enchantTotals.forEach((enchant, amount) -> enchantTotals.merge(enchant, amount, BigInteger::add));
        }

        private void addEnchantTotal(PickaxeEnchant enchant, BigInteger amount) {
            enchantTotals.merge(enchant, amount, BigInteger::add);
        }
    }

    private static final class EarningsWindow {
        private BigInteger tokens = BigInteger.ZERO;
        private BigInteger gems = BigInteger.ZERO;
        private BigInteger beacons = BigInteger.ZERO;
        private final Map<PickaxeEnchant, BigInteger> enchantTotals = new HashMap<>();

        private boolean hasAny() {
            return tokens.compareTo(BigInteger.ZERO) > 0 || gems.compareTo(BigInteger.ZERO) > 0 || beacons.compareTo(BigInteger.ZERO) > 0 || !enchantTotals.isEmpty();
        }
    }
}
