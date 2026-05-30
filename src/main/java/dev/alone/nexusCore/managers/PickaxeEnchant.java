package dev.alone.nexusCore.managers;

public enum PickaxeEnchant {

    EFFICIENCY("efficiency", "Efficiency"),
    FORTUNE("fortune", "Fortune"),
    HASTE("haste", "Haste"),
    SPEED("speed", "Speed"),
    TOKEN_FINDER("token_finder", "Token Finder"),
    GEM_FINDER("gem_finder", "Gem Finder"),
    KEY_FINDER("key_finder", "Key Finder"),
    LUCKY("lucky", "Lucky"),
    EXPLOSIVE("explosive", "Explosive"),
    JACKHAMMER("jackhammer", "Jackhammer"),
    LASER("laser", "Laser");

    private final String id;
    private final String displayName;

    PickaxeEnchant(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PickaxeEnchant fromInput(String input) {
        if (input == null) {
            return null;
        }

        String normalized = input.toLowerCase()
                .replace("-", "_")
                .replace(" ", "_");

        for (PickaxeEnchant enchant : values()) {
            if (enchant.id.equalsIgnoreCase(normalized)) {
                return enchant;
            }

            if (enchant.displayName.toLowerCase().replace(" ", "_").equals(normalized)) {
                return enchant;
            }
        }

        return null;
    }
}