package dev.alone.nexusCore.profiles;

import dev.alone.nexusCore.managers.PickaxeEnchant;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerProfile {

    private final UUID uuid;
    private String username;

    private int rank;
    private int prestige;
    private int ascension;
    private int rebirth;

    private BigDecimal money;
    private BigInteger tokens;
    private BigInteger gems;
    private BigInteger beacons;

    private long blocksMined;
    private long rawBlocksMined;
    private long rankProgressBlocks;

    private int backpackSize;
    private boolean autoSell;
    private boolean autoPickup;
    private boolean autoRebirth;
    private boolean autoAscension;

    private int pickaxeLevel;
    private long pickaxeXp;
    private int pickaxeSlot;
    private boolean receivedPickaxe;

    private final Map<String, Integer> pickaxeEnchants = new HashMap<>();

    private String currentMine;

    public PlayerProfile(UUID uuid, String username) {
        this(uuid, username, 1);
    }

    public PlayerProfile(UUID uuid, String username, int startingRank) {
        this.uuid = uuid;
        this.username = username;

        this.rank = Math.max(1, startingRank);
        this.prestige = 0;
        this.ascension = 0;
        this.rebirth = 0;

        this.money = BigDecimal.ZERO;
        this.tokens = BigInteger.ZERO;
        this.gems = BigInteger.ZERO;
        this.beacons = BigInteger.ZERO;

        this.blocksMined = 0L;
        this.rawBlocksMined = 0L;
        this.rankProgressBlocks = 0L;

        this.backpackSize = 5000;
        this.autoSell = true;
        this.autoPickup = true;
        this.autoRebirth = false;
        this.autoAscension = false;

        this.pickaxeLevel = 1;
        this.pickaxeXp = 0L;
        this.pickaxeSlot = 0;
        this.receivedPickaxe = false;

        this.currentMine = String.valueOf(this.rank);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = Math.max(1, rank);
        syncCurrentMineToRank();
    }

    public void setRank(int rank, int maxRank) {
        int safeMaxRank = Math.max(1, maxRank);

        if (rank < 1) {
            this.rank = 1;
        } else {
            this.rank = Math.min(rank, safeMaxRank);
        }

        syncCurrentMineToRank();
    }

    public boolean rankUp(int maxRank) {
        if (isMaxRank(maxRank)) {
            return false;
        }

        setRank(rank + 1, maxRank);
        return true;
    }

    public boolean isMaxRank(int maxRank) {
        return rank >= Math.max(1, maxRank);
    }

    public int getNextRank(int maxRank) {
        if (isMaxRank(maxRank)) {
            return Math.max(1, maxRank);
        }

        return Math.min(rank + 1, Math.max(1, maxRank));
    }

    public boolean canRebirth(int maxRank) {
        return isMaxRank(maxRank);
    }

    public boolean rebirth(int maxRank) {
        if (!canRebirth(maxRank)) {
            return false;
        }

        rebirth++;
        setRank(maxRank, maxRank);
        return true;
    }

    public boolean canAscend(int rebirthsRequired) {
        return rebirth >= Math.max(1, rebirthsRequired);
    }

    public boolean ascend(int rebirthsRequired, int maxRank) {
        if (!canAscend(rebirthsRequired)) {
            return false;
        }

        ascension++;
        rebirth = 0;
        setRank(maxRank, maxRank);
        return true;
    }

    public int getRebirthsUntilAscension(int rebirthsRequired) {
        return Math.max(0, Math.max(1, rebirthsRequired) - rebirth);
    }

    public int getPrestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        this.prestige = Math.max(0, prestige);
    }

    public int getAscension() {
        return ascension;
    }

    public void setAscension(int ascension) {
        this.ascension = Math.max(0, ascension);
    }

    public int getRebirth() {
        return rebirth;
    }

    public void setRebirth(int rebirth) {
        this.rebirth = Math.max(0, rebirth);
    }

    public BigDecimal getMoney() {
        return money;
    }

    public void setMoney(BigDecimal money) {
        if (money == null) {
            this.money = BigDecimal.ZERO;
            return;
        }

        this.money = money.max(BigDecimal.ZERO);
    }

    public void addMoney(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        this.money = this.money.add(amount);
    }

    public void removeMoney(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        this.money = this.money.subtract(amount).max(BigDecimal.ZERO);
    }

    public BigInteger getTokens() {
        return tokens;
    }

    public void setTokens(BigInteger tokens) {
        if (tokens == null) {
            this.tokens = BigInteger.ZERO;
            return;
        }

        this.tokens = tokens.max(BigInteger.ZERO);
    }

    public void addTokens(BigInteger amount) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        this.tokens = this.tokens.add(amount);
    }

    public void removeTokens(BigInteger amount) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        this.tokens = this.tokens.subtract(amount).max(BigInteger.ZERO);
    }

    public boolean hasTokens(BigInteger amount) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return true;
        }

        return this.tokens.compareTo(amount) >= 0;
    }

    public boolean takeTokens(BigInteger amount) {
        if (!hasTokens(amount)) {
            return false;
        }

        removeTokens(amount);
        return true;
    }

    public BigInteger getGems() {
        return gems;
    }

    public void setGems(BigInteger gems) {
        if (gems == null) {
            this.gems = BigInteger.ZERO;
            return;
        }

        this.gems = gems.max(BigInteger.ZERO);
    }

    public void addGems(BigInteger amount) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        this.gems = this.gems.add(amount);
    }

    public void removeGems(BigInteger amount) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        this.gems = this.gems.subtract(amount).max(BigInteger.ZERO);
    }

    public boolean hasGems(BigInteger amount) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return true;
        }

        return this.gems.compareTo(amount) >= 0;
    }

    public boolean takeGems(BigInteger amount) {
        if (!hasGems(amount)) {
            return false;
        }

        removeGems(amount);
        return true;
    }

    public BigInteger getBeacons() {
        return beacons;
    }

    public void setBeacons(BigInteger beacons) {
        if (beacons == null) {
            this.beacons = BigInteger.ZERO;
            return;
        }

        this.beacons = beacons.max(BigInteger.ZERO);
    }

    public void addBeacons(BigInteger amount) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        this.beacons = this.beacons.add(amount);
    }

    public void removeBeacons(BigInteger amount) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        this.beacons = this.beacons.subtract(amount).max(BigInteger.ZERO);
    }

    public long getBlocksMined() {
        return blocksMined;
    }

    public void setBlocksMined(long blocksMined) {
        this.blocksMined = Math.max(0L, blocksMined);
    }

    public void addBlocksMined(long amount) {
        if (amount <= 0) {
            return;
        }

        this.blocksMined += amount;
    }

    public long getRawBlocksMined() {
        return rawBlocksMined;
    }

    public void setRawBlocksMined(long rawBlocksMined) {
        this.rawBlocksMined = Math.max(0L, rawBlocksMined);
    }

    public void addRawBlocksMined(long amount) {
        if (amount <= 0) {
            return;
        }

        this.rawBlocksMined += amount;
    }

    public long getRankProgressBlocks() {
        return rankProgressBlocks;
    }

    public void setRankProgressBlocks(long rankProgressBlocks) {
        this.rankProgressBlocks = Math.max(0L, rankProgressBlocks);
    }

    public void addRankProgressBlocks(long amount) {
        if (amount <= 0) {
            return;
        }

        this.rankProgressBlocks += amount;
    }

    public void removeRankProgressBlocks(long amount) {
        if (amount <= 0) {
            return;
        }

        this.rankProgressBlocks = Math.max(0L, this.rankProgressBlocks - amount);
    }

    public void resetRankProgressBlocks() {
        this.rankProgressBlocks = 0L;
    }

    public int getBackpackSize() {
        return backpackSize;
    }

    public void setBackpackSize(int backpackSize) {
        this.backpackSize = Math.max(0, backpackSize);
    }

    public boolean isAutoSell() {
        return autoSell;
    }

    public void setAutoSell(boolean autoSell) {
        this.autoSell = autoSell;
    }

    public boolean isAutoPickup() {
        return autoPickup;
    }

    public void setAutoPickup(boolean autoPickup) {
        this.autoPickup = autoPickup;
    }

    public boolean isAutoRebirth() {
        return autoRebirth;
    }

    public void setAutoRebirth(boolean autoRebirth) {
        this.autoRebirth = autoRebirth;
    }

    public boolean isAutoAscension() {
        return autoAscension;
    }

    public void setAutoAscension(boolean autoAscension) {
        this.autoAscension = autoAscension;
    }

    public int getPickaxeLevel() {
        return pickaxeLevel;
    }

    public void setPickaxeLevel(int pickaxeLevel) {
        this.pickaxeLevel = Math.max(1, pickaxeLevel);
    }

    public long getPickaxeXp() {
        return pickaxeXp;
    }

    public void setPickaxeXp(long pickaxeXp) {
        this.pickaxeXp = Math.max(0L, pickaxeXp);
    }

    public void addPickaxeXp(long amount) {
        if (amount <= 0) {
            return;
        }

        this.pickaxeXp += amount;
    }

    public int getPickaxeSlot() {
        return Math.max(0, Math.min(8, pickaxeSlot));
    }

    public void setPickaxeSlot(int pickaxeSlot) {
        this.pickaxeSlot = Math.max(0, Math.min(8, pickaxeSlot));
    }

    public boolean hasReceivedPickaxe() {
        return receivedPickaxe;
    }

    public void setReceivedPickaxe(boolean receivedPickaxe) {
        this.receivedPickaxe = receivedPickaxe;
    }

    public int getEnchantLevel(PickaxeEnchant enchant) {
        if (enchant == null) {
            return 0;
        }

        return pickaxeEnchants.getOrDefault(enchant.getId(), 0);
    }

    public void setEnchantLevel(PickaxeEnchant enchant, int level) {
        if (enchant == null) {
            return;
        }

        pickaxeEnchants.put(enchant.getId(), Math.max(0, level));
    }

    public void addEnchantLevel(PickaxeEnchant enchant, int amount) {
        if (enchant == null || amount <= 0) {
            return;
        }

        setEnchantLevel(enchant, getEnchantLevel(enchant) + amount);
    }

    public Map<String, Integer> getPickaxeEnchants() {
        return pickaxeEnchants;
    }

    public void clearPickaxeEnchants() {
        pickaxeEnchants.clear();
    }

    public String getCurrentMine() {
        return currentMine;
    }

    public void setCurrentMine(String currentMine) {
        if (currentMine == null || currentMine.isBlank()) {
            this.currentMine = String.valueOf(rank);
            return;
        }

        this.currentMine = currentMine;
    }

    public void syncCurrentMineToRank() {
        this.currentMine = String.valueOf(rank);
    }
}