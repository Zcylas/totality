package zcylas.totality.api.rpg.ancestry;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.api.rpg.stats.*;

/**
 * Stores the player's chosen ancestry (species + origin).
 * Null species means the player has not yet selected an ancestry.
 * Persists on death. Synced to client.
 */
public class PlayerAncestryComponent implements SyncedComponent, CopyableComponent<PlayerAncestryComponent> {

    private Species species = null;
    private Origin origin   = null;
    private float heightScale = 1.0f;
    private final ServerPlayer player;

    public PlayerAncestryComponent(ServerPlayer player) {
        this.player = player;
    }

    // ── Access ────────────────────────────────────────────────────────────────

    public Species getSpecies()       { return species; }
    public Origin getOrigin()         { return origin; }
    public float getHeightScale()     { return heightScale; }
    public boolean hasAncestry()      { return species != null; }

    /**
     * Selects species and origin.
     * Height scale is taken from origin if present, otherwise from species.
     * All stat bonuses come from origin only — species has no stats.
     */
    public void selectAncestry(Species chosenSpecies, Origin chosenOrigin) {
        if (this.species != null) return;
        if (chosenSpecies == null) return;
        if (chosenOrigin != null && chosenOrigin.getSpecies() != chosenSpecies) return;
        if (chosenOrigin != null && chosenOrigin.getDefaultUnlockState() != UnlockState.UNLOCKED) return;
        this.species = chosenSpecies;
        this.origin  = chosenOrigin;
        this.heightScale = chosenOrigin != null
                ? chosenOrigin.randomHeight(player.getRandom())
                : chosenSpecies.randomHeight(player.getRandom());
        applyBonuses(chosenOrigin);
        player.refreshDimensions();
        sync();
    }

    public void clearAncestry() {
        this.species     = null;
        this.origin      = null;
        this.heightScale = 1.0f;
        if (player != null) {
            PlayerStats stats = StatsComponents.getStats(player);
            stats.clearOriginBonus();
            PlayerResourceRecalculator.recalculate(player);
        }
        sync();
    }

    private void applyBonuses(Origin chosenOrigin) {
        if (player == null) return;
        PlayerStats stats = StatsComponents.getStats(player);
        stats.setOriginBonus(chosenOrigin != null
                ? chosenOrigin.getAbilityScoreBonus()
                : AbilityScoreBonus.NONE);
        PlayerResourceRecalculator.recalculate(player);
    }

    public void reapplyBonuses() {
        if (species == null || player == null) return;
        applyBonuses(origin);
    }

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            AncestryComponents.PLAYER_ANCESTRY.sync(
                    (zcylas.totality.api.core.component.ComponentProvider) player
            );
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeBoolean(species != null);
        if (species != null) {
            buf.writeUtf(species.name());
            buf.writeBoolean(origin != null);
            if (origin != null) buf.writeUtf(origin.name());
            buf.writeFloat(heightScale);
        }
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        boolean hasAncestry = buf.readBoolean();
        if (hasAncestry) {
            String speciesName = buf.readUtf();
            boolean hasOrigin  = buf.readBoolean();
            String originName  = hasOrigin ? buf.readUtf() : null;
            float scale        = buf.readFloat();
            try {
                species     = Species.valueOf(speciesName);
                origin      = originName != null ? Origin.valueOf(originName) : null;
                heightScale = scale;
            } catch (IllegalArgumentException e) {
                species     = null;
                origin      = null;
                heightScale = 1.0f;
            }
        } else {
            species     = null;
            origin      = null;
            heightScale = 1.0f;
        }
        ClientAncestryManager.apply(species, origin, heightScale);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        output.putString("species",    species != null ? species.name()  : "NONE");
        output.putString("origin",     origin  != null ? origin.name()   : "NONE");
        output.putFloat("heightScale", heightScale);
    }

    @Override
    public void readData(ValueInput input) {
        String speciesName = input.getStringOr("species", "NONE");
        String originName  = input.getStringOr("origin",  "NONE");
        this.heightScale   = input.getFloatOr("heightScale", 1.0f);
        this.species = speciesName.equals("NONE") ? null : safeSpecies(speciesName);
        this.origin  = originName.equals("NONE")  ? null : safeOrigin(originName);
    }

    private Species safeSpecies(String name) {
        try { return Species.valueOf(name); }
        catch (IllegalArgumentException e) { return null; }
    }

    private Origin safeOrigin(String name) {
        try { return Origin.valueOf(name); }
        catch (IllegalArgumentException e) { return null; }
    }

    // ── Death copy ────────────────────────────────────────────────────────────

    @Override
    public void copyFrom(PlayerAncestryComponent other, HolderLookup.Provider registries) {
        this.species     = other.species;
        this.origin      = other.origin;
        this.heightScale = other.heightScale;
    }
}