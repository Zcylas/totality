package zcylas.totality.api.magic.spell;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.rest.RestListener;
import zcylas.totality.api.rpg.rest.RestType;
import zcylas.totality.networking.resource.ResourceSyncManager;

/**
 * Legacy standard spell-slot store. Slots are indexed 0–8 (spell levels 1–9); level 0 = cantrips,
 * no slots. There is no ordinary 10th-level slot (Phase 6 canon, 2026-09-16) — see
 * {@link SpellSlotTable}'s class Javadoc.
 *
 * <p><b>Retired as a production mutation authority (Phase 6, 2026-09-16).</b> {@code totality:spell_slots}
 * is now {@code GENERIC_COMPONENT}-authority, mutated exclusively through {@link
 * zcylas.totality.api.rpg.resources.PlayerResourceService} (see {@code
 * zcylas.totality.api.rpg.resources.integration.StandardSpellSlotResources} for the grant/rest
 * wiring and {@code StandardSpellSlotMaximumResolver} for the maximum resolution this class used to
 * own via {@link #recalculate}). This class remains attached to every {@code ServerPlayer} and still
 * persists whatever it last held <b>solely</b> so {@code BaselineResourceLifecycleEvents}'s one-time
 * legacy-NBT import can still read a pre-migration player's remaining slots on their first post-Phase-6
 * join — mirroring {@code PlayerResourceComponent} (legacy Mana/Stamina) and {@code
 * PlayerChargesComponent} (legacy Rage), which remain attached and readable for exactly the same
 * reason after their own Phase 4/5 migrations. Nothing in production calls {@link #useSlot}/{@link
 * #recalculate}/{@link #restoreAll} anymore; {@link #onRest} is no longer registered as a {@link
 * RestListener} (see {@code PlayerConnectionEvents}). They are left in place, not deleted, matching
 * that same established precedent, and are exercised only by this class's own characterization tests.
 */
public final class SpellSlotComponent implements SyncedComponent, CopyableComponent<SpellSlotComponent>, RestListener {

    public static final int MAX_SPELL_LEVEL = 9;

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

    // ── RestListener ──────────────────────────────────────────────────────────

    /** Dead production code since Phase 6 (no longer registered as a {@link RestListener} — see the
     *  class Javadoc); retained only for this class's own characterization tests, matching {@link
     *  #useSlot}/{@link #recalculate}/{@link #restoreAll}'s own retained-but-unreferenced status. */
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

    /** Dead in production since Phase 6 (see the class Javadoc) — only ever invoked by this class's
     *  own now-unreferenced mutators, which only tests still call. */
    private void sync() {
        if (player != null && !player.level().isClientSide()) {
            SpellSlotComponents.SPELL_SLOTS.sync((ComponentProvider) player);
            ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.SPELL_SLOTS);
        }
    }

    // ── CopyableComponent ─────────────────────────────────────────────────────

    @Override
    public void copyFrom(SpellSlotComponent other, HolderLookup.Provider registries) {
        System.arraycopy(other.maxSlots,  0, this.maxSlots,  0, MAX_SPELL_LEVEL);
        System.arraycopy(other.usedSlots, 0, this.usedSlots, 0, MAX_SPELL_LEVEL);
    }
}