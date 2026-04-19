package zcylas.totality.api.energy;

public class UEFormat {

    public static String energy(long amount) {
        if (amount >= 1_000_000_000L) return (amount / 1_000_000_000L) + "T";
        if (amount >= 1_000_000L) return (amount / 1_000_000L) + "M";
        if (amount >= 1_000L) return (amount / 1_000L) + "k";
        return amount + "";
    }

    private UEFormat() {}
}