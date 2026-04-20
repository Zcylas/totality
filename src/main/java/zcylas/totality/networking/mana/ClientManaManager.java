package zcylas.totality.networking.mana;

public class ClientManaManager {
    private static int mana = 100;
    private static int maxMana = 100;

    public static int getMana() { return mana; }
    public static int getMaxMana() { return maxMana; }

    public static void sync(int mana, int maxMana) {
        ClientManaManager.mana = mana;
        ClientManaManager.maxMana = maxMana;
    }
}
