package dev.alone.nexusCore.managers;

public enum PickaxeEnchant {

    EFFICIENCY("efficiency", "Efficiency", 1),
    HASTE("haste", "Haste", 1),
    SPEED("speed", "Speed", 1),
    TOKEN_FINDER("token_finder", "Token Miner", 1),
    JACKHAMMER("jackhammer", "Jackhammer", 5),
    MINE_STRIKE("mine_strike", "Mine Strike", 10),
    GEM_FINDER("gem_finder", "Gem Finder", 20),
    BEACON_FINDER("beacon_finder", "Beacon Finder", 20),
    VEIN_MINER("vein_miner", "Vein Miner", 30),
    KEY_FINDER("key_finder", "Key Finder", 40),
    TOKEN_EXPLOSION("token_explosion", "Token Explosion", 50),
    NUKE("nuke", "Nuke", 60),
    METEOR("meteor", "Meteor", 70),
    TOKEN_MERCHANT("token_merchant", "Token Merchant", 75),
    SECOND_HAND("second_hand", "Second Hand", 100),
    HOLY_ARROWS("holy_arrows", "Holy Arrows", 125),
    KEY_MERCHANT("key_merchant", "Key Merchant", 150),
    METEOR_STRIKE("meteor_strike", "Meteor Strike", 175),
    GEM_MERCHANT("gem_merchant", "Gem Merchant", 200),
    SHOCKWAVE("shockwave", "Shockwave", 200),
    CLUSTER_BOMB("cluster_bomb", "Cluster Bomb", 225),
    TOKEN_GREED("token_greed", "Token Greed", 250);

    private final String id;
    private final String displayName;
    private final int unlockLevel;

    PickaxeEnchant(String id, String displayName, int unlockLevel) {
        this.id = id;
        this.displayName = displayName;
        this.unlockLevel = Math.max(1, unlockLevel);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getUnlockLevel() {
        return unlockLevel;
    }

    public static PickaxeEnchant fromInput(String input) {
        if (input == null) {
            return null;
        }

        String normalized = input.toLowerCase()
                .replace("-", "_")
                .replace(" ", "_");

        if (normalized.equals("fortune")) {
            return null;
        }

        if (normalized.equals("token_miner")) {
            normalized = "token_finder";
        }

        if (normalized.equals("shard_finder") || normalized.equals("shard_miner")) {
            normalized = "beacon_finder";
        }

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
