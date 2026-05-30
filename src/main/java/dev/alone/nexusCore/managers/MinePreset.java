package dev.alone.nexusCore.managers;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

public enum MinePreset {

    STARTER(
            10,
            Material.STONE,
            "<gradient:#00CFFF:#0066FF><bold>Starter Mine</bold></gradient>",
            palette(
                    Material.STONE, 80,
                    Material.COAL_ORE, 12,
                    Material.IRON_ORE, 8
            )
    ),

    COAL(
            12,
            Material.COAL_ORE,
            "<gradient:#353535:#8A8A8A><bold>Coal Mine</bold></gradient>",
            palette(
                    Material.STONE, 65,
                    Material.COAL_ORE, 25,
                    Material.IRON_ORE, 10
            )
    ),

    IRON(
            14,
            Material.IRON_ORE,
            "<gradient:#D8D8D8:#FFFFFF><bold>Iron Mine</bold></gradient>",
            palette(
                    Material.STONE, 55,
                    Material.COAL_ORE, 20,
                    Material.IRON_ORE, 20,
                    Material.GOLD_ORE, 5
            )
    ),

    GOLD(
            16,
            Material.GOLD_ORE,
            "<gradient:#FCA800:#FCFC54><bold>Gold Mine</bold></gradient>",
            palette(
                    Material.STONE, 45,
                    Material.COAL_ORE, 15,
                    Material.IRON_ORE, 20,
                    Material.GOLD_ORE, 15,
                    Material.DIAMOND_ORE, 5
            )
    ),

    DIAMOND(
            20,
            Material.DIAMOND_ORE,
            "<gradient:#00EAFF:#00A6FF><bold>Diamond Mine</bold></gradient>",
            palette(
                    Material.STONE, 40,
                    Material.IRON_ORE, 20,
                    Material.GOLD_ORE, 20,
                    Material.DIAMOND_ORE, 15,
                    Material.EMERALD_ORE, 5
            )
    ),

    EMERALD(
            22,
            Material.EMERALD_ORE,
            "<gradient:#00FF88:#00B050><bold>Emerald Mine</bold></gradient>",
            palette(
                    Material.STONE, 35,
                    Material.IRON_ORE, 20,
                    Material.GOLD_ORE, 20,
                    Material.DIAMOND_ORE, 15,
                    Material.EMERALD_ORE, 10
            )
    ),

    NEXUS(
            24,
            Material.ANCIENT_DEBRIS,
            "<gradient:#0066FF:#00CFFF:#0066FF><bold>Nexus Mine</bold></gradient>",
            palette(
                    Material.DEEPSLATE, 35,
                    Material.GOLD_ORE, 20,
                    Material.DIAMOND_ORE, 20,
                    Material.EMERALD_ORE, 15,
                    Material.ANCIENT_DEBRIS, 10
            )
    );

    private final int slot;
    private final Material icon;
    private final String displayName;
    private final Map<Material, Integer> palette;

    MinePreset(int slot, Material icon, String displayName, Map<Material, Integer> palette) {
        this.slot = slot;
        this.icon = icon;
        this.displayName = displayName;
        this.palette = palette;
    }

    public int getSlot() {
        return slot;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Map<Material, Integer> getPalette() {
        return new LinkedHashMap<>(palette);
    }

    public static MinePreset fromSlot(int slot) {
        for (MinePreset preset : values()) {
            if (preset.slot == slot) {
                return preset;
            }
        }

        return null;
    }

    private static Map<Material, Integer> palette(Object... values) {
        Map<Material, Integer> map = new LinkedHashMap<>();

        for (int i = 0; i < values.length; i += 2) {
            Material material = (Material) values[i];
            int weight = (int) values[i + 1];
            map.put(material, weight);
        }

        return map;
    }
}