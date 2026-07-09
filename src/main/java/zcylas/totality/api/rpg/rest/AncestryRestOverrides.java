package zcylas.totality.api.rpg.rest;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-species Long Rest duration overrides (Elf Trance, etc.). Keyed by
 * SPECIES id rather than Origin id — Trance is a biological elf trait
 * shared by every elf Origin (High Elf, Wood Elf, Dark Elf, Dunmer, Drow),
 * not an individually-granted cultural feature.
 */
public final class AncestryRestOverrides {

    private static final Map<Identifier, Integer> LONG_REST_TICKS_BY_SPECIES = new HashMap<>();

    static {
        // Elf Trance (real 5e rule): 4-hour semiconscious Trance counts as a full Long Rest.
        LONG_REST_TICKS_BY_SPECIES.put(Identifier.fromNamespaceAndPath("totality", "elf"), 4000);
    }

    public static int getLongRestTicks(ServerPlayer player) {
        Identifier speciesId = AncestryComponents.get(player).getSpeciesId();
        if (speciesId == null) return RestDurations.DEFAULT_LONG_REST_TICKS;
        return LONG_REST_TICKS_BY_SPECIES.getOrDefault(speciesId, RestDurations.DEFAULT_LONG_REST_TICKS);
    }

    private AncestryRestOverrides() {}
}
