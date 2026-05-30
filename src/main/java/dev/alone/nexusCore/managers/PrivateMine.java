package dev.alone.nexusCore.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class PrivateMine {

    private final UUID ownerUuid;
    private String ownerName;

    private final String worldName;

    private final int centerX;
    private final int centerZ;

    private int minX;
    private int minY;
    private int minZ;

    private int maxX;
    private int maxY;
    private int maxZ;

    private double spawnX;
    private double spawnY;
    private double spawnZ;

    private int radius;
    private int height;

    private boolean customPalette;
    private int paletteTier;

    private Map<Material, Integer> palette;

    public PrivateMine(
            UUID ownerUuid,
            String ownerName,
            String worldName,
            int centerX,
            int centerZ,
            int minY,
            int radius,
            int height,
            Map<Material, Integer> palette
    ) {
        this(ownerUuid, ownerName, worldName, centerX, centerZ, minY, radius, height, palette, false, 0);
    }

    public PrivateMine(
            UUID ownerUuid,
            String ownerName,
            String worldName,
            int centerX,
            int centerZ,
            int minY,
            int radius,
            int height,
            Map<Material, Integer> palette,
            boolean customPalette,
            int paletteTier
    ) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.worldName = worldName;

        this.centerX = centerX;
        this.centerZ = centerZ;

        this.minY = minY;
        this.radius = radius;
        this.height = height;

        this.palette = new LinkedHashMap<>(palette);
        this.customPalette = customPalette;
        this.paletteTier = Math.max(0, paletteTier);

        recalculateBounds();
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public void setMinY(int minY) {
        this.minY = minY;
        recalculateBounds();
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public double getSpawnX() {
        return spawnX;
    }

    public double getSpawnY() {
        return spawnY;
    }

    public double getSpawnZ() {
        return spawnZ;
    }

    public int getRadius() {
        return radius;
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return (radius * 2) + 1;
    }

    public boolean isCustomPalette() {
        return customPalette;
    }

    public void setCustomPalette(boolean customPalette) {
        this.customPalette = customPalette;
    }

    public int getPaletteTier() {
        return paletteTier;
    }

    public void setPaletteTier(int paletteTier) {
        this.paletteTier = Math.max(0, paletteTier);
    }

    public void resize(int radius, int height) {
        this.radius = Math.max(1, radius);
        this.height = Math.max(1, height);
        recalculateBounds();
    }

    private void recalculateBounds() {
        this.minX = centerX - radius;
        this.maxX = centerX + radius;

        this.minZ = centerZ - radius;
        this.maxZ = centerZ + radius;

        this.maxY = minY + height - 1;

        this.spawnX = centerX + 0.5;
        this.spawnY = maxY + 3.0;
        this.spawnZ = centerZ + 0.5;
    }

    public Location getSpawnLocation() {
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            return null;
        }

        return new Location(world, spawnX, spawnY, spawnZ, 0.0f, 0.0f);
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (!location.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean isOwner(UUID uuid) {
        return ownerUuid.equals(uuid);
    }

    public boolean canAccess(UUID uuid) {
        return isOwner(uuid);
    }

    public Map<Material, Integer> getPalette() {
        return Collections.unmodifiableMap(palette);
    }

    public void setPalette(Map<Material, Integer> palette) {
        this.palette = new LinkedHashMap<>(palette);
    }
}