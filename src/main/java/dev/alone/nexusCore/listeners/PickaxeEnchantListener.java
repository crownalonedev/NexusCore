package dev.alone.nexusCore.listeners;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.managers.PickaxeEnchant;
import dev.alone.nexusCore.profiles.PlayerProfile;
import dev.alone.nexusCore.utils.MessageUtil;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class PickaxeEnchantListener implements Listener {

    private final NexusCore plugin;

    public PickaxeEnchantListener(NexusCore plugin) {
        this.plugin = plugin;
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

        int affectedBlocks = 1;

        if (shouldProc(PickaxeEnchant.NUKE, profile)) {
            int broken = breakCube(player, block, 16, 2500);
            affectedBlocks += broken;
            sendProcMessage(player, PickaxeEnchant.NUKE, broken);
        } else if (shouldProc(PickaxeEnchant.JACKHAMMER, profile)) {
            int broken = breakLayer(player, block);
            affectedBlocks += broken;
            sendProcMessage(player, PickaxeEnchant.JACKHAMMER, broken);
        } else if (shouldProc(PickaxeEnchant.MINE_STRIKE, profile)) {
            int broken = breakCube(player, block, 3, 320);
            affectedBlocks += broken;
            sendProcMessage(player, PickaxeEnchant.MINE_STRIKE, broken);
        } else if (shouldProc(PickaxeEnchant.VEIN_MINER, profile)) {
            int broken = breakVein(player, block, 5, 220);
            affectedBlocks += broken;
            sendProcMessage(player, PickaxeEnchant.VEIN_MINER, broken);
        } else if (shouldProc(PickaxeEnchant.METEOR_STRIKE, profile)) {
            int broken = breakCube(player, block, 6, 900);
            affectedBlocks += broken;
            sendProcMessage(player, PickaxeEnchant.METEOR_STRIKE, broken);
        } else if (shouldProc(PickaxeEnchant.METEOR, profile)) {
            int broken = breakCube(player, block, 4, 450);
            affectedBlocks += broken;
            sendProcMessage(player, PickaxeEnchant.METEOR, broken);
        } else if (shouldProc(PickaxeEnchant.SHOCKWAVE, profile)) {
            int broken = breakRing(player, block, 7, 600);
            affectedBlocks += broken;
            sendProcMessage(player, PickaxeEnchant.SHOCKWAVE, broken);
        } else if (shouldProc(PickaxeEnchant.CLUSTER_BOMB, profile)) {
            int broken = breakCube(player, block, 5, 650);
            affectedBlocks += broken;
            sendProcMessage(player, PickaxeEnchant.CLUSTER_BOMB, broken);
        } else if (shouldProc(PickaxeEnchant.HOLY_ARROWS, profile)) {
            int broken = breakColumns(player, block, 8, 500);
            affectedBlocks += broken;
            sendProcMessage(player, PickaxeEnchant.HOLY_ARROWS, broken);
        }

        int extraBlocks = Math.max(0, affectedBlocks - 1);

        if (extraBlocks > 0) {
            plugin.getProgressionManager().addMiningProgress(player, profile, extraBlocks);
        }

        RewardResult rewards = rollRewards(player, profile, affectedBlocks);

        if (rewards.hasAnyReward()) {
            giveRewards(player, profile, rewards);
        }

        plugin.getProfileManager().saveProfile(profile);
        plugin.getPickaxeManager().syncPickaxe(player);
    }

    private int breakLayer(Player player, Block origin) {
        int broken = 0;
        int maxBlocks = Math.max(1, plugin.getPickaxeManager().getEnchantInt(PickaxeEnchant.JACKHAMMER, "max-extra-blocks", 5000));
        int radius = Math.max(1, plugin.getPickaxeManager().getEnchantInt(PickaxeEnchant.JACKHAMMER, "layer-radius", 64));

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }

                if (broken >= maxBlocks) {
                    return broken;
                }

                Block nearby = origin.getRelative(x, 0, z);

                if (breakExtraBlock(player, origin, nearby)) {
                    broken++;
                }
            }
        }

        return broken;
    }

    private int breakCube(Player player, Block origin, int radius, int maxBlocks) {
        int broken = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }

                    if (broken >= maxBlocks) {
                        return broken;
                    }

                    if (breakExtraBlock(player, origin, origin.getRelative(x, y, z))) {
                        broken++;
                    }
                }
            }
        }

        return broken;
    }

    private int breakVein(Player player, Block origin, int radius, int maxBlocks) {
        int broken = 0;

        for (int distance = 1; distance <= radius; distance++) {
            Block[] blocks = {
                    origin.getRelative(distance, 0, 0),
                    origin.getRelative(-distance, 0, 0),
                    origin.getRelative(0, distance, 0),
                    origin.getRelative(0, -distance, 0),
                    origin.getRelative(0, 0, distance),
                    origin.getRelative(0, 0, -distance)
            };

            for (Block block : blocks) {
                if (broken >= maxBlocks) {
                    return broken;
                }

                if (breakExtraBlock(player, origin, block)) {
                    broken++;
                }
            }
        }

        return broken;
    }

    private int breakRing(Player player, Block origin, int radius, int maxBlocks) {
        int broken = 0;

        for (int r = 1; r <= radius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.abs(x) != r && Math.abs(z) != r) {
                        continue;
                    }

                    if (broken >= maxBlocks) {
                        return broken;
                    }

                    if (breakExtraBlock(player, origin, origin.getRelative(x, 0, z))) {
                        broken++;
                    }
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
                    if (broken >= maxBlocks) {
                        return broken;
                    }

                    if (breakExtraBlock(player, origin, origin.getRelative(x, y, z))) {
                        broken++;
                    }
                }
            }
        }

        return broken;
    }

    private boolean breakExtraBlock(Player player, Block origin, Block block) {
        if (block == null || block.equals(origin)) {
            return false;
        }

        if (!block.getWorld().equals(origin.getWorld())) {
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

    private RewardResult rollRewards(Player player, PlayerProfile profile, int affectedBlocks) {
        RewardResult result = new RewardResult();

        result.tokens = result.tokens.add(rollCurrencyReward(PickaxeEnchant.TOKEN_FINDER, profile, affectedBlocks));
        result.tokens = result.tokens.add(rollCurrencyReward(PickaxeEnchant.TOKEN_MERCHANT, profile, affectedBlocks));
        result.tokens = result.tokens.add(rollCurrencyReward(PickaxeEnchant.TOKEN_EXPLOSION, profile, affectedBlocks));
        result.tokens = result.tokens.add(rollCurrencyReward(PickaxeEnchant.TOKEN_GREED, profile, affectedBlocks));

        result.gems = result.gems.add(rollCurrencyReward(PickaxeEnchant.GEM_FINDER, profile, affectedBlocks));
        result.gems = result.gems.add(rollCurrencyReward(PickaxeEnchant.GEM_MERCHANT, profile, affectedBlocks));
        result.gems = result.gems.add(rollCurrencyReward(PickaxeEnchant.METEOR, profile, affectedBlocks));
        result.gems = result.gems.add(rollCurrencyReward(PickaxeEnchant.METEOR_STRIKE, profile, affectedBlocks));

        result.beacons = result.beacons.add(rollCurrencyReward(PickaxeEnchant.BEACON_FINDER, profile, affectedBlocks));
        result.keys = rollKeyReward(profile, affectedBlocks);

        int keyMerchantLevel = profile.getEnchantLevel(PickaxeEnchant.KEY_MERCHANT);
        if (result.keys > 0 && keyMerchantLevel > 0 && plugin.getPickaxeManager().isEnchantEnabled(PickaxeEnchant.KEY_MERCHANT)) {
            int multiplier = 1 + Math.max(0, keyMerchantLevel / 50);
            result.keys *= multiplier;
        }

        return result;
    }

    private BigInteger rollCurrencyReward(PickaxeEnchant enchant, PlayerProfile profile, int affectedBlocks) {
        if (!plugin.getPickaxeManager().isEnchantEnabled(enchant)) {
            return BigInteger.ZERO;
        }

        int level = profile.getEnchantLevel(enchant);

        if (level <= 0 || affectedBlocks <= 0) {
            return BigInteger.ZERO;
        }

        int maxRolls = Math.max(1, plugin.getPickaxeManager().getEnchantInt(enchant, "max-reward-rolls-per-break", 350));
        int rolls = Math.min(affectedBlocks, maxRolls);
        BigInteger total = BigInteger.ZERO;

        for (int i = 0; i < rolls; i++) {
            if (!rollChance(enchant, profile)) {
                continue;
            }

            total = total.add(rollRewardAmount(enchant, profile));
        }

        return total;
    }

    private int rollKeyReward(PlayerProfile profile, int affectedBlocks) {
        PickaxeEnchant enchant = PickaxeEnchant.KEY_FINDER;

        if (!plugin.getPickaxeManager().isEnchantEnabled(enchant)) {
            return 0;
        }

        int level = profile.getEnchantLevel(enchant);

        if (level <= 0 || affectedBlocks <= 0) {
            return 0;
        }

        int maxRolls = Math.max(1, plugin.getPickaxeManager().getEnchantInt(enchant, "max-reward-rolls-per-break", 350));
        int rolls = Math.min(affectedBlocks, maxRolls);
        int total = 0;

        for (int i = 0; i < rolls; i++) {
            if (!rollChance(enchant, profile)) {
                continue;
            }

            if (total >= Integer.MAX_VALUE) {
                break;
            }

            BigInteger amount = rollRewardAmount(enchant, profile);
            BigInteger safeAmount = amount.min(BigInteger.valueOf(Integer.MAX_VALUE - (long) total));
            total += safeAmount.intValue();
        }

        return total;
    }

    private BigInteger rollRewardAmount(PickaxeEnchant enchant, PlayerProfile profile) {
        int level = profile.getEnchantLevel(enchant);
        BigInteger minimum = plugin.getPickaxeManager().getEnchantBigInteger(enchant, "min-reward", BigInteger.ONE).max(BigInteger.ONE);
        BigInteger maximum = plugin.getPickaxeManager().getEnchantBigInteger(enchant, "max-reward", minimum).max(minimum);
        BigInteger perLevelReward = plugin.getPickaxeManager().getEnchantBigInteger(enchant, "reward-per-level", BigInteger.ZERO).max(BigInteger.ZERO);
        int levelsPerExtra = Math.max(1, plugin.getPickaxeManager().getEnchantInt(enchant, "levels-per-extra-reward", 25));
        BigInteger extraReward = plugin.getPickaxeManager().getEnchantBigInteger(enchant, "extra-reward", BigInteger.ZERO).max(BigInteger.ZERO);

        BigInteger amount = randomBigInteger(minimum, maximum);
        amount = amount.add(perLevelReward.multiply(BigInteger.valueOf(level)));
        amount = amount.add(extraReward.multiply(BigInteger.valueOf(level / levelsPerExtra)));

        double rewardMultiplier = Math.max(0.0, plugin.getPickaxeManager().getEnchantDouble(enchant, "reward-multiplier", 1.0));

        if (rewardMultiplier != 1.0) {
            amount = multiplyBigInteger(amount, rewardMultiplier);
        }

        return amount.max(BigInteger.ONE);
    }

    private BigInteger randomBigInteger(BigInteger minimum, BigInteger maximum) {
        if (maximum.compareTo(minimum) <= 0) {
            return minimum;
        }

        BigInteger range = maximum.subtract(minimum).add(BigInteger.ONE);

        if (range.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
            long bound = range.longValue();
            long random = ThreadLocalRandom.current().nextLong(bound);
            return minimum.add(BigInteger.valueOf(random));
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
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0 || multiplier <= 0.0) {
            return BigInteger.ZERO;
        }

        return new BigDecimal(amount)
                .multiply(BigDecimal.valueOf(multiplier))
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger()
                .max(BigInteger.ONE);
    }

    private boolean shouldProc(PickaxeEnchant enchant, PlayerProfile profile) {
        if (!plugin.getPickaxeManager().isEnchantEnabled(enchant)) {
            return false;
        }

        if (profile.getEnchantLevel(enchant) <= 0) {
            return false;
        }

        return rollChance(enchant, profile);
    }

    private boolean rollChance(PickaxeEnchant enchant, PlayerProfile profile) {
        int level = profile.getEnchantLevel(enchant);
        double baseChance = plugin.getPickaxeManager().getEnchantDouble(enchant, "base-chance", 0.0);
        double chancePerLevel = plugin.getPickaxeManager().getEnchantDouble(enchant, "chance-per-level", 0.0);
        double maxChance = plugin.getPickaxeManager().getEnchantDouble(enchant, "max-chance", 100.0);
        double chance = baseChance + (level * chancePerLevel);
        chance = Math.max(0.0, Math.min(maxChance, chance));
        return ThreadLocalRandom.current().nextDouble(100.0) < chance;
    }

    private void giveRewards(Player player, PlayerProfile profile, RewardResult rewards) {
        if (rewards.tokens.compareTo(BigInteger.ZERO) > 0) {
            profile.addTokens(rewards.tokens);
        }

        if (rewards.gems.compareTo(BigInteger.ZERO) > 0) {
            profile.addGems(rewards.gems);
        }

        if (rewards.beacons.compareTo(BigInteger.ZERO) > 0) {
            profile.addBeacons(rewards.beacons);
        }

        if (rewards.keys > 0) {
            dispatchKeyCommands(player, rewards.keys);
        }
    }

    private void dispatchKeyCommands(Player player, int amount) {
        List<String> commands = plugin.getPickaxeManager().getEnchantStringList(PickaxeEnchant.KEY_FINDER, "commands");

        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }

            String parsed = command
                    .replace("%player%", player.getName())
                    .replace("%amount%", String.valueOf(amount));

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    private void sendProcMessage(Player player, PickaxeEnchant enchant, int blocks) {
        if (!plugin.getPickaxeManager().isProcMessagesEnabled(enchant)) {
            return;
        }

        String message = plugin.getPickaxeManager().getProcMessage(enchant)
                .replace("%enchant%", enchant.getDisplayName())
                .replace("%blocks%", format(BigInteger.valueOf(Math.max(0, blocks))));

        MessageUtil.sendWithPrefix(player, message);
    }

    private boolean isValidMineBlock(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }

        if (isExcludedWorld(block.getWorld().getName())) {
            return false;
        }

        if (plugin.getMineManager() != null && plugin.getMineManager().isMineWorld(block.getWorld())) {
            if (!plugin.getMineManager().canBreakBlock(player, block.getLocation())) {
                return false;
            }
        }

        Material material = block.getType();

        if (material.isAir()) {
            return false;
        }

        if (!material.isBlock()) {
            return false;
        }

        return !isIgnoredMaterial(material);
    }

    private boolean isExcludedWorld(String worldName) {
        if (worldName == null) {
            return false;
        }

        for (String excludedWorld : plugin.getConfig().getStringList("progression.excluded-worlds")) {
            if (worldName.equalsIgnoreCase(excludedWorld)) {
                return true;
            }
        }

        return false;
    }

    private boolean isIgnoredMaterial(Material material) {
        if (material == null) {
            return true;
        }

        for (String ignoredMaterial : plugin.getConfig().getStringList("progression.ignored-materials")) {
            if (material.name().equalsIgnoreCase(ignoredMaterial)) {
                return true;
            }
        }

        String name = material.name().toLowerCase(Locale.ROOT);
        return name.endsWith("_door")
                || name.endsWith("_trapdoor")
                || name.endsWith("_button")
                || name.endsWith("_pressure_plate")
                || name.endsWith("_sign")
                || name.endsWith("_banner")
                || name.endsWith("_bed")
                || name.contains("shulker_box")
                || material.hasGravity();
    }

    private String format(BigInteger number) {
        return NumberFormat.getNumberInstance(Locale.US).format(number);
    }

    private static final class RewardResult {
        private BigInteger tokens = BigInteger.ZERO;
        private BigInteger gems = BigInteger.ZERO;
        private BigInteger beacons = BigInteger.ZERO;
        private int keys = 0;

        private boolean hasAnyReward() {
            return tokens.compareTo(BigInteger.ZERO) > 0
                    || gems.compareTo(BigInteger.ZERO) > 0
                    || beacons.compareTo(BigInteger.ZERO) > 0
                    || keys > 0;
        }
    }
}
