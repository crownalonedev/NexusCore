package dev.alone.nexusCore.commands;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.menus.ProfileMenu;
import dev.alone.nexusCore.profiles.PlayerProfile;
import dev.alone.nexusCore.utils.MessageUtil;
import dev.alone.nexusCore.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class NexusCoreCommand implements CommandExecutor, TabCompleter {

    private final NexusCore plugin;

    public NexusCoreCommand(NexusCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("version")) {
            handleVersion(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("profile")) {
            handleProfile(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("currency")) {
            handleCurrency(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("setrank")) {
            handleSetRank(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("setrebirth")) {
            handleSetRebirth(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("setascension")) {
            handleSetAscension(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("addblocks")) {
            handleAddBlocks(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("resetprogress")) {
            handleResetProgress(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("saveprofiles")) {
            handleSaveProfiles(sender);
            return true;
        }

        MessageUtil.sendWithPrefix(sender, "<red>Unknown command. Use <white>/nexuscore help<red>.");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.send(sender, "");
        MessageUtil.send(sender, "<gradient:#00CFFF:#0066FF><bold>NexusCore Commands</bold></gradient>");
        MessageUtil.send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.send(sender, "<aqua>/nexuscore help <gray>- View command help.");
        MessageUtil.send(sender, "<aqua>/nexuscore version <gray>- View plugin version.");
        MessageUtil.send(sender, "<aqua>/settings <gray>- Open your player settings.");
        MessageUtil.send(sender, "<aqua>/balance <gray>- View your balances.");
        MessageUtil.send(sender, "<aqua>/pickaxe <gray>- Sync your Nexus Pickaxe.");
        MessageUtil.send(sender, "<aqua>/enchants <gray>- Open your pickaxe enchant menu.");
        MessageUtil.send(sender, "<aqua>/rebirth <gray>- Rebirth after reaching max rank.");
        MessageUtil.send(sender, "<aqua>/ascend <gray>- Ascend after enough rebirths.");

        if (sender.hasPermission("nexuscore.admin")) {
            MessageUtil.send(sender, "");
            MessageUtil.send(sender, "<gradient:#00CFFF:#0066FF><bold>Admin Commands</bold></gradient>");
            MessageUtil.send(sender, "<aqua>/nexuscore reload <gray>- Reload plugin configuration.");
            MessageUtil.send(sender, "<aqua>/nexuscore profile <player> <gray>- Open a player's profile menu.");
            MessageUtil.send(sender, "<aqua>/nexuscore setrank <player> <rank> <gray>- Set a player's rank.");
            MessageUtil.send(sender, "<aqua>/nexuscore setrebirth <player> <amount> <gray>- Set a player's rebirths.");
            MessageUtil.send(sender, "<aqua>/nexuscore setascension <player> <amount> <gray>- Set a player's ascensions.");
            MessageUtil.send(sender, "<aqua>/nexuscore addblocks <player> <amount> <gray>- Add mining progress blocks.");
            MessageUtil.send(sender, "<aqua>/nexuscore resetprogress <player> <gray>- Reset rank, rebirths, and ascensions.");
            MessageUtil.send(sender, "<aqua>/nexuscore currency set <player> <currency> <amount> <gray>- Set currency.");
            MessageUtil.send(sender, "<aqua>/nexuscore currency add <player> <currency> <amount> <gray>- Add currency.");
            MessageUtil.send(sender, "<aqua>/nexuscore currency take <player> <currency> <amount> <gray>- Take currency.");
            MessageUtil.send(sender, "<aqua>/nexuscore currency reset <player> <currency> <gray>- Reset currency.");
            MessageUtil.send(sender, "<aqua>/nexuscore saveprofiles <gray>- Save all loaded profiles.");
        }

        MessageUtil.send(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.send(sender, "");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("nexuscore.admin")) {
            MessageUtil.sendWithPrefix(sender, MessageUtil.getMessage("messages.no-permission"));
            return;
        }

        plugin.reloadPlugin();

        MessageUtil.sendWithPrefix(sender, MessageUtil.getMessage("messages.reload"));

        if (sender instanceof Player player) {
            SoundUtil.play(player, "sounds.reload");
        }
    }

    private void handleVersion(CommandSender sender) {
        MessageUtil.sendWithPrefix(
                sender,
                "<gray>Running <gradient:#00CFFF:#0066FF><bold>NexusCore</bold></gradient> <white>v"
                        + plugin.getDescription().getVersion()
                        + "<gray>."
        );
    }

    private void handleProfile(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexuscore.admin")) {
            MessageUtil.sendWithPrefix(sender, MessageUtil.getMessage("messages.no-permission"));
            return;
        }

        if (!(sender instanceof Player viewer)) {
            MessageUtil.sendWithPrefix(sender, "<red>Only players can open the profile menu.");
            return;
        }

        if (args.length < 2) {
            MessageUtil.sendWithPrefix(sender, "<red>Usage: <white>/nexuscore profile <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player is not online.");
            return;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(target);

        if (profile == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player's profile is not loaded.");
            return;
        }

        new ProfileMenu(plugin).open(viewer, target, profile);
        SoundUtil.play(viewer, "sounds.menus.open");
    }

    private void handleCurrency(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }

        if (args.length < 4) {
            sendCurrencyUsage(sender);
            return;
        }

        String action = args[1].toLowerCase();
        Player target = getTarget(args[2]);

        if (target == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player is not online.");
            return;
        }

        String currency = args[3].toLowerCase();

        if (!plugin.getCurrencyManager().isValidCurrency(currency)) {
            MessageUtil.sendWithPrefix(sender, "<red>Invalid currency. Use <white>money<red>, <white>tokens<red>, <white>gems<red>, or <white>beacons<red>.");
            return;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(target);

        if (profile == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player's profile is not loaded.");
            return;
        }

        if (action.equals("reset")) {
            plugin.getCurrencyManager().resetCurrency(profile, currency);
            plugin.getProfileManager().saveProfile(target.getUniqueId());

            MessageUtil.sendWithPrefix(sender, "<green>Reset <aqua>" + target.getName() + "<green>'s <white>" + plugin.getCurrencyManager().getDisplayName(currency) + "<green>.");
            MessageUtil.sendWithPrefix(target, "<gray>Your <white>" + plugin.getCurrencyManager().getDisplayName(currency) + " <gray>has been reset.");
            return;
        }

        if (!action.equals("set") && !action.equals("add") && !action.equals("take")) {
            sendCurrencyUsage(sender);
            return;
        }

        if (args.length < 5) {
            MessageUtil.sendWithPrefix(sender, "<red>You must enter an amount.");
            return;
        }

        BigDecimal amount = plugin.getCurrencyManager().parseAmount(args[4]);

        if (amount == null) {
            MessageUtil.sendWithPrefix(sender, "<red>Invalid amount. Examples: <white>1000<red>, <white>25k<red>, <white>1.5m<red>.");
            return;
        }

        switch (action) {
            case "set" -> plugin.getCurrencyManager().setCurrency(profile, currency, amount);
            case "add" -> plugin.getCurrencyManager().addCurrency(profile, currency, amount);
            case "take" -> plugin.getCurrencyManager().takeCurrency(profile, currency, amount);
            default -> {
                sendCurrencyUsage(sender);
                return;
            }
        }

        plugin.getProfileManager().saveProfile(target.getUniqueId());

        String formatted = plugin.getCurrencyManager().formatCurrency(profile, currency);
        String displayName = plugin.getCurrencyManager().getDisplayName(currency);

        MessageUtil.sendWithPrefix(sender, "<green>Updated <aqua>" + target.getName() + "<green>'s <white>" + displayName + "<green>. New balance: <white>" + formatted + "<green>.");
        MessageUtil.sendWithPrefix(target, "<gray>Your <white>" + displayName + " <gray>balance is now <white>" + formatted + "<gray>.");
    }

    private void sendCurrencyUsage(CommandSender sender) {
        MessageUtil.sendWithPrefix(sender, "<red>Usage: <white>/nexuscore currency <set/add/take/reset> <player> <money/tokens/gems/beacons> [amount]");
    }

    private void handleSetRank(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }

        if (args.length < 3) {
            MessageUtil.sendWithPrefix(sender, "<red>Usage: <white>/nexuscore setrank <player> <rank>");
            return;
        }

        Player target = getTarget(args[1]);

        if (target == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player is not online.");
            return;
        }

        Integer rank = parseInteger(args[2]);

        if (rank == null) {
            MessageUtil.sendWithPrefix(sender, "<red>Rank must be a number.");
            return;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(target);

        if (profile == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player's profile is not loaded.");
            return;
        }

        profile.setRank(rank, plugin.getProfileManager().getMaxRank());
        profile.resetRankProgressBlocks();
        plugin.getProfileManager().saveProfile(target.getUniqueId());

        MessageUtil.sendWithPrefix(sender, "<green>Set <aqua>" + target.getName() + "<green>'s rank to <white>" + profile.getRank() + "<green>.");
        MessageUtil.sendWithPrefix(target, "<gray>Your rank has been set to <aqua>" + profile.getRank() + "<gray>.");
    }

    private void handleSetRebirth(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }

        if (args.length < 3) {
            MessageUtil.sendWithPrefix(sender, "<red>Usage: <white>/nexuscore setrebirth <player> <amount>");
            return;
        }

        Player target = getTarget(args[1]);

        if (target == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player is not online.");
            return;
        }

        Integer rebirth = parseInteger(args[2]);

        if (rebirth == null) {
            MessageUtil.sendWithPrefix(sender, "<red>Rebirth amount must be a number.");
            return;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(target);

        if (profile == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player's profile is not loaded.");
            return;
        }

        profile.setRebirth(rebirth);
        plugin.getProfileManager().saveProfile(target.getUniqueId());

        MessageUtil.sendWithPrefix(sender, "<green>Set <aqua>" + target.getName() + "<green>'s rebirths to <white>" + profile.getRebirth() + "<green>.");
        MessageUtil.sendWithPrefix(target, "<gray>Your rebirths have been set to <light_purple>" + profile.getRebirth() + "<gray>.");
    }

    private void handleSetAscension(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }

        if (args.length < 3) {
            MessageUtil.sendWithPrefix(sender, "<red>Usage: <white>/nexuscore setascension <player> <amount>");
            return;
        }

        Player target = getTarget(args[1]);

        if (target == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player is not online.");
            return;
        }

        Integer ascension = parseInteger(args[2]);

        if (ascension == null) {
            MessageUtil.sendWithPrefix(sender, "<red>Ascension amount must be a number.");
            return;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(target);

        if (profile == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player's profile is not loaded.");
            return;
        }

        profile.setAscension(ascension);
        plugin.getProfileManager().saveProfile(target.getUniqueId());

        MessageUtil.sendWithPrefix(sender, "<green>Set <aqua>" + target.getName() + "<green>'s ascensions to <white>" + profile.getAscension() + "<green>.");
        MessageUtil.sendWithPrefix(target, "<gray>Your ascensions have been set to <gold>" + profile.getAscension() + "<gray>.");
    }

    private void handleAddBlocks(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }

        if (args.length < 3) {
            MessageUtil.sendWithPrefix(sender, "<red>Usage: <white>/nexuscore addblocks <player> <amount>");
            return;
        }

        Player target = getTarget(args[1]);

        if (target == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player is not online.");
            return;
        }

        Long blocks = parseLong(args[2]);

        if (blocks == null || blocks <= 0) {
            MessageUtil.sendWithPrefix(sender, "<red>Block amount must be a positive number.");
            return;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(target);

        if (profile == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player's profile is not loaded.");
            return;
        }

        plugin.getProgressionManager().addMiningProgress(target, profile, blocks);
        plugin.getProfileManager().saveProfile(target.getUniqueId());

        MessageUtil.sendWithPrefix(sender, "<green>Added <white>" + blocks + " <green>mining progress blocks to <aqua>" + target.getName() + "<green>.");
    }

    private void handleResetProgress(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }

        if (args.length < 2) {
            MessageUtil.sendWithPrefix(sender, "<red>Usage: <white>/nexuscore resetprogress <player>");
            return;
        }

        Player target = getTarget(args[1]);

        if (target == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player is not online.");
            return;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(target);

        if (profile == null) {
            MessageUtil.sendWithPrefix(sender, "<red>That player's profile is not loaded.");
            return;
        }

        profile.setRank(plugin.getProfileManager().getStartingRank(), plugin.getProfileManager().getMaxRank());
        profile.setPrestige(0);
        profile.setRebirth(0);
        profile.setAscension(0);
        profile.resetRankProgressBlocks();

        plugin.getProfileManager().saveProfile(target.getUniqueId());

        MessageUtil.sendWithPrefix(sender, "<green>Reset progression for <aqua>" + target.getName() + "<green>.");
        MessageUtil.sendWithPrefix(target, "<gray>Your progression has been reset.");
    }

    private void handleSaveProfiles(CommandSender sender) {
        if (!checkAdmin(sender)) {
            return;
        }

        plugin.getProfileManager().saveAllProfiles();
        MessageUtil.sendWithPrefix(sender, "<green>All loaded profiles have been saved.");
    }

    private boolean checkAdmin(CommandSender sender) {
        if (!sender.hasPermission("nexuscore.admin")) {
            MessageUtil.sendWithPrefix(sender, MessageUtil.getMessage("messages.no-permission"));
            return false;
        }

        return true;
    }

    private Player getTarget(String name) {
        return Bukkit.getPlayerExact(name);
    }

    private Integer parseInteger(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long parseLong(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();

            completions.add("help");
            completions.add("version");

            if (sender.hasPermission("nexuscore.admin")) {
                completions.add("reload");
                completions.add("profile");
                completions.add("currency");
                completions.add("setrank");
                completions.add("setrebirth");
                completions.add("setascension");
                completions.add("addblocks");
                completions.add("resetprogress");
                completions.add("saveprofiles");
            }

            return filter(completions, args[0]);
        }

        if (args[0].equalsIgnoreCase("currency")) {
            return completeCurrencyCommand(sender, args);
        }

        if (args.length == 2 && isPlayerArgumentCommand(args[0])) {
            if (!sender.hasPermission("nexuscore.admin")) {
                return List.of();
            }

            return getOnlinePlayerNames(args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setrank")) {
            return filter(List.of("1", "100", "250", "500", "1000"), args[2]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setrebirth")) {
            return filter(List.of("0", "1", "5", "10", "25", "50", "100"), args[2]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setascension")) {
            return filter(List.of("0", "1", "5", "10", "25", "50", "100"), args[2]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("addblocks")) {
            return filter(List.of("100", "1000", "10000", "100000", "1m"), args[2]);
        }

        return List.of();
    }

    private List<String> completeCurrencyCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexuscore.admin")) {
            return List.of();
        }

        if (args.length == 2) {
            return filter(List.of("set", "add", "take", "reset"), args[1]);
        }

        if (args.length == 3) {
            return getOnlinePlayerNames(args[2]);
        }

        if (args.length == 4) {
            return filter(plugin.getCurrencyManager().getCurrencyNames(), args[3]);
        }

        if (args.length == 5 && !args[1].equalsIgnoreCase("reset")) {
            return filter(List.of("1", "10", "100", "1k", "10k", "100k", "1m", "10m", "100m", "1b"), args[4]);
        }

        return List.of();
    }

    private boolean isPlayerArgumentCommand(String commandName) {
        return commandName.equalsIgnoreCase("profile")
                || commandName.equalsIgnoreCase("setrank")
                || commandName.equalsIgnoreCase("setrebirth")
                || commandName.equalsIgnoreCase("setascension")
                || commandName.equalsIgnoreCase("addblocks")
                || commandName.equalsIgnoreCase("resetprogress");
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lowerInput = input.toLowerCase();

        for (String option : options) {
            if (option.toLowerCase().startsWith(lowerInput)) {
                result.add(option);
            }
        }

        return result;
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