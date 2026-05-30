package dev.alone.nexusCore.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MineItemUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static final String PREFIX = "<gradient:#00CFFF:#0066FF><bold>NexusMC</bold></gradient> <dark_gray>»</dark_gray>";

    public static Component prefixed(String text) {
        return component(PREFIX + " " + text);
    }

    public static Component component(String text) {
        Component component;

        if (text.contains("<") && text.contains(">")) {
            component = MINI_MESSAGE.deserialize(text);
        } else {
            component = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        }

        return component.decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(component(name));

            List<Component> loreComponents = new ArrayList<>();

            for (String line : lore) {
                loreComponents.add(component(line));
            }

            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }

        return item;
    }

    public static ItemStack item(Material material, String name, String... lore) {
        return item(material, name, List.of(lore));
    }

    public static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }
}