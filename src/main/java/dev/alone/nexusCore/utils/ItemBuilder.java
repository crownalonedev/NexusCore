package dev.alone.nexusCore.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack item;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemBuilder name(String name) {
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return this;
        }

        meta.displayName(MessageUtil.color(name));
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return this;
        }

        List<Component> lore = new ArrayList<>();

        for (String line : lines) {
            lore.add(MessageUtil.color(line));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return this;
        }

        List<Component> lore = new ArrayList<>();

        for (String line : lines) {
            lore.add(MessageUtil.color(line));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder hideFlags() {
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return this;
        }

        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return this;
    }

    public ItemStack build() {
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }

        return item;
    }
}