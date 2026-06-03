// api/combat/damage/DamageResistanceRecalculator.java
package zcylas.totality.api.combat.damage;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.api.rpg.ancestry.OriginData;
import zcylas.totality.api.rpg.ancestry.OriginRegistry;
import zcylas.totality.api.rpg.ancestry.PlayerAncestryComponent;

public final class DamageResistanceRecalculator {

    private DamageResistanceRecalculator() {}

    public static void recalculate(ServerPlayer player) {
        DamageResistanceComponent comp = DamageResistanceComponent.get(player);
        comp.clear();

        // ── Species bonuses ───────────────────────────────────────────────────
        PlayerAncestryComponent ancestry = AncestryComponents.get(player);
        if (ancestry.hasAncestry()) {
            OriginData origin = ancestry.getOriginData();
            if (origin != null) applyOriginResistances(origin.getId(), comp);
        }




        // ── Class bonuses (future) ────────────────────────────────────────────
        // ClassComponents.get(player).applyResistances(comp);

        // ── Equipment bonuses (future) ────────────────────────────────────────
        // EquipmentResistanceApplier.apply(player, comp);

    }

    private static void applyOriginResistances(Identifier originId, DamageResistanceComponent comp) {
        if (originId.equals(OriginRegistry.KRYPTONIAN.getId())) {
            comp.addResistance(DamageTypes.RADIANT, false);
            // add more Kryptonian resistances here
        }
        // other origins...
    }
}