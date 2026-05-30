package dev.alone.nexusCore.managers;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.profiles.PlayerProfile;
import dev.alone.nexusCore.profiles.ProfileManager;
import dev.alone.nexusCore.utils.MessageUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.Locale;

public class RebirthManager {

    private final NexusCore plugin;
    private final ProfileManager profileManager;

    public RebirthManager(NexusCore plugin, ProfileManager profileManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
    }

    public long getBlocksRequired(PlayerProfile profile) {
        return plugin.getProgressionManager().getBlocksRequiredForNextRebirth(profile);
    }

    public long getBlocksRemaining(PlayerProfile profile) {
        return plugin.getProgressionManager().getBlocksRemainingForNextRebirth(profile);
    }

    public void sendRebirthInfo(Player player) {
        PlayerProfile profile = profileManager.getProfile(player);
        int maxRank = profileManager.getMaxRank();
        long required = getBlocksRequired(profile);
        long progress = profile.getRankProgressBlocks();
        long remaining = getBlocksRemaining(profile);

        MessageUtil.send(player, "");
        MessageUtil.send(player, "<gradient:#00CFFF:#0066FF><bold>Rebirth</bold></gradient>");
        MessageUtil.send(player, "<gray>Current Rebirth: <aqua>" + profile.getRebirth() + "</aqua>");
        MessageUtil.send(player, "<gray>Next Rebirth: <aqua>" + (profile.getRebirth() + 1) + "</aqua>");
        MessageUtil.send(player, "<gray>Required Rank: <aqua>" + maxRank + "</aqua>");
        MessageUtil.send(player, "");
        MessageUtil.send(player, "<gray>Progress:");
        MessageUtil.send(player, "  <gray>Blocks: <aqua>" + format(Math.min(progress, required)) + "</aqua><dark_gray>/</dark_gray><aqua>" + format(required) + "</aqua>");
        MessageUtil.send(player, "  <gray>Remaining: <yellow>" + format(remaining) + "</yellow>");
        MessageUtil.send(player, "");

        if (!profile.canRebirth(maxRank)) {
            MessageUtil.send(player, "<red>You must reach rank <white>" + maxRank + "</white><red> before rebirthing.");
            return;
        }

        if (plugin.getProgressionManager().canRebirth(profile)) {
            MessageUtil.send(player, "<yellow>Use <white>/rebirth confirm</white> to rebirth now.");
        } else {
            MessageUtil.send(player, "<yellow>Keep mining. Rebirth works like ranking up after rank <white>" + maxRank + "</white><yellow>.");
        }
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

        if (!plugin.getProgressionManager().canRebirth(profile)) {
            MessageUtil.send(player, "<red>You need <yellow>" + format(getBlocksRemaining(profile)) + "</yellow><red> more blocks before rebirthing.");
            return;
        }

        plugin.getProgressionManager().attemptRebirth(player, profile, false);
    }

    public void playSound(Player player) {
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

    private String format(long number) {
        return NumberFormat.getNumberInstance(Locale.US).format(number);
    }
}
