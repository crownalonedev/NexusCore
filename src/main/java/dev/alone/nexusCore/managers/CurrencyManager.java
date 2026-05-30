package dev.alone.nexusCore.managers;

import dev.alone.nexusCore.NexusCore;
import dev.alone.nexusCore.profiles.PlayerProfile;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CurrencyManager {

    private static final List<String> CURRENCIES = List.of("money", "tokens", "gems", "beacons");

    private static final Map<String, Integer> SUFFIX_POWERS = Map.ofEntries(
            Map.entry("k", 3),
            Map.entry("m", 6),
            Map.entry("b", 9),
            Map.entry("t", 12),
            Map.entry("q", 15),
            Map.entry("qa", 15),
            Map.entry("qi", 18),
            Map.entry("sx", 21),
            Map.entry("sp", 24),
            Map.entry("oc", 27),
            Map.entry("no", 30),
            Map.entry("dc", 33)
    );

    private final NexusCore plugin;

    public CurrencyManager(NexusCore plugin) {
        this.plugin = plugin;
    }

    public boolean isValidCurrency(String currency) {
        if (currency == null) {
            return false;
        }

        return CURRENCIES.contains(currency.toLowerCase(Locale.ROOT));
    }

    public List<String> getCurrencyNames() {
        return CURRENCIES;
    }

    public boolean isGangTopOnly(String currency) {
        if (currency == null) {
            return false;
        }

        return plugin.getConfig().getBoolean("currencies." + currency.toLowerCase(Locale.ROOT) + ".gang-top-only", false);
    }

    public String getDisplayName(String currency) {
        if (currency == null) {
            return "Unknown";
        }

        String normalized = currency.toLowerCase(Locale.ROOT);
        return plugin.getConfig().getString("currencies." + normalized + ".display-name", capitalize(normalized));
    }

    public String getSymbol(String currency) {
        if (currency == null) {
            return "";
        }

        return plugin.getConfig().getString("currencies." + currency.toLowerCase(Locale.ROOT) + ".symbol", "");
    }

    public BigDecimal parseAmount(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String cleaned = input
                .trim()
                .replace(",", "")
                .replace("_", "")
                .toLowerCase(Locale.ROOT);

        try {
            for (String suffix : getSuffixesByLength()) {
                if (!cleaned.endsWith(suffix)) {
                    continue;
                }

                String numberPart = cleaned.substring(0, cleaned.length() - suffix.length());

                if (numberPart.isBlank()) {
                    return null;
                }

                BigDecimal base = new BigDecimal(numberPart);
                BigDecimal multiplier = BigDecimal.TEN.pow(SUFFIX_POWERS.get(suffix));

                return base.multiply(multiplier).max(BigDecimal.ZERO);
            }

            return new BigDecimal(cleaned).max(BigDecimal.ZERO);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public void setCurrency(PlayerProfile profile, String currency, BigDecimal amount) {
        if (profile == null || !isValidCurrency(currency) || amount == null) {
            return;
        }

        String normalized = currency.toLowerCase(Locale.ROOT);
        BigDecimal safeAmount = amount.max(BigDecimal.ZERO);

        switch (normalized) {
            case "money" -> profile.setMoney(safeAmount);
            case "tokens" -> profile.setTokens(toWholeNumber(safeAmount));
            case "gems" -> profile.setGems(toWholeNumber(safeAmount));
            case "beacons" -> profile.setBeacons(toWholeNumber(safeAmount));
            default -> {
            }
        }
    }

    public void addCurrency(PlayerProfile profile, String currency, BigDecimal amount) {
        if (profile == null || !isValidCurrency(currency) || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String normalized = currency.toLowerCase(Locale.ROOT);

        switch (normalized) {
            case "money" -> profile.addMoney(amount);
            case "tokens" -> profile.addTokens(toWholeNumber(amount));
            case "gems" -> profile.addGems(toWholeNumber(amount));
            case "beacons" -> profile.addBeacons(toWholeNumber(amount));
            default -> {
            }
        }
    }

    public void takeCurrency(PlayerProfile profile, String currency, BigDecimal amount) {
        if (profile == null || !isValidCurrency(currency) || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String normalized = currency.toLowerCase(Locale.ROOT);

        switch (normalized) {
            case "money" -> profile.removeMoney(amount);
            case "tokens" -> profile.removeTokens(toWholeNumber(amount));
            case "gems" -> profile.removeGems(toWholeNumber(amount));
            case "beacons" -> profile.removeBeacons(toWholeNumber(amount));
            default -> {
            }
        }
    }

    public void resetCurrency(PlayerProfile profile, String currency) {
        if (profile == null || !isValidCurrency(currency)) {
            return;
        }

        setCurrency(profile, currency, BigDecimal.ZERO);
    }

    public String formatMoney(PlayerProfile profile) {
        if (profile == null) {
            return formatMoney(BigDecimal.ZERO);
        }

        return formatMoney(profile.getMoney());
    }

    public String formatTokens(PlayerProfile profile) {
        if (profile == null) {
            return formatWholeCurrency("tokens", BigInteger.ZERO);
        }

        return formatWholeCurrency("tokens", profile.getTokens());
    }

    public String formatGems(PlayerProfile profile) {
        if (profile == null) {
            return formatWholeCurrency("gems", BigInteger.ZERO);
        }

        return formatWholeCurrency("gems", profile.getGems());
    }

    public String formatBeacons(PlayerProfile profile) {
        if (profile == null) {
            return formatWholeCurrency("beacons", BigInteger.ZERO);
        }

        return formatWholeCurrency("beacons", profile.getBeacons());
    }

    public String formatCurrency(PlayerProfile profile, String currency) {
        if (profile == null || !isValidCurrency(currency)) {
            return "0";
        }

        return switch (currency.toLowerCase(Locale.ROOT)) {
            case "money" -> formatMoney(profile);
            case "tokens" -> formatTokens(profile);
            case "gems" -> formatGems(profile);
            case "beacons" -> formatBeacons(profile);
            default -> "0";
        };
    }

    public String getRawCurrency(PlayerProfile profile, String currency) {
        if (profile == null || !isValidCurrency(currency)) {
            return "0";
        }

        return switch (currency.toLowerCase(Locale.ROOT)) {
            case "money" -> profile.getMoney().toPlainString();
            case "tokens" -> profile.getTokens().toString();
            case "gems" -> profile.getGems().toString();
            case "beacons" -> profile.getBeacons().toString();
            default -> "0";
        };
    }

    public String formatMoney(BigDecimal amount) {
        String symbol = getSymbol("money");
        int decimals = Math.max(0, plugin.getConfig().getInt("currencies.money.decimal-places", 2));

        return symbol + formatNumber(amount, decimals);
    }

    private String formatWholeCurrency(String currency, BigInteger amount) {
        String symbol = getSymbol(currency);
        return symbol + formatNumber(new BigDecimal(amount), 0);
    }

    private String formatNumber(BigDecimal amount, int decimalPlaces) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }

        BigDecimal safeAmount = amount.max(BigDecimal.ZERO);
        boolean compact = plugin.getConfig().getBoolean("currencies.format.compact", true);

        if (!compact) {
            return formatWithCommas(safeAmount, decimalPlaces);
        }

        BigInteger wholePart = safeAmount.toBigInteger();
        int digits = wholePart.abs().toString().length();

        if (digits < 4) {
            return formatWithCommas(safeAmount, decimalPlaces);
        }

        List<String> suffixes = plugin.getConfig().getStringList("currencies.format.suffixes");

        if (suffixes.isEmpty()) {
            suffixes = List.of("", "K", "M", "B", "T", "Q", "Qi", "Sx", "Sp", "Oc", "No", "Dc");
        }

        int group = Math.min((digits - 1) / 3, suffixes.size() - 1);
        BigDecimal divisor = BigDecimal.TEN.pow(group * 3);
        BigDecimal shortened = safeAmount.divide(divisor, 2, RoundingMode.DOWN).stripTrailingZeros();

        return shortened.toPlainString() + suffixes.get(group);
    }

    private String formatWithCommas(BigDecimal amount, int decimalPlaces) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat format = new DecimalFormat(decimalPlaces > 0 ? "#,##0." + "0".repeat(decimalPlaces) : "#,##0", symbols);

        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(amount);
    }

    private BigInteger toWholeNumber(BigDecimal amount) {
        if (amount == null) {
            return BigInteger.ZERO;
        }

        return amount.setScale(0, RoundingMode.DOWN).toBigInteger().max(BigInteger.ZERO);
    }

    private List<String> getSuffixesByLength() {
        return SUFFIX_POWERS.keySet()
                .stream()
                .sorted((first, second) -> Integer.compare(second.length(), first.length()))
                .toList();
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1).toLowerCase(Locale.ROOT);
    }
}