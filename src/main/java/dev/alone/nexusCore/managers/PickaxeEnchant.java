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
    TOKEN_GREED("token_greed", "Token Greed", 250),
    WILD_BLAZE("wild_blaze", "Wild Blaze", 275),
    DOUBLE_STRIKE("double_strike", "Double Strike", 300),
    GANG_POINT_FINDER("gang_point_finder", "Gang Point Finder", 325),
    SNOW_ARMY("snow_army", "Snow Army", 350),
    DRAGONS_WRATH("dragons_wrath", "Dragon's Wrath", 375),
    SCAVENGER("scavenger", "Scavenger", 400),
    ENDERMAN_ABOMINATION("enderman_abomination", "Enderman Abomination", 450),
    VOLCANO("volcano", "Volcano", 475),
    HYDROGEN_BOMB("hydrogen_bomb", "Hydrogen Bomb", 500),
    PROPHET("prophet", "Prophet", 550),
    BLACK_HOLE("black_hole", "Black Hole", 600),

    LOTTERY("lottery", "Lottery", 25),
    SHATTER("shatter", "Shatter", 35),
    JACKPOT("jackpot", "Jackpot", 40),
    METEORITE("meteorite", "Meteorite", 50),
    FIRECRACKER("firecracker", "Firecracker", 75),
    DRAGONS_EYE("dragons_eye", "Dragons Eye", 100),
    FIRECRACKS("firecracks", "Firecracks", 125),
    PET_XP_FINDER("pet_xp_finder", "Pet XP Finder", 125),
    CHARITY("charity", "Charity", 150),
    EXCAVATOR("excavator", "Excavator", 175),
    ARCTIC_DESTROYER("arctic_destroyer", "Arctic Destroyer", 200),
    DYNAMITE("dynamite", "Dynamite", 250),
    HIRED_HAND("hired_hand", "Hired Hand", 300),
    SOUL_REAPER("soul_reaper", "Soul Reaper", 325),
    OVERFLOW("overflow", "Overflow", 350),
    CHUGGERNAUT("chuggernaut", "Chuggernaut", 400),
    HEROS_ASSISTANCE("heros_assistance", "Heros Assistance", 450),
    SNOWSTORM("snowstorm", "Snowstorm", 500),
    INVASION("invasion", "Invasion", 550),
    BLESSED("blessed", "Blessed", 600),
    WORSHIP("worship", "Worship", 700),
    THUNDERBIRD("thunderbird", "Thunderbird", 750),
    SWARM("swarm", "Swarm", 825),
    SUPERNOVA("supernova", "Supernova", 900);

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
                .replace("'", "")
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

        if (normalized.equals("dragon_wrath")) {
            normalized = "dragons_wrath";
        }

        if (normalized.equals("dragon_eye")) {
            normalized = "dragons_eye";
        }

        if (normalized.equals("gang_points") || normalized.equals("gang_point_miner")) {
            normalized = "gang_point_finder";
        }

        if (normalized.equals("hero_assistance") || normalized.equals("hero's_assistance")) {
            normalized = "heros_assistance";
        }

        for (PickaxeEnchant enchant : values()) {
            if (enchant.id.equalsIgnoreCase(normalized)) {
                return enchant;
            }

            if (enchant.displayName.toLowerCase().replace("'", "").replace(" ", "_").equals(normalized)) {
                return enchant;
            }
        }

        return null;
    }
}
