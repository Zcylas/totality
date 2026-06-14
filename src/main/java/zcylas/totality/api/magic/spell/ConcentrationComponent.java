package zcylas.totality.api.magic.spell;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.combat.SavingThrow;
import zcylas.totality.api.rpg.stats.AbilityScore;

/**
 * Tracks a player's active concentration spell.
 *
 * CON save on damage: DC = max(10, rawDamage / 2). Fail = concentration ends.
 * Casting a new concentration spell ends the previous one.
 * Death always ends concentration (NEVER_COPY respawn strategy).
 */
public final class ConcentrationComponent implements SyncedComponent, CopyableComponent<ConcentrationComponent> {

    @Nullable private final ServerPlayer player;
    @Nullable private Identifier activeSpellId = null;

    /** Server-side constructor. */
    public ConcentrationComponent(ServerPlayer player) {
        this.player = player;
    }

    // ── Start / end ───────────────────────────────────────────────────────────

    public void startConcentrating(Identifier spellId) {
        if (activeSpellId != null && !activeSpellId.equals(spellId)) {
            clearConcentration();
        }
        activeSpellId = spellId;
        sync();
    }

    public void endConcentration() {
        if (activeSpellId == null) return;
        // TODO: notify active spell so it can clean up its effects
        clearConcentration();
    }

    private void clearConcentration() {
        activeSpellId = null;
        sync();
    }

    // ── Damage handling ───────────────────────────────────────────────────────

    /** Returns true if concentration held, false if broken. */
    public boolean onDamageTaken(float rawDamage) {
        if (activeSpellId == null || player == null) return true;
        int dc = Math.max(10, (int)(rawDamage / 2));
        RollOutcome result = SavingThrow.roll(player, AbilityScore.CON, dc, RollType.NORMAL);
        if (!result.isSuccess()) {
            endConcentration();
            return false;
        }
        return true;
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    public boolean isConcentrating()                      { return activeSpellId != null; }
    public boolean isConcentratingOn(Identifier spellId) { return spellId.equals(activeSpellId); }
    @Nullable public Identifier getActiveSpellId()        { return activeSpellId; }

    // ── TotalityComponent ─────────────────────────────────────────────────────

    @Override
    public void readData(ValueInput input) {
        String id = input.getStringOr("active_spell", "");
        activeSpellId = id.isEmpty() ? null : Identifier.parse(id);
    }

    @Override
    public void writeData(ValueOutput output) {
        output.putString("active_spell", activeSpellId != null ? activeSpellId.toString() : "");
    }

    // ── SyncedComponent ───────────────────────────────────────────────────────

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeBoolean(activeSpellId != null);
        if (activeSpellId != null) buf.writeUtf(activeSpellId.toString());
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        boolean hasActive = buf.readBoolean();
        activeSpellId = hasActive ? Identifier.parse(buf.readUtf()) : null;
    }

    private void sync() {
        if (player != null && !player.level().isClientSide()) {
            ConcentrationComponents.CONCENTRATION.sync((ComponentProvider) player);
        }
    }

    // ── CopyableComponent ─────────────────────────────────────────────────────

    @Override
    public void copyFrom(ConcentrationComponent other, HolderLookup.Provider registries) {
        // Death ends concentration — NEVER_COPY, so this clears rather than copies
        this.activeSpellId = null;
    }
}