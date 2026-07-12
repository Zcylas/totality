package zcylas.totality.api.magic.spell;

/** Client-side mirror of the local player's {@link SpellSlotComponent}, kept in sync by
 *  {@link SpellSlotComponent#applySyncPacket} — used by the Spell radial HUD to draw
 *  the available/used slot pips without needing a server round-trip per frame. */
public final class ClientSpellSlotManager {

    private static final int[] maxSlots  = new int[SpellSlotComponent.MAX_SPELL_LEVEL];
    private static final int[] usedSlots = new int[SpellSlotComponent.MAX_SPELL_LEVEL];

    public static void apply(int[] newMax, int[] newUsed) {
        System.arraycopy(newMax,  0, maxSlots,  0, SpellSlotComponent.MAX_SPELL_LEVEL);
        System.arraycopy(newUsed, 0, usedSlots, 0, SpellSlotComponent.MAX_SPELL_LEVEL);
    }

    public static int getMax(int spellLevel) {
        int i = spellLevel - 1;
        return (i >= 0 && i < SpellSlotComponent.MAX_SPELL_LEVEL) ? maxSlots[i] : 0;
    }

    public static int getRemaining(int spellLevel) {
        int i = spellLevel - 1;
        if (i < 0 || i >= SpellSlotComponent.MAX_SPELL_LEVEL) return 0;
        return maxSlots[i] - usedSlots[i];
    }

    private ClientSpellSlotManager() {}
}
