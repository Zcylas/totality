package zcylas.totality.api.rpg.race;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.api.rpg.skills.core.Skill;
import zcylas.totality.api.rpg.skills.core.SkillsComponents;
import zcylas.totality.api.rpg.race.ClientRaceManager;

import java.util.Map;

/**
 * Stores the player's chosen race.
 * Null means the player has not yet selected a race (triggers the selection screen).
 * Persists on death. Synced to client so overlays and HUD can read it.
 */
public class RaceComponent implements SyncedComponent, CopyableComponent<RaceComponent> {

    private Race race = null;
    private final ServerPlayer player;

    public RaceComponent(ServerPlayer player) {
        this.player = player;
    }

    // ── Access ────────────────────────────────────────────────────────────────

    /** Null means no race selected yet. */
    public Race getRace() {
        return race;
    }

    public boolean hasRace() {
        return race != null;
    }

    /**
     * Sets the race and applies its skill bonuses on top of current skill levels.
     * Should only be called once (on first selection). Does nothing if race already set.
     */
    public void selectRace(Race chosen) {
        if (this.race != null) return; // already chosen — permanent
        this.race = chosen;
        applyBonuses(chosen);
        sync();
    }

    /**
     * Dev-only: clears the race so the selection screen will reopen on next join.
     * Skill bonuses are NOT reversed — use only for testing.
     */
    public void clearRace() {
        this.race = null;
        sync();
    }

    private void applyBonuses(Race chosen) {
        if (player == null) return;
        var skillsComp = SkillsComponents.get(player);
        for (Map.Entry<Skill, Integer> entry : chosen.getSkillBonuses().entrySet()) {
            var data = skillsComp.getSkills().getData(entry.getKey());
            data.setLevelDirectly(10 + entry.getValue());
        }
        skillsComp.sync();
    }

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            RaceComponents.PLAYER_RACE.sync(
                    (zcylas.totality.api.core.component.ComponentProvider) player
            );
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeBoolean(race != null);
        if (race != null) {
            buf.writeUtf(race.name());
        }
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        boolean hasRace = buf.readBoolean();
        if (hasRace) {
            String name = buf.readUtf();
            try {
                race = Race.valueOf(name);
            } catch (IllegalArgumentException e) {
                race = null;
            }
        } else {
            race = null;
        }
        ClientRaceManager.apply(race);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        output.putString("race", race != null ? race.name() : "NONE");
    }

    @Override
    public void readData(ValueInput input) {
        String name = input.getStringOr("race", "NONE");
        if (name.equals("NONE")) {
            race = null;
        } else {
            try {
                race = Race.valueOf(name);
            } catch (IllegalArgumentException e) {
                race = null;
            }
        }
    }

    // ── Death copy ────────────────────────────────────────────────────────────

    @Override
    public void copyFrom(RaceComponent other, HolderLookup.Provider registries) {
        this.race = other.race;
        // Bonuses are already baked into skill levels, which PlayerSkillsComponent
        // also copies on death — no need to re-apply here.
    }

    /**
     * Reapplies this race's skill bonuses on top of base level 10.
     * Call after any skill reset so race bonuses are always the floor.
     */
    public void reapplyBonuses() {
        if (race == null || player == null) return;
        applyBonuses(race);
    }
}