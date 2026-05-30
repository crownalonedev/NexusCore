package dev.alone.nexusCore.commands;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.managers.MineManager;
import dev.alone.nexusCore.managers.PrivateMine;
import dev.alone.nexusCore.menus.MineMenus;
import dev.alone.nexusCore.utils.MineItemUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MineCommand implements CommandExecutor, TabCompleter {

    private final NexusCore plugin;
    private final MineManager mineManager;

    public MineCommand(NexusCore plugin, MineManager mineManager) {
        this.plugin = plugin;
        this.mineManager = mineManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("nexuscore.mine.use")) {
            player.sendMessage(MineItemUtil.component("<red>You do not have permission to use private mines."));
            return true;
        }

        if (!plugin.getConfig().getBoolean("private-mines.enabled", true)) {
            player.sendMessage(MineItemUtil.component("<red>Private mines are currently disabled."));
            return true;
        }

        if (args.length == 0) {
            MineMenus.openMain(plugin, mineManager, player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "go" -> handleGo(player);
            case "reset" -> handleReset(player);
            case "blocks" -> MineMenus.openBlocks(plugin, mineManager, player);
            case "help" -> sendHelp(player, label);
            default -> sendHelp(player, label);
        }

        return true;
    }

    private void handleGo(Player player) {
        PrivateMine mine = mineManager.getOrCreateMine(player);
        mineManager.teleportToMine(player, mine);
    }

    private void handleReset(Player player) {
        PrivateMine mine = mineManager.getOrCreateMine(player);

        mineManager.resetMine(mine);
        mineManager.saveAll();
        mineManager.playConfiguredSound(player, "reset");

        player.sendMessage(MineItemUtil.component("<gradient:#00CFFF:#0066FF><bold>NexusMC</bold></gradient> <dark_gray>»</dark_gray> <green>Your private mine has been reset."));
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(MineItemUtil.component(""));
        player.sendMessage(MineItemUtil.component("<gradient:#0066FF:#00CFFF><bold>Private Mine Commands</bold></gradient>"));
        player.sendMessage(MineItemUtil.component("<gray>/" + label + " <dark_gray>- <white>Open your mine menu"));
        player.sendMessage(MineItemUtil.component("<gray>/" + label + " go <dark_gray>- <white>Teleport to your mine"));
        player.sendMessage(MineItemUtil.component("<gray>/" + label + " reset <dark_gray>- <white>Reset your mine"));
        player.sendMessage(MineItemUtil.component("<gray>/" + label + " blocks <dark_gray>- <white>Change mine blocks"));
        player.sendMessage(MineItemUtil.component("<gray>/" + label + " help <dark_gray>- <white>View mine commands"));
        player.sendMessage(MineItemUtil.component(""));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        if (args.length == 1) {
            completions.add("go");
            completions.add("reset");
            completions.add("blocks");
            completions.add("help");

            return filter(completions, args[0]);
        }

        return completions;
    }

    private List<String> filter(List<String> values, String input) {
        List<String> filtered = new ArrayList<>();

        for (String value : values) {
            if (value.toLowerCase().startsWith(input.toLowerCase())) {
                filtered.add(value);
            }
        }

        return filtered;
    }
}