package dev.alone.nexusCore.managers;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.profiles.PlayerProfile;
import dev.alone.nexusCore.utils.MessageUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScoreboardManager {

    private static final String[] ENTRIES = {
            ChatColor.BLACK.toString(),
            ChatColor.DARK_BLUE.toString(),
            ChatColor.DARK_GREEN.toString(),
            ChatColor.DARK_AQUA.toString(),
            ChatColor.DARK_RED.toString(),
            ChatColor.DARK_PURPLE.toString(),
            ChatColor.GOLD.toString(),
            ChatColor.GRAY.toString(),
            ChatColor.DARK_GRAY.toString(),
            ChatColor.BLUE.toString(),
            ChatColor.GREEN.toString(),
            ChatColor.AQUA.toString(),
            ChatColor.RED.toString(),
            ChatColor.LIGHT_PURPLE.toString(),
            ChatColor.YELLOW.toString()
    };

    private final NexusCore plugin;
    private final Map<java.util.UUID, SidebarBoard> boards = new HashMap<>();
    private BukkitTask updateTask;

    public ScoreboardManager(NexusCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!isEnabled()) {
            return;
        }

        stopTask();

        for (Player player : Bukkit.getOnlinePlayers()) {
            createBoard(player);
        }

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    public void reload() {
        shutdown();
        start();
    }

    public void shutdown() {
        stopTask();

        org.bukkit.scoreboard.ScoreboardManager bukkitScoreboardManager = Bukkit.getScoreboardManager();

        if (bukkitScoreboardManager != null) {
            Scoreboard mainScoreboard = bukkitScoreboardManager.getMainScoreboard();

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setScoreboard(mainScoreboard);
            }
        }

        boards.clear();
    }

    public void createBoard(Player player) {
        if (player == null || !player.isOnline() || !isEnabled()) {
            return;
        }

        org.bukkit.scoreboard.ScoreboardManager bukkitScoreboardManager = Bukkit.getScoreboardManager();

        if (bukkitScoreboardManager == null) {
            return;
        }

        Scoreboard scoreboard = bukkitScoreboardManager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("nexuscore", "dummy", formatComponent(getTitle()));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Team[] teams = new Team[ENTRIES.length];

        for (int i = 0; i < ENTRIES.length; i++) {
            Team team = scoreboard.registerNewTeam("line_" + i);
            team.addEntry(ENTRIES[i]);
            teams[i] = team;
        }

        SidebarBoard board = new SidebarBoard(scoreboard, objective, teams);
        boards.put(player.getUniqueId(), board);

        player.setScoreboard(scoreboard);
        updatePlayer(player);
    }

    public void removeBoard(java.util.UUID uuid) {
        boards.remove(uuid);
    }

    public void updateAll() {
        if (!isEnabled()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    public void updatePlayer(Player player) {
        if (player == null || !player.isOnline() || !isEnabled()) {
            return;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        if (profile == null) {
            return;
        }

        SidebarBoard board = boards.get(player.getUniqueId());

        if (board == null) {
            createBoard(player);
            board = boards.get(player.getUniqueId());
        }

        if (board == null) {
            return;
        }

        board.objective.displayName(formatComponent(getTitle()));

        List<Component> lines = buildLines(player, profile);
        board.update(lines);
    }

    private List<Component> buildLines(Player player, PlayerProfile profile) {
        List<String> template = getTemplate(profile);
        List<Component> processed = new ArrayList<>();

        for (String line : template) {
            processed.add(formatComponent(applyPlaceholders(player, profile, line)));
        }

        return processed;
    }

    private List<String> getTemplate(PlayerProfile profile) {
        String path = "scoreboard.templates.rank";

        int maxRank = plugin.getProfileManager().getMaxRank();

        if (profile.getAscension() > 0) {
            path = "scoreboard.templates.ascension";
        } else if (profile.getRebirth() > 0) {
            path = "scoreboard.templates.rebirth";
        } else if (profile.isMaxRank(maxRank)) {
            path = "scoreboard.templates.max-rank";
        }

        List<String> lines = plugin.getConfig().getStringList(path);

        if (!lines.isEmpty()) {
            return lines;
        }

        return getFallbackTemplate(profile);
    }

    private List<String> getFallbackTemplate(PlayerProfile profile) {
        List<String> lines = new ArrayList<>();

        lines.add("<gradient:#00CFFF:#0066FF><bold>%player_name%</bold></gradient>");

        if (profile.getAscension() > 0) {
            lines.add("<white>Rebirth: <light_purple>%rebirth%");
            lines.add("<white>Ascension: <gold>%ascension%");
        } else if (profile.getRebirth() > 0) {
            lines.add("<white>Rebirth: <light_purple>%rebirth%");
        } else if (profile.isMaxRank(plugin.getProfileManager().getMaxRank())) {
            lines.add("<light_purple><bold>Rebirth Ready!</bold></light_purple>");
            lines.add("<white>Progress: <green>%rebirth_progress%</green><gray>/</gray><green>%rebirth_requirement%");
        } else {
            lines.add("<white>Rank: <aqua>%rank_raw%");
        }

        lines.add("<white>Blocks Mined: <green>%blocks_mined%");
        lines.add("<white>Gang: <aqua>%gang%");
        lines.add("");
        lines.add("<gradient:#00CFFF:#0066FF><bold>Balance:</bold></gradient>");
        lines.add("<white>Money: <green>%money%");
        lines.add("<white>Tokens: <yellow>%tokens%");
        lines.add("<white>Gems: <light_purple>%gems%");
        lines.add("<white>Beacons: <aqua>%beacons%");
        lines.add(" ");
        lines.add("<gray>%server_ip%");

        return lines;
    }

    private String applyPlaceholders(Player player, PlayerProfile profile, String text) {
        if (text == null) {
            return "";
        }

        String replaced = text
                .replace("%player%", player.getName())
                .replace("%player_name%", player.getName())

                .replace("%rank%", String.valueOf(profile.getRank()))
                .replace("%rank_raw%", String.valueOf(profile.getRank()))
                .replace("%max_rank%", String.valueOf(plugin.getProfileManager().getMaxRank()))

                .replace("%rebirth%", "R" + profile.getRebirth())
                .replace("%rebirth_raw%", String.valueOf(profile.getRebirth()))

                .replace("%ascension%", "A" + profile.getAscension())
                .replace("%ascension_raw%", String.valueOf(profile.getAscension()))

                .replace("%blocks_mined%", String.valueOf(profile.getBlocksMined()))
                .replace("%raw_blocks_mined%", String.valueOf(profile.getRawBlocksMined()))

                .replace("%gang%", plugin.getConfig().getString("scoreboard.default-gang", "None"))
                .replace("%server_ip%", plugin.getConfig().getString("scoreboard.server-ip", "play.nexusnetwork.net"))

                .replace("%money%", plugin.getCurrencyManager().formatMoney(profile))
                .replace("%tokens%", plugin.getCurrencyManager().formatTokens(profile))
                .replace("%gems%", plugin.getCurrencyManager().formatGems(profile))
                .replace("%beacons%", plugin.getCurrencyManager().formatBeacons(profile))

                .replace("%rebirth_progress%", String.valueOf(profile.getRankProgressBlocks()))
                .replace("%rebirth_requirement%", String.valueOf(plugin.getProgressionManager().getBlocksRequiredForNextRebirth(profile)))
                .replace("%rebirth_remaining%", String.valueOf(plugin.getProgressionManager().getBlocksRemainingForNextRebirth(profile)))

                .replace("%current_mine%", profile.getCurrentMine())
                .replace("%pickaxe_level%", String.valueOf(profile.getPickaxeLevel()))
                .replace("%pickaxe_xp%", String.valueOf(profile.getPickaxeXp()));

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            replaced = PlaceholderAPI.setPlaceholders(player, replaced);
        }

        return replaced;
    }

    private String getTitle() {
        return plugin.getConfig().getString(
                "scoreboard.title",
                "<gradient:#00CFFF:#0066FF><bold>Nexus Prison</bold></gradient>"
        );
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("scoreboard.enabled", true);
    }

    private Component formatComponent(String text) {
        return MessageUtil.color(text == null ? "" : text);
    }

    private void stopTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private static final class SidebarBoard {

        private final Scoreboard scoreboard;
        private final Objective objective;
        private final Team[] teams;

        private SidebarBoard(Scoreboard scoreboard, Objective objective, Team[] teams) {
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.teams = teams;
        }

        private void update(List<Component> lines) {
            int maxLines = Math.min(lines.size(), ENTRIES.length);

            for (int i = 0; i < ENTRIES.length; i++) {
                String entry = ENTRIES[i];
                Team team = teams[i];

                if (i < maxLines) {
                    Component line = lines.get(i);

                    team.prefix(line);
                    team.suffix(Component.empty());
                    objective.getScore(entry).setScore(maxLines - i);
                } else {
                    scoreboard.resetScores(entry);
                    team.prefix(Component.empty());
                    team.suffix(Component.empty());
                }
            }
        }
    }
}