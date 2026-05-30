package dev.alone.nexusCore.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkullUtil {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private SkullUtil() {
        // Utility class
    }

    public static ItemStack createBase64Head(String base64, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        if (meta == null) {
            return item;
        }

        applyTexture(meta, base64);

        meta.displayName(MessageUtil.color(name));

        List<Component> lore = new ArrayList<>();

        for (String line : loreLines) {
            lore.add(MessageUtil.color(line));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());

        item.setItemMeta(meta);
        return item;
    }

    private static void applyTexture(SkullMeta meta, String base64) {
        String skinUrl = extractSkinUrl(base64);

        if (skinUrl == null || skinUrl.isBlank()) {
            return;
        }

        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "NexusTexture");
            PlayerTextures textures = profile.getTextures();

            URL url = URI.create(skinUrl).toURL();
            textures.setSkin(url);

            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (Exception ignored) {
            // If a texture fails, the item will stay as a normal head.
        }
    }

    private static String extractSkinUrl(String base64) {
        try {
            String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            Matcher matcher = URL_PATTERN.matcher(decoded);

            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        return null;
    }
}