// api/rpg/ancestry/PlayerAncestryComponent.java
package zcylas.totality.api.rpg.ancestry;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.api.rpg.stats.*;

/**
 * Stores the player's chosen ancestry as Identifiers.
 * Null speciesId means the player has not yet selected an ancestry.
 * Persists on death. Synced to client.
 */
public class PlayerAncestryComponent implements SyncedComponent, CopyableComponent<PlayerAncestryComponent> {

    private Identifier speciesId    = null;
    private Identifier originId     = null;
    private float      heightScale  = 1.0f;
    private final ServerPlayer player;

    public PlayerAncestryComponent(ServerPlayer player) {
        this.player = player;
    }

    // ── Access ────────────────────────────────────────────────────────────────

    public Identifier getSpeciesId()    { return speciesId; }
    public Identifier getOriginId()     { return originId; }
    public float      getHeightScale()  { return heightScale; }
    public boolean    hasAncestry()     { return speciesId != null; }

    public SpeciesData getSpeciesData() {
        return speciesId != null ? SpeciesRegistry.get(speciesId) : null;
    }

    public OriginData getOriginData() {
        return originId != null ? OriginRegistry.get(originId) : null;
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    public void selectAncestry(Identifier chosenSpeciesId, Identifier chosenOriginId) {
        if (this.speciesId != null) return;
        if (chosenSpeciesId == null) return;

        SpeciesData species = SpeciesRegistry.get(chosenSpeciesId);
        if (species == null) return;

        OriginData origin = chosenOriginId != null ? OriginRegistry.get(chosenOriginId) : null;
        if (origin != null) {
            if (!origin.getSpeciesId().equals(chosenSpeciesId)) return;
            if (origin.getUnlockState() != UnlockState.UNLOCKED) return;
        }

        this.speciesId   = chosenSpeciesId;
        this.originId    = chosenOriginId;
        this.heightScale = origin != null
                ? origin.randomHeight(player.getRandom())
                : species.randomHeight(player.getRandom());

        applyBonuses(origin);
        player.refreshDimensions();
        sync();
    }

    public void clearAncestry() {
        this.speciesId   = null;
        this.originId    = null;
        this.heightScale = 1.0f;
        if (player != null) {
            PlayerStats stats = StatsComponents.getStats(player);
            stats.clearOriginBonus();
            PlayerResourceRecalculator.recalculate(player);
        }
        sync();
    }

    private void applyBonuses(OriginData origin) {
        if (player == null) return;
        PlayerStats stats = StatsComponents.getStats(player);
        stats.setOriginBonus(origin != null
                ? origin.getAbilityScoreBonus()
                : AbilityScoreBonus.NONE);
        PlayerResourceRecalculator.recalculate(player);
    }

    public void reapplyBonuses() {
        if (speciesId == null || player == null) return;
        applyBonuses(getOriginData());
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
        buf.writeBoolean(speciesId != null);
        if (speciesId != null) {
            buf.writeUtf(speciesId.toString());
            buf.writeBoolean(originId != null);
            if (originId != null) buf.writeUtf(originId.toString());
            buf.writeFloat(heightScale);
        }
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        boolean has = buf.readBoolean();
        if (has) {
            String speciesStr = buf.readUtf();
            boolean hasOrigin = buf.readBoolean();
            String originStr  = hasOrigin ? buf.readUtf() : null;
            float  scale      = buf.readFloat();
            speciesId   = parseIdentifier(speciesStr);
            originId    = originStr != null ? parseIdentifier(originStr) : null;
            heightScale = scale;
        } else {
            speciesId   = null;
            originId    = null;
            heightScale = 1.0f;
        }
        ClientAncestryManager.apply(speciesId, originId, heightScale);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        output.putString("species",    speciesId != null ? speciesId.toString() : "none");
        output.putString("origin",     originId  != null ? originId.toString()  : "none");
        output.putFloat("heightScale", heightScale);
    }

    @Override
    public void readData(ValueInput input) {
        String speciesStr = input.getStringOr("species", "none");
        String originStr  = input.getStringOr("origin",  "none");
        this.heightScale  = input.getFloatOr("heightScale", 1.0f);
        this.speciesId    = speciesStr.equals("none") ? null : parseIdentifier(speciesStr);
        this.originId     = originStr.equals("none")  ? null : parseIdentifier(originStr);
    }

    /**
     * Parses an Identifier from a stored string.
     * Handles backward compat: old enum names like "KRYPTONIAN" become "totality:kryptonian".
     */
    private static Identifier parseIdentifier(String stored) {
        if (stored == null || stored.isEmpty() || stored.equals("none") || stored.equals("NONE")) {
            return null;
        }
        // Backward compat: old enum names have no ":" separator
        if (!stored.contains(":")) {
            return Identifier.fromNamespaceAndPath("totality", stored.toLowerCase());
        }
        try {
            return Identifier.parse(stored);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Death copy ────────────────────────────────────────────────────────────

    @Override
    public void copyFrom(PlayerAncestryComponent other, HolderLookup.Provider registries) {
        this.speciesId   = other.speciesId;
        this.originId    = other.originId;
        this.heightScale = other.heightScale;
    }
}