package dev.alone.nexusCore.commands;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.managers.PickaxeEnchant;
import dev.alone.nexusCore.managers.PickaxeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

public class EnchantsCommand implements CommandExecutor, TabCompleter {

    private final PickaxeManager pickaxeManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public EnchantsCommand(NexusCore plugin) {
        this.pickaxeManager = plugin.getPickaxeManager();
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            send(sender, "<red>Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("nexuscore.command.enchants")) {
            send(player, "<red>You do not have permission.");
            return true;
        }

        if (args.length == 0) {
            pickaxeManager.openEnchantMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("upgrade")) {
            if (args.length < 2) {
                send(player, "<red>Usage: /enchants upgrade <enchant> [amount]");
                return true;
            }

            PickaxeEnchant enchant = PickaxeEnchant.fromInput(args[1]);

            if (enchant == null) {
                send(player, "<red>Unknown enchant.");
                return true;
            }

            int amount = 1;

            if (args.length >= 3) {
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException exception) {
                    send(player, "<red>Amount must be a number.");
                    return true;
                }
            }

            pickaxeManager.upgradeEnchant(player, enchant, amount);
            return true;
        }

        pickaxeManager.openEnchantMenu(player);
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
            StringUtil.copyPartialMatches(args[0], List.of("upgrade"), completions);
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("upgrade")) {
            List<String> enchants = new ArrayList<>();

            for (PickaxeEnchant enchant : PickaxeEnchant.values()) {
                enchants.add(enchant.getId());
            }

            StringUtil.copyPartialMatches(args[1], enchants, completions);
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("upgrade")) {
            StringUtil.copyPartialMatches(args[2], List.of("1", "5", "10", "25", "50", "100"), completions);
            return completions;
        }

        return completions;
    }

    private void send(CommandSender sender, String message) {
        Component component = miniMessage.deserialize(message).decoration(TextDecoration.ITALIC, false);
        sender.sendMessage(component);
    }
}