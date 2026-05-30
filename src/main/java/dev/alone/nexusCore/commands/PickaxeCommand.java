package dev.alone.nexusCore.commands;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.managers.PickaxeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PickaxeCommand implements CommandExecutor, TabCompleter {

    private final NexusCore plugin;
    private final PickaxeManager pickaxeManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PickaxeCommand(NexusCore plugin) {
        this.plugin = plugin;
        this.pickaxeManager = plugin.getPickaxeManager();
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, "<red>Only players can use this command.");
                return true;
            }

            pickaxeManager.syncPickaxe(player);
            send(player, "<green>Your Nexus Pickaxe has been synced.");
            return true;
        }

        if (args[0].equalsIgnoreCase("slot")) {
            if (!(sender instanceof Player player)) {
                send(sender, "<red>Only players can use this command.");
                return true;
            }

            pickaxeManager.openSlotMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("enchants")) {
            if (!(sender instanceof Player player)) {
                send(sender, "<red>Only players can use this command.");
                return true;
            }

            pickaxeManager.openEnchantMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("nexuscore.pickaxe.give")) {
                send(sender, "<red>You do not have permission.");
                return true;
            }

            if (args.length < 2) {
                send(sender, "<red>Usage: /pickaxe give <player>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);

            if (target == null) {
                send(sender, "<red>That player is not online.");
                return true;
            }

            pickaxeManager.syncPickaxe(target);
            send(sender, "<green>Synced Nexus Pickaxe for <aqua>" + target.getName() + "</aqua><green>.");
            return true;
        }

        send(sender, "<red>Usage: /pickaxe, /pickaxe menu, /pickaxe enchants, /pickaxe slot, /pickaxe give <player>");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], List.of("menu", "enchants", "slot", "give"), completions);
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> players = new ArrayList<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                players.add(player.getName());
            }

            StringUtil.copyPartialMatches(args[1], players, completions);
            return completions;
        }

        return completions;
    }

    private void send(CommandSender sender, String message) {
        Component component = miniMessage.deserialize(message).decoration(TextDecoration.ITALIC, false);
        sender.sendMessage(component);
    }
}