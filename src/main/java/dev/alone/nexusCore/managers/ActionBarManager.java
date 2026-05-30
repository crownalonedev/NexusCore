package dev.alone.nexusCore.managers;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.profiles.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;

public final class ActionBarManager {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final NexusCore plugin;
    private BukkitTask task;

    public ActionBarManager(NexusCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        if (!plugin.getConfig().getBoolean("action-bar.enabled", true)) {
            return;
        }

        long interval = Math.max(5L, plugin.getConfig().getLong("action-bar.update-interval-ticks", 20L));

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sendActionBars, 20L, interval);
    }

    public void restart() {
        stop();
        start();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void sendActionBars() {
        if (!plugin.getConfig().getBoolean("action-bar.enabled", true)) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isAllowedWorld(player)) {
                continue;
            }

            PlayerProfile profile = plugin.getProfileManager().getProfile(player);

            if (profile == null) {
                continue;
            }

            player.sendActionBar(buildActionBar(profile));
        }
    }

    private Component buildActionBar(PlayerProfile profile) {
        if (profile.getRebirth() <= 0) {
            return buildRankupActionBar(profile);
        }

        return buildRebirthActionBar(profile);
    }

    private Component buildRankupActionBar(PlayerProfile profile) {
        int maxRank = plugin.getProfileManager().getMaxRank();

        if (profile.isMaxRank(maxRank)) {
            String format = plugin.getConfig().getString(
                    "action-bar.formats.ready-rebirth",
                    "<gradient:#00CFFF:#0066FF><bold>MAX RANK</bold></gradient> <dark_gray>┃</dark_gray> %bar% <gray>100%</gray> <dark_gray>┃</dark_gray> <#FCFC54>Ready for /rebirth</#FCFC54>"
            );

            return MINI_MESSAGE.deserialize(format.replace("%bar%", buildBar(1.0D)));
        }

        long current = profile.getRankProgressBlocks();
        long required = plugin.getProgressionManager().getBlocksRequiredForNextRank(profile);
        double progress = getProgress(current, required);

        String format = plugin.getConfig().getString(
                "action-bar.formats.rankup",
                "<gradient:#00CFFF:#0066FF><bold>RANKUP</bold></gradient> <dark_gray>┃</dark_gray> <white>Rank <#00CFFF>%current%</#00CFFF> <gray>➜</gray> <#0066FF>%next%</#0066FF> <dark_gray>┃</dark_gray> %bar% <gray>%percent%%</gray> <dark_gray>┃</dark_gray> <#FCFC54>%detail%</#FCFC54>"
        );

        return MINI_MESSAGE.deserialize(
                format
                        .replace("%current%", String.valueOf(profile.getRank()))
                        .replace("%next%", String.valueOf(profile.getNextRank(maxRank)))
                        .replace("%bar%", buildBar(progress))
                        .replace("%percent%", getPercent(progress))
                        .replace("%detail%", formatCompact(current) + " / " + formatCompact(required) + " Blocks")
        );
    }

    private Component buildRebirthActionBar(PlayerProfile profile) {
        int maxRank = plugin.getProfileManager().getMaxRank();

        long current = profile.getRank();
        long required = maxRank;
        double progress = getProgress(current, required);

        String status = profile.isMaxRank(maxRank)
                ? "Ready for /rebirth"
                : "Rank " + formatCompact(current) + " / " + formatCompact(required);

        String format = plugin.getConfig().getString(
                "action-bar.formats.rebirth",
                "<gradient:#00CFFF:#0066FF><bold>REBIRTH</bold></gradient> <dark_gray>┃</dark_gray> <white>RB <#00CFFF>%current_rebirth%</#00CFFF> <gray>➜</gray> <#0066FF>%next_rebirth%</#0066FF> <dark_gray>┃</dark_gray> %bar% <gray>%percent%%</gray> <dark_gray>┃</dark_gray> <#FCFC54>%detail%</#FCFC54>"
        );

        return MINI_MESSAGE.deserialize(
                format
                        .replace("%current_rebirth%", String.valueOf(profile.getRebirth()))
                        .replace("%next_rebirth%", String.valueOf(profile.getRebirth() + 1))
                        .replace("%bar%", buildBar(progress))
                        .replace("%percent%", getPercent(progress))
                        .replace("%detail%", status)
        );
    }

    private String buildBar(double progress) {
        int length = Math.max(5, plugin.getConfig().getInt("action-bar.bar-length", 16));

        String filledSymbol = plugin.getConfig().getString("action-bar.symbols.filled", "▰");
        String emptySymbol = plugin.getConfig().getString("action-bar.symbols.empty", "▱");

        String primary = getColor("action-bar.colors.primary", "#00CFFF");
        String secondary = getColor("action-bar.colors.secondary", "#0066FF");
        String empty = getColor("action-bar.colors.empty", "#1B2A36");

        int filled = (int) Math.round(progress * length);

        if (progress > 0.0D && filled <= 0) {
            filled = 1;
        }

        filled = Math.max(0, Math.min(length, filled));

        String filledPart = filledSymbol.repeat(filled);
        String emptyPart = emptySymbol.repeat(length - filled);

        return "<gradient:" + primary + ":" + secondary + ">" + filledPart + "</gradient>" +
                "<color:" + empty + ">" + emptyPart + "</color>";
    }

    private double getProgress(long current, long required) {
        if (required <= 0L) {
            return 1.0D;
        }

        return Math.max(0.0D, Math.min(1.0D, (double) current / (double) required));
    }

    private String getPercent(double progress) {
        return String.valueOf((int) Math.round(progress * 100.0D));
    }

    private String getColor(String path, String fallback) {
        String color = plugin.getConfig().getString(path, fallback);

        if (color == null || !color.matches("#[A-Fa-f0-9]{6}")) {
            return fallback;
        }

        return color;
    }

    private boolean isAllowedWorld(Player player) {
        List<String> worlds = plugin.getConfig().getStringList("action-bar.worlds");

        if (worlds.isEmpty()) {
            return true;
        }

        return worlds.contains(player.getWorld().getName());
    }

    private String formatCompact(long value) {
        double number = value;
        String[] suffixes = {"", "K", "M", "B", "T", "Q"};

        int suffixIndex = 0;

        while (Math.abs(number) >= 1000.0D && suffixIndex < suffixes.length - 1) {
            number /= 1000.0D;
            suffixIndex++;
        }

        if (suffixIndex == 0) {
            return String.valueOf(value);
        }

        if (Math.abs(number) >= 100.0D || number == Math.floor(number)) {
            return String.format(Locale.US, "%.0f%s", number, suffixes[suffixIndex]);
        }

        return String.format(Locale.US, "%.1f%s", number, suffixes[suffixIndex]);
    }
}