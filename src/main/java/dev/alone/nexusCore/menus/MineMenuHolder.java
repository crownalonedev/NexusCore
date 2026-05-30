package dev.alone.nexusCore.menus;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.LinkedHashSet;
import java.util.Set;

public class MineMenuHolder implements InventoryHolder {

    public enum MenuType {
        MAIN,
        BLOCKS
    }

    private final MenuType menuType;
    private final Set<Material> selectedBlocks;
    private Inventory inventory;

    public MineMenuHolder(MenuType menuType) {
        this(menuType, new LinkedHashSet<>());
    }

    public MineMenuHolder(MenuType menuType, Set<Material> selectedBlocks) {
        this.menuType = menuType;
        this.selectedBlocks = new LinkedHashSet<>(selectedBlocks);
    }

    public MenuType getMenuType() {
        return menuType;
    }

    public Set<Material> getSelectedBlocks() {
        return selectedBlocks;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}