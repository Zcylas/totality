package zcylas.totality.client.spell;

import org.jetbrains.annotations.Nullable;

/**
 * Tracks the ID of the last spell the player activated.
 * Right-clicking an {@link zcylas.totality.item.spell_material.ArcaneFocusItem}
 * will re-cast this spell using the focus's own casting sound.
 *
 * Updated from {@link zcylas.totality.networking.TotalityClientPacketHandlers}
 * when an {@link zcylas.totality.networking.ability.ActivateAbilityPayload}
 * is intercepted for a spell (abilities in SpellRegistry).
 */
public final class ClientSelectedSpellManager {

    @Nullable
    private static String selectedSpellId = null;

    private ClientSelectedSpellManager() {}

    public static void setSelectedSpell(String spellId) {
        selectedSpellId = spellId;
    }

    @Nullable
    public static String getSelectedSpell() {
        return selectedSpellId;
    }

    public static boolean hasSelection() {
        return selectedSpellId != null;
    }

    public static void clear() {
        selectedSpellId = null;
    }
}