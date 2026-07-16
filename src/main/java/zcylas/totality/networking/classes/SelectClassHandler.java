package zcylas.totality.networking.classes;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.magic.spell.SpellSlotRecalculator;
import zcylas.totality.api.rpg.classes.*;

public final class SelectClassHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                SelectClassPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> handle(player, payload));
                }
        );
    }

    private static void handle(ServerPlayer player, SelectClassPayload payload) {
        Identifier classId    = Identifier.parse(payload.classId());
        Identifier subclassId = payload.subclassId() != null
                ? Identifier.parse(payload.subclassId()) : null;
        Identifier covenantId = payload.covenantId() != null
                ? Identifier.parse(payload.covenantId()) : null;

        if (ClassRegistry.get(classId).isEmpty()) {
            Totality.LOGGER.warn("SelectClassHandler: unknown class {}", classId); return;
        }

        PlayerClassComponent comp = ClassComponents.get(player);

        // Don't allow re-selection once a class is chosen
        if (comp.hasAnyClass()) {
            Totality.LOGGER.warn("SelectClassHandler: {} already has a class", player.getName().getString());
            return;
        }

        comp.selectClass(classId, 1); // starts at 0 levels, gains levels via XP
        if (subclassId != null) comp.selectSubclass(subclassId);
        if (covenantId != null) comp.selectCovenant(covenantId);

        comp.sync();
        SpellSlotRecalculator.recalculate(player);
        if (classId.equals(TotalityClasses.BARBARIAN_ID)) {
            BarbarianRageAbility.registerChargePool(player);
            AbilityComponents.ABILITIES.get((ComponentProvider) player)
                    .unlock(BarbarianRageAbility.ID);
            AbilityComponents.ABILITIES.get((ComponentProvider) player)
                    .unlock(AbilityRegistry.BARBARIAN_UNARMORED_DEFENSE.getId()); // ← add this
            AbilityComponents.ABILITIES.sync((ComponentProvider) player);
        }
        Totality.LOGGER.info("Class selected: {} sub={} cov={} for {}",
                classId, subclassId, covenantId, player.getName().getString());
    }

    private SelectClassHandler() {}
}