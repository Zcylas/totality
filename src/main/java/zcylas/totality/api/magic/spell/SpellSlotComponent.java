package zcylas.totality.api.magic.spell;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.api.rpg.rest.RestListener;
import zcylas.totality.api.rpg.rest.RestType;

/**
 * Tracks spell slot availability for a player.
 * Slots are indexed 0–9 (spell levels 1–10). Level 0 = cantrips, no slots.
 *
 * Long rest → {@link #restoreAll()}. Warlock short rest → {@link #restoreSome} (not yet wired —
 * Pact Magic is a separate pool this component doesn't model, see {@link CasterProgression}).
 * Max slots per level set via {@link #recalculate} from {@link SpellSlotTable}
 * ({@link SpellSlotRecalculator} drives this from class levels).
 */
public final class SpellSlotComponent implements SyncedComponent, CopyableComponent<SpellSlotComponent>, RestListener {

    public static final int MAX_SPELL_LEVEL = 10;

    private final ServerPlayer player;
    private final int[] maxSlots  = new int[MAX_SPELL_LEVEL];
    private final int[] usedSlots = new int[MAX_SPELL_LEVEL];

    /** Server-side constructor. Null on client. */
    public SpellSlotComponent(ServerPlayer player) {
        this.player = player;
    }

    // ── Recalculate ───────────────────────────────────────────────────────────

    public void recalculate(int[] slots) {
        for (int i = 0; i < MAX_SPELL_LEVEL; i++) {
            maxSlots[i]  = i < slots.length ? slots[i] : 0;
            usedSlots[i] = Math.min(usedSlots[i], maxSlots[i]);
        }
        sync();
    }

    // ── Slot access ───────────────────────────────────────────────────────────

    public boolean hasSlot(int spellLevel) {
        int i = spellLevel - 1;
        return i >= 0 && i < MAX_SPELL_LEVEL && usedSlots[i] < maxSlots[i];
    }

    public boolean useSlot(int spellLevel) {
        int i = spellLevel - 1;
        if (i < 0 || i >= MAX_SPELL_LEVEL || usedSlots[i] >= maxSlots[i]) return false;
        usedSlots[i]++;
        sync();
        return true;
    }

    public int getMax(int spellLevel)       { int i=spellLevel-1; return (i>=0&&i<MAX_SPELL_LEVEL)?maxSlots[i]:0; }
    public int getUsed(int spellLevel)      { int i=spellLevel-1; return (i>=0&&i<MAX_SPELL_LEVEL)?usedSlots[i]:0; }
    public int getRemaining(int spellLevel) { return getMax(spellLevel) - getUsed(spellLevel); }

    // ── Rest restoration ──────────────────────────────────────────────────────

    public void restoreAll() {
        for (int i = 0; i < MAX_SPELL_LEVEL; i++) usedSlots[i] = 0;
        sync();
    }

    public void restoreSome(int count, int maxLevel) {
        int remaining = count;
        for (int i = Math.min(maxLevel, MAX_SPELL_LEVEL) - 1; i >= 0 && remaining > 0; i--) {
            int restore = Math.min(usedSlots[i], remaining);
            usedSlots[i] -= restore;
            remaining    -= restore;
        }
        sync();
    }

    // ── RestListener ──────────────────────────────────────────────────────────

    /** A Long Rest fully restores every caster's slots (D&D 2024 rule). Short Rest only
     *  matters for Warlock Pact Magic / Wizard Arcane Recovery — not wired yet (see class doc). */
    @Override
    public void onRest(ServerPlayer player, RestType type) {
        if (type == RestType.LONG) restoreAll();
    }

    // ── TotalityComponent ─────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        for (int i = 0; i < MAX_SPELL_LEVEL; i++) {
            output.putInt("max_"  + i, maxSlots[i]);
            output.putInt("used_" + i, usedSlots[i]);
        }
    }

    @Override
    public void readData(ValueInput input) {
        for (int i = 0; i < MAX_SPELL_LEVEL; i++) {
            maxSlots[i]  = input.getIntOr("max_"  + i, 0);
            usedSlots[i] = input.getIntOr("used_" + i, 0);
        }
    }

    // ── SyncedComponent ───────────────────────────────────────────────────────

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        for (int i = 0; i < MAX_SPELL_LEVEL; i++) {
            buf.writeByte(maxSlots[i]);
            buf.writeByte(usedSlots[i]);
        }
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        for (int i = 0; i < MAX_SPELL_LEVEL; i++) {
            maxSlots[i]  = buf.readByte();
            usedSlots[i] = buf.readByte();
        }
        ClientSpellSlotManager.apply(maxSlots, usedSlots);
    }

    private void sync() {
        if (player != null && !player.level().isClientSide()) {
            SpellSlotComponents.SPELL_SLOTS.sync((ComponentProvider) player);
        }
    }

    // ── CopyableComponent ─────────────────────────────────────────────────────

    @Override
    public void copyFrom(SpellSlotComponent other, HolderLookup.Provider registries) {
        System.arraycopy(other.maxSlots,  0, this.maxSlots,  0, MAX_SPELL_LEVEL);
        System.arraycopy(other.usedSlots, 0, this.usedSlots, 0, MAX_SPELL_LEVEL);
    }
}