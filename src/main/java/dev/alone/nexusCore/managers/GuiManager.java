package dev.alone.nexusCore.managers;

import dev.alone.nexusCore.NexusCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class GuiManager {

    private final NexusCore plugin;
    private final File guiFolder;
    private final Map<String, YamlConfiguration> guis = new HashMap<>();

    public GuiManager(NexusCore plugin) {
        this.plugin = plugin;
        this.guiFolder = new File(plugin.getDataFolder(), "guis");

        createFolder();
        loadGuis();
    }

    public void reload() {
        guis.clear();
        loadGuis();
    }

    public YamlConfiguration getGui(String name) {
        return guis.getOrDefault(name.toLowerCase(Locale.ROOT), new YamlConfiguration());
    }

    private void createFolder() {
        if (!guiFolder.exists() && !guiFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create guis folder.");
        }
    }

    private void loadGuis() {
        saveDefaultGui("profile.yml");
        saveDefaultGui("settings.yml");
        saveDefaultGui("pickaxe-enchant-menu.yml");

        File[] files = guiFolder.listFiles((folder, fileName) ->
                fileName.toLowerCase(Locale.ROOT).endsWith(".yml")
        );

        if (files == null) {
            plugin.getLogger().warning("No GUI files were found.");
            return;
        }

        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String guiName = file.getName().replace(".yml", "").toLowerCase(Locale.ROOT);

            if (!config.isConfigurationSection("items")) {
                plugin.getLogger().warning("GUI file '" + file.getName() + "' has no 'items' section. The menu may appear empty.");
            }

            guis.put(guiName, config);
            plugin.getLogger().info("Loaded GUI file: " + file.getName());
        }
    }

    private void saveDefaultGui(String fileName) {
        File file = new File(guiFolder, fileName);

        if (!file.exists()) {
            saveResource(fileName);
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        boolean broken = !config.isConfigurationSection("settings") || !config.isConfigurationSection("items");

        if (!broken) {
            return;
        }

        File backup = new File(guiFolder, fileName + ".broken");

        if (backup.exists() && !backup.delete()) {
            plugin.getLogger().warning("Failed to delete old broken GUI backup: " + backup.getName());
        }

        if (file.renameTo(backup)) {
            plugin.getLogger().warning("Broken GUI file backed up as: " + backup.getName());
            saveResource(fileName);
        } else {
            plugin.getLogger().warning("Failed to backup broken GUI file: " + fileName);
        }
    }

    private void saveResource(String fileName) {
        try {
            plugin.saveResource("guis/" + fileName, false);
            plugin.getLogger().info("Created default GUI file: guis/" + fileName);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().severe("Could not find default resource: src/main/resources/guis/" + fileName);
            plugin.getLogger().severe("Your menu will be empty until this file exists in your resources folder.");
        }
    }
}