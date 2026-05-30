package dev.alone.nexusCore.managers;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.profiles.PlayerProfile;
import dev.alone.nexusCore.utils.MessageUtil;
import dev.alone.nexusCore.utils.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class ProgressionManager {

    private final NexusCore plugin;

    public ProgressionManager(NexusCore plugin) {
        this.plugin = plugin;
    }

    public void handleBlockBreak(Player player, Material material) {
        if (player == null || material == null) {
            return;
        }

        if (isExcludedWorld(player.getWorld().getName())) {
            return;
        }

        if (isIgnoredMaterial(material)) {
            return;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        if (profile == null) {
            return;
        }

        addMiningProgress(player, profile, 1L);
    }

    public void addMiningProgress(Player player, PlayerProfile profile, long blocks) {
        if (player == null || profile == null || blocks <= 0) {
            return;
        }

        profile.addBlocksMined(blocks);
        profile.addRawBlocksMined(blocks);
        profile.addPickaxeXp(blocks * getPickaxeXpPerBlock());

        if (!isAutomaticRankupEnabled()) {
            return;
        }

        profile.addRankProgressBlocks(blocks * getRankProgressPerBlock());
        processAutomaticProgression(player, profile);
    }

    public void processAutomaticProgression(Player player, PlayerProfile profile) {
        if (player == null || profile == null) {
            return;
        }

        int maxRank = plugin.getProfileManager().getMaxRank();
        int rankups = 0;

        while (!profile.isMaxRank(maxRank)) {
            long blocksRequired = getBlocksRequiredForNextRank(profile);

            if (profile.getRankProgressBlocks() < blocksRequired) {
                break;
            }

            profile.removeRankProgressBlocks(blocksRequired);
            profile.rankUp(maxRank);
            rankups++;

            if (rankups >= maxRank) {
                break;
            }
        }

        if (rankups > 0) {
            if (rankups == 1) {
                sendProgressionMessage(
                        player,
                        "rankup",
                        "<green>You ranked up to <aqua>Rank %rank%<green>!",
                        profile
                );
            } else {
                sendProgressionMessage(
                        player,
                        "multi-rankup",
                        "<green>You ranked up <aqua>%rankups%x <green>and reached <aqua>Rank %rank%<green>!",
                        profile,
                        rankups
                );
            }

            SoundUtil.play(player, "sounds.rankup");
        }

        if (!profile.isMaxRank(maxRank)) {
            return;
        }

        if (rankups > 0) {
            sendProgressionMessage(
                    player,
                    "max-rank",
                    "<yellow>You reached max rank. Rank is now capped, so keep mining for rebirth progress.",
                    profile
            );
        }

        int rebirthsProcessed = 0;

        while (canRebirth(profile)) {
            if (!attemptRebirth(player, profile, true)) {
                break;
            }

            rebirthsProcessed++;

            if (rebirthsProcessed >= 100) {
                break;
            }
        }
    }

    public boolean attemptRebirth(Player player, PlayerProfile profile, boolean automatic) {
        if (player == null || profile == null) {
            return false;
        }

        if (!canRebirth(profile)) {
            if (!automatic) {
                sendProgressionMessage(
                        player,
                        "cannot-rebirth",
                        "<red>You need <white>%blocks_remaining_rebirth% <red>more blocks before rebirthing.",
                        profile
                );
            }

            return false;
        }

        int maxRank = plugin.getProfileManager().getMaxRank();
        long blocksRequired = getBlocksRequiredForNextRebirth(profile);

        profile.removeRankProgressBlocks(blocksRequired);
        profile.rebirth(maxRank);

        if (automatic) {
            sendProgressionMessage(
                    player,
                    "auto-rebirth",
                    "<light_purple>Rebirth complete! You are now <white>%rebirth%<light_purple>.",
                    profile
            );
        } else {
            sendProgressionMessage(
                    player,
                    "rebirth",
                    "<light_purple>You rebirthed! You are now <white>%rebirth%<light_purple>.",
                    profile
            );
        }

        SoundUtil.play(player, "sounds.rebirth");

        if (profile.isAutoAscension()) {
            attemptAscension(player, profile, true);
        }

        plugin.getProfileManager().saveProfile(profile.getUuid());
        return true;
    }

    public boolean attemptAscension(Player player, PlayerProfile profile, boolean automatic) {
        if (player == null || profile == null) {
            return false;
        }

        int maxRank = plugin.getProfileManager().getMaxRank();
        int rebirthsRequired = plugin.getProfileManager().getRebirthsRequiredToAscend();

        if (!profile.canAscend(rebirthsRequired)) {
            if (!automatic) {
                sendProgressionMessage(
                        player,
                        "cannot-ascend",
                        "<red>You need <white>%rebirths_until_ascension% <red>more rebirths before ascending.",
                        profile
                );
            }

            return false;
        }

        profile.ascend(rebirthsRequired, maxRank);

        if (automatic) {
            sendProgressionMessage(
                    player,
                    "auto-ascension",
                    "<gold>Auto Ascension complete! Your rebirths have reset. You are now <white>%ascension%<gold>.",
                    profile
            );
        } else {
            sendProgressionMessage(
                    player,
                    "ascension",
                    "<gold>You ascended! Your rebirths have reset. You are now <white>%ascension%<gold>.",
                    profile
            );
        }

        SoundUtil.play(player, "sounds.ascension");

        plugin.getProfileManager().saveProfile(profile.getUuid());
        return true;
    }

    public boolean canRebirth(PlayerProfile profile) {
        if (profile == null) {
            return false;
        }

        int maxRank = plugin.getProfileManager().getMaxRank();

        return profile.canRebirth(maxRank) && profile.getRankProgressBlocks() >= getBlocksRequiredForNextRebirth(profile);
    }

    public long getBlocksRequiredForNextRank(PlayerProfile profile) {
        if (profile == null) {
            return 1L;
        }

        if (profile.isMaxRank(plugin.getProfileManager().getMaxRank())) {
            return getBlocksRequiredForNextRebirth(profile);
        }

        long base = Math.max(1L, plugin.getConfig().getLong("progression.blocks-per-rank.base", 100L));
        long increasePerRank = Math.max(0L, plugin.getConfig().getLong("progression.blocks-per-rank.increase-per-rank", 25L));

        double rebirthMultiplier = Math.max(0.0, plugin.getConfig().getDouble("progression.blocks-per-rank.rebirth-multiplier", 0.15));
        double ascensionMultiplier = Math.max(0.0, plugin.getConfig().getDouble("progression.blocks-per-rank.ascension-multiplier", 0.35));

        long rawRequirement = base + ((long) Math.max(0, profile.getRank() - 1) * increasePerRank);
        double multiplier = 1.0
                + (profile.getRebirth() * rebirthMultiplier)
                + (profile.getAscension() * ascensionMultiplier);

        return Math.max(1L, Math.round(rawRequirement * multiplier));
    }

    public long getBlocksRequiredForNextRebirth(PlayerProfile profile) {
        if (profile == null) {
            return 1L;
        }

        long base = Math.max(1L, plugin.getConfig().getLong("progression.blocks-per-rebirth.base", 2500L));
        long increasePerRebirth = Math.max(0L, plugin.getConfig().getLong("progression.blocks-per-rebirth.increase-per-rebirth", 500L));
        double ascensionMultiplier = Math.max(0.0, plugin.getConfig().getDouble("progression.blocks-per-rebirth.ascension-multiplier", 0.50));

        long rawRequirement = base + ((long) profile.getRebirth() * increasePerRebirth);
        double multiplier = 1.0 + (profile.getAscension() * ascensionMultiplier);

        return Math.max(1L, Math.round(rawRequirement * multiplier));
    }

    public long getBlocksRemainingForNextRank(PlayerProfile profile) {
        if (profile == null) {
            return 0L;
        }

        return Math.max(0L, getBlocksRequiredForNextRank(profile) - profile.getRankProgressBlocks());
    }

    public long getBlocksRemainingForNextRebirth(PlayerProfile profile) {
        if (profile == null) {
            return 0L;
        }

        return Math.max(0L, getBlocksRequiredForNextRebirth(profile) - profile.getRankProgressBlocks());
    }

    public double getRankProgressPercent(PlayerProfile profile) {
        if (profile == null) {
            return 0.0;
        }

        long required = getBlocksRequiredForNextRank(profile);

        if (required <= 0L) {
            return 100.0;
        }

        double percent = ((double) profile.getRankProgressBlocks() / (double) required) * 100.0;
        return Math.max(0.0, Math.min(100.0, percent));
    }

    public String getRankColor(int rank) {
        return getHundredColor("chat.colors.rank", rank);
    }

    public String getRebirthColor(int rebirth) {
        return getHundredColor("chat.colors.rebirth", rebirth);
    }

    public String getAscensionColor(int ascension) {
        return getHundredColor("chat.colors.ascension", ascension);
    }

    public String getColoredRank(PlayerProfile profile) {
        if (profile == null) {
            return getRankColor(1) + "1";
        }

        return getRankColor(profile.getRank()) + profile.getRank();
    }

    public String getColoredRebirth(PlayerProfile profile) {
        if (profile == null) {
            return getRebirthColor(1) + "R0";
        }

        return getRebirthColor(profile.getRebirth()) + "R" + profile.getRebirth();
    }

    public String getColoredAscension(PlayerProfile profile) {
        if (profile == null) {
            return getAscensionColor(1) + "A0";
        }

        return getAscensionColor(profile.getAscension()) + "A" + profile.getAscension();
    }

    public String getChatTag(PlayerProfile profile) {
        if (profile == null) {
            return "";
        }

        if (profile.getAscension() > 0) {
            return "&8[" + getColoredAscension(profile) + "&8] &8[" + getColoredRebirth(profile) + "&8]";
        }

        if (profile.getRebirth() > 0) {
            return "&8[" + getColoredRebirth(profile) + "&8]";
        }

        return "&8[" + getColoredRank(profile) + "&8]";
    }

    public boolean isAutomaticRankupEnabled() {
        return plugin.getConfig().getBoolean("progression.automatic-rankup.enabled", true);
    }

    public long getRankProgressPerBlock() {
        return Math.max(1L, plugin.getConfig().getLong("progression.automatic-rankup.rank-progress-per-block", 1L));
    }

    public long getPickaxeXpPerBlock() {
        return Math.max(0L, plugin.getConfig().getLong("progression.pickaxe-xp-per-block", 1L));
    }

    private String getHundredColor(String path, int value) {
        List<String> colors = plugin.getConfig().getStringList(path);

        if (colors.isEmpty()) {
            return "&f";
        }

        int safeValue = Math.max(1, value);
        int index = (safeValue - 1) / 100;

        if (index >= colors.size()) {
            index = colors.size() - 1;
        }

        return colors.get(index);
    }

    private boolean isExcludedWorld(String worldName) {
        if (worldName == null) {
            return false;
        }

        List<String> excludedWorlds = plugin.getConfig().getStringList("progression.excluded-worlds");

        for (String excludedWorld : excludedWorlds) {
            if (worldName.equalsIgnoreCase(excludedWorld)) {
                return true;
            }
        }

        return false;
    }

    private boolean isIgnoredMaterial(Material material) {
        List<String> ignoredMaterials = plugin.getConfig().getStringList("progression.ignored-materials");

        for (String ignoredMaterial : ignoredMaterials) {
            if (material.name().equalsIgnoreCase(ignoredMaterial)) {
                return true;
            }
        }

        return false;
    }

    private void sendProgressionMessage(Player player, String messageKey, String fallback, PlayerProfile profile) {
        sendProgressionMessage(player, messageKey, fallback, profile, 1);
    }

    private void sendProgressionMessage(Player player, String messageKey, String fallback, PlayerProfile profile, int rankups) {
        String message = plugin.getConfig().getString("messages.progression." + messageKey, fallback);

        int maxRank = plugin.getProfileManager().getMaxRank();
        int rebirthsRequired = plugin.getProfileManager().getRebirthsRequiredToAscend();

        message = message
                .replace("%player%", player.getName())
                .replace("%rank%", String.valueOf(profile.getRank()))
                .replace("%max_rank%", String.valueOf(maxRank))
                .replace("%rankups%", String.valueOf(rankups))

                .replace("%rebirth%", getColoredRebirth(profile))
                .replace("%rebirth_raw%", String.valueOf(profile.getRebirth()))

                .replace("%ascension%", getColoredAscension(profile))
                .replace("%ascension_raw%", String.valueOf(profile.getAscension()))

                .replace("%rebirths_required%", String.valueOf(rebirthsRequired))
                .replace("%rebirths_until_ascension%", String.valueOf(profile.getRebirthsUntilAscension(rebirthsRequired)))
                .replace("%blocks_progress_rebirth%", String.valueOf(profile.getRankProgressBlocks()))
                .replace("%rebirth_progress%", String.valueOf(profile.getRankProgressBlocks()))
                .replace("%blocks_required_rebirth%", String.valueOf(getBlocksRequiredForNextRebirth(profile)))
                .replace("%rebirth_requirement%", String.valueOf(getBlocksRequiredForNextRebirth(profile)))
                .replace("%blocks_remaining_rebirth%", String.valueOf(getBlocksRemainingForNextRebirth(profile)));

        MessageUtil.sendWithPrefix(player, message);
    }
}
