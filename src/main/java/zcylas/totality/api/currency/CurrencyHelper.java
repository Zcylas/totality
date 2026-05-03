package zcylas.totality.api.currency;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for converting raw currency values into denominations.
 */
public final class CurrencyHelper {

    private CurrencyHelper() {}

    /**
     * Breaks a raw value into the minimum number of coins across all denominations,
     * highest denomination first.
     *
     * Example: 10350 → [1 Gold, 3 Silver, 50 Bronze]
     */
    public static List<CoinCount> breakdown(long rawValue) {
        List<CoinCount> result = new ArrayList<>();
        long remaining = rawValue;

        // Iterate from highest to lowest denomination
        Denomination[] denoms = Denomination.VALUES;
        for (int i = denoms.length - 1; i >= 0; i--) {
            Denomination denom = denoms[i];
            long count = remaining / denom.baseValue;
            if (count > 0) {
                result.add(new CoinCount(denom, count));
                remaining -= count * denom.baseValue;
            }
        }

        return result;
    }

    /**
     * Formats a raw value as a human-readable string.
     * Example: "1 Gold, 3 Silver, 50 Bronze"
     */
    public static String format(long rawValue) {
        List<CoinCount> counts = breakdown(rawValue);
        if (counts.isEmpty()) return "0 Bronze";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < counts.size(); i++) {
            CoinCount cc = counts.get(i);
            sb.append(cc.count()).append(" ").append(cc.denomination().displayName);
            if (i < counts.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    public record CoinCount(Denomination denomination, long count) {}
}