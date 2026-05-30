package dev.alone.nexusCore.commands;

import dev.alone.nexusCore.utils.MessageUtil;
import dev.alone.nexusCore.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class EssentialCommands implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "fly" -> handleFly(sender, args);
            case "gmc" -> handleGameMode(sender, args, GameMode.CREATIVE, "Creative");
            case "gms" -> handleGameMode(sender, args, GameMode.SURVIVAL, "Survival");
            case "gmsp" -> handleGameMode(sender, args, GameMode.SPECTATOR, "Spectator");
            case "gma" -> handleGameMode(sender, args, GameMode.ADVENTURE, "Adventure");
            default -> MessageUtil.sendWithPrefix(sender, "<red>Unknown essentials command.");
        }

        return true;
    }

    private void handleFly(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexuscore.command.fly")) {
            MessageUtil.sendWithPrefix(sender, MessageUtil.getMessage("messages.no-permission"));
            return;
        }

        Player target = getTargetPlayer(sender, args, "nexuscore.command.fly.others");

        if (target == null) {
            return;
        }

        boolean enabled = !target.getAllowFlight();

        if (enabled) {
            target.setAllowFlight(true);
            target.setFlying(true);

            MessageUtil.sendWithPrefix(target, "<green>Flight has been enabled.");
            SoundUtil.play(target, "sounds.fly.enable");
        } else {
            target.setFlying(false);

            if (target.getGameMode() != GameMode.CREATIVE && target.getGameMode() != GameMode.SPECTATOR) {
                target.setAllowFlight(false);
            }

            MessageUtil.sendWithPrefix(target, "<red>Flight has been disabled.");
            SoundUtil.play(target, "sounds.fly.disable");
        }

        if (!target.equals(sender)) {
            MessageUtil.sendWithPrefix(sender, "<gray>You toggled flight for <aqua>" + target.getName() + "<gray>.");
        }
    }

    private void handleGameMode(CommandSender sender, String[] args, GameMode gameMode, String displayName) {
        if (!sender.hasPermission("nexuscore.command.gamemode")) {
            MessageUtil.sendWithPrefix(sender, MessageUtil.getMessage("messages.no-permission"));
            return;
        }

        Player target = getTargetPlayer(sender, args, "nexuscore.command.gamemode.others");

        if (target == null) {
            return;
        }

        target.setGameMode(gameMode);

        MessageUtil.sendWithPrefix(target, "<gray>Your gamemode has been set to <aqua>" + displayName + "<gray>.");
        SoundUtil.play(target, "sounds.gamemode");

        if (!target.equals(sender)) {
            MessageUtil.sendWithPrefix(sender, "<gray>You set <aqua>" + target.getName() + "<gray>'s gamemode to <aqua>" + displayName + "<gray>.");
        }
    }

    private Player getTargetPlayer(CommandSender sender, String[] args, String othersPermission) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                MessageUtil.sendWithPrefix(sender, MessageUtil.getMessage("messages.player-only"));
                return null;
            }

            return player;
        }

        if (!sender.hasPermission(othersPermission)) {
            MessageUtil.sendWithPrefix(sender, MessageUtil.getMessage("messages.no-permission"));
            return null;
        }

        Player target = Bukkit.getPlayerExact(args[0]);

        if (target == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player is not online.");
            return null;
        }

        return target;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        String commandName = command.getName().toLowerCase();

        if (args.length == 1) {
            if (commandName.equals("fly")) {
                if (!sender.hasPermission("nexuscore.command.fly.others")) {
                    return List.of();
                }

                return getOnlinePlayerNames(args[0]);
            }

            if (isGameModeCommand(commandName)) {
                if (!sender.hasPermission("nexuscore.command.gamemode.others")) {
                    return List.of();
                }

                return getOnlinePlayerNames(args[0]);
            }
        }

        return List.of();
    }

    private boolean isGameModeCommand(String commandName) {
        return commandName.equals("gmc")
                || commandName.equals("gms")
                || commandName.equals("gmsp")
                || commandName.equals("gma");
    }

    private List<String> getOnlinePlayerNames(String input) {
        List<String> names = new ArrayList<>();
        String lowerInput = input.toLowerCase();

        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();

            if (name.toLowerCase().startsWith(lowerInput)) {
                names.add(name);
            }
        }

        return names;
    }
}