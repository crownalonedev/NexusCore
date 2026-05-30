package dev.alone.nexusCore.menus;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.managers.MineManager;
import dev.alone.nexusCore.managers.PrivateMine;
import dev.alone.nexusCore.utils.MineItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MineMenus {

    private static final int[] BLOCK_OPTION_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final List<Material> BLOCK_OPTIONS = List.of(
            Material.AMETHYST_BLOCK,
            Material.CALCITE,
            Material.SMOOTH_QUARTZ,
            Material.QUARTZ_BLOCK,
            Material.SEA_LANTERN,
            Material.PRISMARINE,
            Material.DARK_PRISMARINE,

            Material.PURPUR_BLOCK,
            Material.BLUE_ICE,
            Material.PACKED_ICE,
            Material.COPPER_BLOCK,
            Material.CUT_COPPER,
            Material.OXIDIZED_COPPER,
            Material.IRON_BLOCK,

            Material.GOLD_BLOCK,
            Material.DIAMOND_BLOCK,
            Material.EMERALD_BLOCK,
            Material.REDSTONE_BLOCK,
            Material.LAPIS_BLOCK,
            Material.COAL_BLOCK,
            Material.LIGHT_BLUE_CONCRETE,

            Material.CYAN_CONCRETE,
            Material.BLUE_CONCRETE,
            Material.PURPLE_CONCRETE,
            Material.MAGENTA_CONCRETE,
            Material.LIME_CONCRETE,
            Material.ORANGE_CONCRETE,
            Material.WHITE_CONCRETE
    );

    public static void openMain(NexusCore plugin, MineManager mineManager, Player player) {
        PrivateMine mine = mineManager.getOrCreateMine(player);
        MineManager.MineSize size = mineManager.getMineSize(player);

        int requiredRank = getBlockSelectionRequiredRank(plugin);
        int currentRank = mineManager.getPlayerRankLevel(player);
        boolean blockSelectionUnlocked = currentRank >= requiredRank;

        MineMenuHolder holder = new MineMenuHolder(MineMenuHolder.MenuType.MAIN);
        Inventory inventory = Bukkit.createInventory(
                holder,
                45,
                MineItemUtil.component("<gradient:#0066FF:#00CFFF><bold>Private Mine</bold></gradient>")
        );

        holder.setInventory(inventory);
        fill(inventory);

        inventory.setItem(11, MineItemUtil.item(
                Material.ENDER_PEARL,
                "<gradient:#00CFFF:#0066FF><bold>Go To Mine</bold></gradient>",
                "<gray>Teleport to your private mine.",
                "",
                "<yellow>Click to teleport."
        ));

        inventory.setItem(13, MineItemUtil.item(
                Material.ANVIL,
                "<gradient:#FCFC54:#FCA800><bold>Reset Mine</bold></gradient>",
                "<gray>Refills your private mine.",
                "<gray>Drops are disabled.",
                "",
                "<yellow>Click to reset."
        ));

        inventory.setItem(15, MineItemUtil.item(
                blockSelectionUnlocked ? Material.DIAMOND_BLOCK : Material.RED_STAINED_GLASS_PANE,
                blockSelectionUnlocked
                        ? "<gradient:#00EAFF:#00A6FF><bold>Change Mine Blocks</bold></gradient>"
                        : "<red><bold>Mine Blocks Locked</bold>",
                blockSelectionUnlocked
                        ? List.of(
                        "<gray>Select 1-4 blocks for your mine.",
                        "<gray>Your selected blocks will be randomly mixed.",
                        "",
                        "<yellow>Click to open selector."
                )
                        : List.of(
                        "<gray>You must reach rank <aqua>" + requiredRank + "</aqua><gray>.",
                        "<gray>Your Rank: <red>" + currentRank,
                        "",
                        "<red>Locked until rank " + requiredRank + "."
                )
        ));

        inventory.setItem(29, MineItemUtil.item(
                Material.BEACON,
                "<gradient:#0066FF:#00CFFF><bold>Mine Size</bold></gradient>",
                "<gray>Current Size: <aqua>" + mine.getWidth() + "x" + mine.getHeight() + "x" + mine.getWidth(),
                "<gray>Upgrade Tier: <aqua>" + size.tier() + "/" + size.maxTier(),
                "<gray>Max Rank: <aqua>1000",
                "",
                "<dark_gray>Your mine grows as your rank increases."
        ));

        inventory.setItem(33, MineItemUtil.item(
                Material.BEDROCK,
                "<gradient:#FFFFFF:#00CFFF><bold>Mine Protection</bold></gradient>",
                "<gray>This mine is solo-only.",
                "<gray>Outside blocks are protected.",
                "<gray>All damage is disabled.",
                "<gray>Suffocation and hunger loss are disabled.",
                "<gray>Drops are disabled."
        ));

        inventory.setItem(40, MineItemUtil.item(
                Material.BARRIER,
                "<red><bold>Close</bold>",
                "<gray>Close this menu."
        ));

        player.openInventory(inventory);
    }

    public static boolean openBlocks(NexusCore plugin, MineManager mineManager, Player player) {
        PrivateMine mine = mineManager.getOrCreateMine(player);
        return openBlocks(plugin, mineManager, player, getCurrentSelectedBlocks(mine));
    }

    public static boolean openBlocks(NexusCore plugin, MineManager mineManager, Player player, Set<Material> selectedBlocks) {
        int requiredRank = getBlockSelectionRequiredRank(plugin);
        int currentRank = mineManager.getPlayerRankLevel(player);

        if (currentRank < requiredRank) {
            player.sendMessage(MineItemUtil.prefixed(
                    "<red>You must be rank <aqua>" + requiredRank + "</aqua><red> to change your mine blocks."
            ));
            mineManager.playConfiguredSound(player, "error");
            return false;
        }

        int maxBlocks = getMaxSelectableBlocks(plugin);

        MineMenuHolder holder = new MineMenuHolder(MineMenuHolder.MenuType.BLOCKS, selectedBlocks);
        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                MineItemUtil.component("<gradient:#0066FF:#00CFFF><bold>Mine Block Selector</bold></gradient>")
        );

        holder.setInventory(inventory);
        fill(inventory);

        for (int i = 0; i < BLOCK_OPTIONS.size() && i < BLOCK_OPTION_SLOTS.length; i++) {
            Material material = BLOCK_OPTIONS.get(i);
            boolean selected = selectedBlocks.contains(material);

            inventory.setItem(BLOCK_OPTION_SLOTS[i], MineItemUtil.item(
                    material,
                    selected
                            ? "<green><bold>✔ " + cleanMaterial(material) + "</bold>"
                            : "<gradient:#00CFFF:#0066FF><bold>" + cleanMaterial(material) + "</bold></gradient>",
                    selected
                            ? List.of(
                            "<green>Selected",
                            "",
                            "<gray>Your mine can use up to <aqua>" + maxBlocks + "</aqua><gray> blocks.",
                            "<gray>Selected: <aqua>" + selectedBlocks.size() + "/" + maxBlocks,
                            "",
                            "<yellow>Click to remove."
                    )
                            : List.of(
                            "<gray>Add this block to your mine mix.",
                            "",
                            "<gray>Your mine can use up to <aqua>" + maxBlocks + "</aqua><gray> blocks.",
                            "<gray>Selected: <aqua>" + selectedBlocks.size() + "/" + maxBlocks,
                            "",
                            "<yellow>Click to select."
                    )
            ));
        }

        inventory.setItem(45, MineItemUtil.item(
                Material.BOOK,
                "<gradient:#FFFFFF:#00CFFF><bold>How This Works</bold></gradient>",
                "<gray>Select <aqua>1</aqua><gray>, <aqua>2</aqua><gray>, <aqua>3</aqua><gray>, or <aqua>4</aqua><gray> blocks.",
                "<gray>Your mine will randomly mix them.",
                "<gray>You cannot select more than <aqua>" + maxBlocks + "</aqua><gray> blocks."
        ));

        inventory.setItem(48, MineItemUtil.item(
                Material.REDSTONE,
                "<red><bold>Clear Selected</bold>",
                "<gray>Remove all selected blocks."
        ));

        inventory.setItem(49, MineItemUtil.item(
                Material.ARROW,
                "<yellow><bold>Back</bold>",
                "<gray>Return to the main menu."
        ));

        inventory.setItem(50, MineItemUtil.item(
                Material.LIME_DYE,
                "<green><bold>Apply Blocks</bold>",
                "<gray>Selected: <aqua>" + selectedBlocks.size() + "/" + maxBlocks,
                "",
                "<yellow>Click to apply and reset your mine."
        ));

        player.openInventory(inventory);
        return true;
    }

    public static boolean isBlockOption(Material material) {
        return BLOCK_OPTIONS.contains(material);
    }

    public static Map<Material, Integer> createPaletteFromSelection(Set<Material> selectedBlocks) {
        Map<Material, Integer> palette = new LinkedHashMap<>();

        for (Material material : selectedBlocks) {
            if (!isBlockOption(material)) {
                continue;
            }

            if (palette.size() >= 4) {
                break;
            }

            palette.put(material, 100);
        }

        return palette;
    }

    public static int getBlockSelectionRequiredRank(NexusCore plugin) {
        return Math.max(1, plugin.getConfig().getInt("private-mines.block-selection.required-rank", 1000));
    }

    public static int getMaxSelectableBlocks(NexusCore plugin) {
        return Math.max(1, Math.min(4, plugin.getConfig().getInt("private-mines.block-selection.max-blocks", 4)));
    }

    private static Set<Material> getCurrentSelectedBlocks(PrivateMine mine) {
        Set<Material> selected = new LinkedHashSet<>();

        for (Material material : mine.getPalette().keySet()) {
            if (!isBlockOption(material)) {
                continue;
            }

            if (selected.size() >= 4) {
                break;
            }

            selected.add(material);
        }

        return selected;
    }

    private static void fill(Inventory inventory) {
        ItemStack filler = MineItemUtil.filler();

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private static String cleanMaterial(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        StringBuilder builder = new StringBuilder();

        for (String part : name.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }

            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(" ");
        }

        return builder.toString().trim();
    }
}