package dev.alone.nexusCore.commands;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.menus.SettingsMenu;
import dev.alone.nexusCore.profiles.PlayerProfile;
import dev.alone.nexusCore.utils.MessageUtil;
import dev.alone.nexusCore.utils.SoundUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SettingsCommand implements CommandExecutor {

    private final NexusCore plugin;

    public SettingsCommand(NexusCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nexuscore.command.settings")) {
            MessageUtil.sendWithPrefix(sender, MessageUtil.getMessage("messages.no-permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            MessageUtil.sendWithPrefix(sender, MessageUtil.getMessage("messages.player-only"));
            return true;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(player);

        if (profile == null) {
            MessageUtil.sendWithPrefix(player, "<red>Your profile is not loaded.");
            return true;
        }

        new SettingsMenu(plugin).open(player, profile);
        SoundUtil.play(player, "sounds.menus.open");
        return true;
    }
}