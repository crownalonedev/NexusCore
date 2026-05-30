package dev.alone.nexusCore.utils;

import dev.alone.nexusCore.NexusCore;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class SoundUtil {

    private SoundUtil() {
        // Utility class
    }

    public static void play(Player player, String path) {
        FileConfiguration config = NexusCore.getInstance().getConfig();

        if (!config.getBoolean(path + ".enabled", true)) {
            return;
        }

        String soundName = config.getString(path + ".sound", "UI_BUTTON_CLICK");
        float volume = (float) config.getDouble(path + ".volume", 1.0);
        float pitch = (float) config.getDouble(path + ".pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException exception) {
            NexusCore.getInstance().getLogger().warning("Invalid sound in config at '" + path + ".sound': " + soundName);
        }
    }
}