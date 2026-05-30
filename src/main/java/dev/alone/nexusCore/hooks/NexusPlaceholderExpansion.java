package dev.alone.nexusCore.hooks;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.profiles.PlayerProfile;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class NexusPlaceholderExpansion extends PlaceholderExpansion {

    private final NexusCore plugin;

    public NexusPlaceholderExpansion(NexusCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nexuscore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Alone";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        if (profile == null) {
            return "";
        }

        String placeholder = params.toLowerCase(Locale.ROOT);

        return switch (placeholder) {
            case "rank" -> String.valueOf(profile.getRank());
            case "rank_colored" -> plugin.getProgressionManager().getColoredRank(profile);
            case "rank_color" -> plugin.getProgressionManager().getRankColor(profile.getRank());
            case "max_rank" -> String.valueOf(plugin.getProfileManager().getMaxRank());
            case "next_rank" -> String.valueOf(profile.getNextRank(plugin.getProfileManager().getMaxRank()));

            case "rebirth" -> "R" + profile.getRebirth();
            case "rebirth_raw" -> String.valueOf(profile.getRebirth());
            case "rebirth_colored" -> plugin.getProgressionManager().getColoredRebirth(profile);
            case "rebirth_color" -> plugin.getProgressionManager().getRebirthColor(profile.getRebirth());

            case "ascension" -> "A" + profile.getAscension();
            case "ascension_raw" -> String.valueOf(profile.getAscension());
            case "ascension_colored" -> plugin.getProgressionManager().getColoredAscension(profile);
            case "ascension_color" -> plugin.getProgressionManager().getAscensionColor(profile.getAscension());

            case "chat_tag" -> plugin.getProgressionManager().getChatTag(profile);

            case "money" -> profile.getMoney().toPlainString();
            case "money_formatted" -> plugin.getCurrencyManager().formatMoney(profile);

            case "tokens" -> profile.getTokens().toString();
            case "tokens_formatted" -> plugin.getCurrencyManager().formatTokens(profile);

            case "gems" -> profile.getGems().toString();
            case "gems_formatted" -> plugin.getCurrencyManager().formatGems(profile);

            case "beacons" -> profile.getBeacons().toString();
            case "beacons_formatted" -> plugin.getCurrencyManager().formatBeacons(profile);
            case "beacons_note" -> "Gang Top Only";

            case "gang", "gang_name" -> plugin.getConfig().getString("scoreboard.default-gang", "None");

            case "blocks_mined" -> String.valueOf(profile.getBlocksMined());
            case "raw_blocks_mined" -> String.valueOf(profile.getRawBlocksMined());

            case "rank_progress_blocks" -> String.valueOf(profile.getRankProgressBlocks());
            case "blocks_required_next_rank" -> String.valueOf(plugin.getProgressionManager().getBlocksRequiredForNextRank(profile));
            case "blocks_remaining_next_rank" -> String.valueOf(plugin.getProgressionManager().getBlocksRemainingForNextRank(profile));
            case "rank_progress_percent" -> String.format(Locale.US, "%.1f", plugin.getProgressionManager().getRankProgressPercent(profile));

            case "blocks_required_rebirth" -> String.valueOf(plugin.getProgressionManager().getBlocksRequiredForNextRebirth(profile));
            case "blocks_remaining_rebirth" -> String.valueOf(plugin.getProgressionManager().getBlocksRemainingForNextRebirth(profile));
            case "can_rebirth" -> plugin.getProgressionManager().canRebirth(profile) ? "Yes" : "No";

            case "rebirths_required_ascension" -> String.valueOf(plugin.getProfileManager().getRebirthsRequiredToAscend());
            case "rebirths_until_ascension" -> String.valueOf(profile.getRebirthsUntilAscension(plugin.getProfileManager().getRebirthsRequiredToAscend()));
            case "can_ascend" -> profile.canAscend(plugin.getProfileManager().getRebirthsRequiredToAscend()) ? "Yes" : "No";

            case "current_mine" -> profile.getCurrentMine();
            case "backpack_size" -> String.valueOf(profile.getBackpackSize());

            case "autosell" -> profile.isAutoSell() ? "Enabled" : "Disabled";
            case "autopickup" -> profile.isAutoPickup() ? "Enabled" : "Disabled";
            case "auto_rebirth" -> profile.isAutoRebirth() ? "Enabled" : "Disabled";
            case "auto_ascension" -> profile.isAutoAscension() ? "Enabled" : "Disabled";

            case "pickaxe_level" -> String.valueOf(profile.getPickaxeLevel());
            case "pickaxe_xp" -> String.valueOf(profile.getPickaxeXp());

            default -> null;
        };
    }
}