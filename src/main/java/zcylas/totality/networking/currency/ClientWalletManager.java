package zcylas.totality.networking.currency;

public final class ClientWalletManager {

    private static long value = 0;

    private ClientWalletManager() {}

    public static void sync(long newValue) {
        value = newValue;
    }

    public static long getValue() {
        return value;
    }
}