package dev.alone.nexusCore.commands;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.profiles.PlayerProfile;
import dev.alone.nexusCore.utils.MessageUtil;
import dev.alone.nexusCore.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class CurrencyCommand implements CommandExecutor, TabCompleter {

    private final NexusCore plugin;

    public CurrencyCommand(NexusCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nexuscore.command.balance")) {
            MessageUtil.sendWithPrefix(sender, MessageUtil.getMessage("messages.no-permission"));
            return true;
        }

        Player target;

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                MessageUtil.sendWithPrefix(sender, MessageUtil.getMessage("messages.player-only"));
                return true;
            }

            target = player;
        } else {
            if (!sender.hasPermission("nexuscore.command.balance.others")) {
                MessageUtil.sendWithPrefix(sender, MessageUtil.getMessage("messages.no-permission"));
                return true;
            }

            target = Bukkit.getPlayerExact(args[0]);

            if (target == null) {
                MessageUtil.sendWithPrefix(sender, "<red>That player is not online.");
                return true;
            }
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(target);

        if (profile == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player's profile is not loaded.");
            return true;
        }

        sendBalance(sender, target, profile);

        if (sender instanceof Player player) {
            SoundUtil.play(player, "sounds.menus.open");
        }

        return true;
    }

    private void sendBalance(CommandSender sender, Player target, PlayerProfile profile) {
        MessageUtil.send(sender, "");
        MessageUtil.send(sender, "<gradient:#00CFFF:#0066FF><bold>Nexus Balances</bold></gradient> <dark_gray>| <white>" + target.getName());
        MessageUtil.send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.send(sender, "<green>Money: <white>" + plugin.getCurrencyManager().formatMoney(profile));
        MessageUtil.send(sender, "<yellow>Tokens: <white>" + plugin.getCurrencyManager().formatTokens(profile));
        MessageUtil.send(sender, "<light_purple>Gems: <white>" + plugin.getCurrencyManager().formatGems(profile));

        boolean showBeacons = plugin.getConfig().getBoolean("currencies.beacons.show-in-balance", false)
                || sender.hasPermission("nexuscore.admin");

        if (showBeacons) {
            MessageUtil.send(sender, "<aqua>Gang Beacons: <white>" + plugin.getCurrencyManager().formatBeacons(profile) + " <dark_gray>(Gang Top)");
        } else {
            MessageUtil.send(sender, "<aqua>Gang Beacons: <dark_gray>Gang Top Only");
        }

        MessageUtil.send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.send(sender, "");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("nexuscore.command.balance.others")) {
            return getOnlinePlayerNames(args[0]);
        }

        return List.of();
    }

    private List<String> getOnlinePlayerNames(String input) {
        List<String> names = new ArrayList<>();
        String lowerInput = input.toLowerCase();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(lowerInput)) {
                names.add(player.getName());
            }
        }

        return names;
    }
}