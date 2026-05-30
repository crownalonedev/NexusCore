package dev.alone.nexusCore.commands;

import dev.alone.nexusCore.managers.RebirthManager;
import dev.alone.nexusCore.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RebirthCommand implements CommandExecutor {

    private final RebirthManager rebirthManager;

    public RebirthCommand(RebirthManager rebirthManager) {
        this.rebirthManager = rebirthManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "<red>Only players can use this command.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("confirm")) {
            rebirthManager.rebirth(player);
            return true;
        }

        rebirthManager.sendRebirthInfo(player);
        return true;
    }
}