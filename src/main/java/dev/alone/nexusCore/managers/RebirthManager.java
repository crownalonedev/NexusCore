package dev.alone.nexusCore.managers;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.profiles.PlayerProfile;
import dev.alone.nexusCore.profiles.ProfileManager;
import dev.alone.nexusCore.utils.MessageUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Locale;

public class RebirthManager {

    private final NexusCore plugin;
    private final ProfileManager profileManager;

    public RebirthManager(NexusCore plugin, ProfileManager profileManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
    }

    public BigInteger getTokenCost(PlayerProfile profile) {
        BigInteger base = BigInteger.valueOf(plugin.getConfig().getLong("rebirth.costs.tokens.base", 100000));
        BigInteger increase = BigInteger.valueOf(plugin.getConfig().getLong("rebirth.costs.tokens.increase-per-rebirth", 75000));
        BigInteger rebirth = BigInteger.valueOf(profile.getRebirth());

        return base.add(increase.multiply(rebirth));
    }

    public BigInteger getGemCost(PlayerProfile profile) {
        BigInteger base = BigInteger.valueOf(plugin.getConfig().getLong("rebirth.costs.gems.base", 250));
        BigInteger increase = BigInteger.valueOf(plugin.getConfig().getLong("rebirth.costs.gems.increase-per-rebirth", 175));
        BigInteger rebirth = BigInteger.valueOf(profile.getRebirth());

        return base.add(increase.multiply(rebirth));
    }

    public void sendRebirthInfo(Player player) {
        PlayerProfile profile = profileManager.getProfile(player);

        BigInteger tokenCost = getTokenCost(profile);
        BigInteger gemCost = getGemCost(profile);

        MessageUtil.send(player, "");
        MessageUtil.send(player, "<gradient:#00CFFF:#0066FF><bold>Rebirth</bold></gradient>");
        MessageUtil.send(player, "<gray>Current Rebirth: <aqua>" + profile.getRebirth() + "</aqua>");
        MessageUtil.send(player, "<gray>Next Rebirth: <aqua>" + (profile.getRebirth() + 1) + "</aqua>");
        MessageUtil.send(player, "<gray>Required Rank: <aqua>" + profileManager.getMaxRank() + "</aqua>");
        MessageUtil.send(player, "");
        MessageUtil.send(player, "<gray>Cost:");
        MessageUtil.send(player, "  <gray>Tokens: <yellow>" + format(tokenCost) + "</yellow>");
        MessageUtil.send(player, "  <gray>Gems: <light_purple>" + format(gemCost) + "</light_purple>");
        MessageUtil.send(player, "");
        MessageUtil.send(player, "<yellow>Use <white>/rebirth confirm</white> to rebirth.");
    }

    public void rebirth(Player player) {
        if (!plugin.getConfig().getBoolean("rebirth.enabled", true)) {
            MessageUtil.send(player, "<red>Rebirths are currently disabled.");
            return;
        }

        PlayerProfile profile = profileManager.getProfile(player);
        int maxRank = profileManager.getMaxRank();

        if (!profile.canRebirth(maxRank)) {
            MessageUtil.send(player, "<red>You must reach rank <white>" + maxRank + "</white><red> before rebirthing.");
            return;
        }

        BigInteger tokenCost = getTokenCost(profile);
        BigInteger gemCost = getGemCost(profile);

        if (profile.getTokens().compareTo(tokenCost) < 0) {
            MessageUtil.send(player, "<red>You need <yellow>" + format(tokenCost) + "</yellow><red> tokens to rebirth.");
            return;
        }

        if (profile.getGems().compareTo(gemCost) < 0) {
            MessageUtil.send(player, "<red>You need <light_purple>" + format(gemCost) + "</light_purple><red> gems to rebirth.");
            return;
        }

        profile.removeTokens(tokenCost);
        profile.removeGems(gemCost);
        profile.setRebirth(profile.getRebirth() + 1);

        if (plugin.getConfig().getBoolean("rebirth.reset-rank", true)) {
            int rankAfterRebirth = plugin.getConfig().getInt("rebirth.rank-after-rebirth", profileManager.getStartingRank());
            profile.setRank(rankAfterRebirth, maxRank);
        }

        if (plugin.getConfig().getBoolean("rebirth.reset-blocks-mined", false)) {
            profile.setBlocksMined(0);
            profile.setRawBlocksMined(0);
            profile.resetRankProgressBlocks();
        }

        profileManager.saveProfile(profile);

        MessageUtil.send(player, "");
        MessageUtil.send(player, "<gradient:#00CFFF:#0066FF><bold>REBIRTH COMPLETE!</bold></gradient>");
        MessageUtil.send(player, "<gray>You are now Rebirth <light_purple>" + profile.getRebirth() + "</light_purple><gray>.");
        MessageUtil.send(player, "<gray>Spent <yellow>" + format(tokenCost) + "</yellow><gray> tokens and <light_purple>" + format(gemCost) + "</light_purple><gray> gems.");

        playSound(player);
    }

    private void playSound(Player player) {
        if (!plugin.getConfig().getBoolean("sounds.rebirth.enabled", true)) {
            return;
        }

        String soundName = plugin.getConfig().getString("sounds.rebirth.sound", "UI_TOAST_CHALLENGE_COMPLETE");
        float volume = (float) plugin.getConfig().getDouble("sounds.rebirth.volume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("sounds.rebirth.pitch", 1.1);

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            // Invalid sound in config.
        }
    }

    private String format(BigInteger number) {
        return NumberFormat.getNumberInstance(Locale.US).format(number);
    }
}