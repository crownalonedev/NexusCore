package dev.alone.nexusCore;

import dev.alone.nexusCore.commands.AscensionCommand;
import dev.alone.nexusCore.commands.CurrencyCommand;
import dev.alone.nexusCore.commands.EnchantsCommand;
import dev.alone.nexusCore.commands.EssentialCommands;
import dev.alone.nexusCore.commands.MineCommand;
import dev.alone.nexusCore.commands.NexusCoreCommand;
import dev.alone.nexusCore.commands.PickaxeCommand;
import dev.alone.nexusCore.commands.RebirthCommand;
import dev.alone.nexusCore.commands.SettingsCommand;
import dev.alone.nexusCore.hooks.NexusPlaceholderExpansion;
import dev.alone.nexusCore.listeners.ChatListener;
import dev.alone.nexusCore.listeners.MenuListener;
import dev.alone.nexusCore.listeners.MineListener;
import dev.alone.nexusCore.listeners.MineMenuListener;
import dev.alone.nexusCore.listeners.MiningListener;
import dev.alone.nexusCore.listeners.PickaxeEnchantListener;
import dev.alone.nexusCore.listeners.PickaxeListener;
import dev.alone.nexusCore.listeners.PickaxeMenuPolishListener;
import dev.alone.nexusCore.listeners.ProfileListener;
import dev.alone.nexusCore.listeners.ScoreboardListener;
import dev.alone.nexusCore.managers.*;
import dev.alone.nexusCore.profiles.ProfileManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class NexusCore extends JavaPlugin {

    private static NexusCore instance;

    private ProfileManager profileManager;
    private GuiManager guiManager;
    private ProgressionManager progressionManager;
    private CurrencyManager currencyManager;
    private ScoreboardManager scoreboardManager;
    private PickaxeManager pickaxeManager;
    private RebirthManager rebirthManager;
    private MineManager mineManager;
    private ActionBarManager actionBarManager;

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final String[] NEXUS_ASCII = {
            "███╗   ██╗███████╗██╗  ██╗██╗   ██╗███████╗",
            "████╗  ██║██╔════╝╚██╗██╔╝██║   ██║██╔════╝",
            "██╔██╗ ██║█████╗   ╚███╔╝ ██║   ██║███████╗",
            "██║╚██╗██║██╔══╝   ██╔██╗ ██║   ██║╚════██║",
            "██║ ╚████║███████╗██╔╝ ██╗╚██████╔╝███████║",
            "╚═╝  ╚═══╝╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝"
    };

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        profileManager = new ProfileManager(this);
        guiManager = new GuiManager(this);
        progressionManager = new ProgressionManager(this);
        currencyManager = new CurrencyManager(this);
        scoreboardManager = new ScoreboardManager(this);
        pickaxeManager = new PickaxeManager(this);
        rebirthManager = new RebirthManager(this, profileManager);

        mineManager = new MineManager(this);
        mineManager.load();

        this.actionBarManager = new ActionBarManager(this);
        this.actionBarManager.start();

        registerCommands();
        registerListeners();
        registerHooks();

        scoreboardManager.start();
        pickaxeManager.startTasks();

        sendEnableMessage();
    }

    @Override
    public void onDisable() {
        if (scoreboardManager != null) {
            scoreboardManager.shutdown();
        }

        if (pickaxeManager != null) {
            pickaxeManager.shutdown();
        }

        if (mineManager != null) {
            mineManager.saveAll();
        }

        if (profileManager != null) {
            profileManager.saveAllProfiles();
        }

        if (actionBarManager != null) {
            actionBarManager.stop();
        }

        sendDisableMessage();
    }

    public static NexusCore getInstance() {
        return instance;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public ProgressionManager getProgressionManager() {
        return progressionManager;
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public PickaxeManager getPickaxeManager() {
        return pickaxeManager;
    }

    public RebirthManager getRebirthManager() {
        return rebirthManager;
    }

    public MineManager getMineManager() {
        return mineManager;
    }

    public ActionBarManager getActionBarManager() {
        return actionBarManager;
    }

    public void reloadPlugin() {
        reloadConfig();

        if (guiManager != null) {
            guiManager.reload();
        }

        if (scoreboardManager != null) {
            scoreboardManager.reload();
        }

        if (pickaxeManager != null) {
            pickaxeManager.reload();
        }

        if (mineManager != null) {
            mineManager.saveAll();
        }
    }

    private void registerCommands() {
        registerCommand("nexuscore", new NexusCoreCommand(this));

        EssentialCommands essentialCommands = new EssentialCommands();

        registerCommand("fly", essentialCommands);
        registerCommand("gmc", essentialCommands);
        registerCommand("gms", essentialCommands);
        registerCommand("gmsp", essentialCommands);
        registerCommand("gma", essentialCommands);

        registerCommand("settings", new SettingsCommand(this));
        registerCommand("rebirth", new RebirthCommand(rebirthManager));
        registerCommand("ascension", new AscensionCommand(this));
        registerCommand("balance", new CurrencyCommand(this));
        registerCommand("pickaxe", new PickaxeCommand(this));
        registerCommand("enchants", new EnchantsCommand(this));
        registerCommand("mine", new MineCommand(this, mineManager));
    }

    private void registerCommand(String commandName, CommandExecutor executor) {
        if (getCommand(commandName) == null) {
            getLogger().warning("Command '" + commandName + "' is missing from plugin.yml.");
            return;
        }

        getCommand(commandName).setExecutor(executor);

        if (executor instanceof TabCompleter tabCompleter) {
            getCommand(commandName).setTabCompleter(tabCompleter);
        }
    }

    private void registerListeners() {
        registerListener(new ProfileListener(this));
        registerListener(new MenuListener());
        registerListener(new MiningListener(this));
        registerListener(new PickaxeEnchantListener(this));
        registerListener(new PickaxeMenuPolishListener(this));
        registerListener(new ChatListener(this));
        registerListener(new ScoreboardListener(this));
        registerListener(new PickaxeListener(this));

        registerListener(new MineListener(mineManager));
        registerListener(new MineMenuListener(this, mineManager));
    }

    private void registerListener(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void registerHooks() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI was not found. NexusCore placeholders will not be registered.");
            return;
        }

        new NexusPlaceholderExpansion(this).register();
        getLogger().info("Registered PlaceholderAPI expansion.");
    }

    private void sendEnableMessage() {
        sendBanner(
                "<gradient:#00CFFF:#0066FF>",
                "<green><bold>ENABLED</bold></green>",
                "NexusCore has successfully started.",
                "Core systems are ready for Nexus Network."
        );
    }

    private void sendDisableMessage() {
        sendBanner(
                "<gradient:#FF5555:#AA0000>",
                "<red><bold>DISABLED</bold></red>",
                "NexusCore is shutting down.",
                "All core systems have been safely stopped."
        );
    }

    private void sendBanner(String gradient, String status, String lineOne, String lineTwo) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();

        send(console, "");
        send(console, gradient + "<bold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</bold></gradient>");

        for (String line : NEXUS_ASCII) {
            send(console, gradient + "<bold>" + line + "</bold></gradient>");
        }

        send(console, "");
        send(console, status);
        send(console, "<gray>" + lineOne);
        send(console, "<dark_gray>" + lineTwo);
        send(console, gradient + "<bold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</bold></gradient>");
        send(console, "");
    }

    private void send(ConsoleCommandSender console, String message) {
        console.sendMessage(MINI_MESSAGE.deserialize(message));
    }
}
