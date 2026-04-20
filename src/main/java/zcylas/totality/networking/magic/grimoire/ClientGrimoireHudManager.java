package zcylas.totality.networking.magic.grimoire;

public class ClientGrimoireHudManager {
    private static int currentSlot = 0;
    private static String spellName = "";

    public static int getCurrentSlot() { return currentSlot; }
    public static String getSpellName() { return spellName; }

    public static void sync(int slot, String name) {
        currentSlot = slot;
        spellName = name == null ? "" : name;
    }
}