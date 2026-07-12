package zcylas.totality.networking.ability;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellSlotComponent;
import zcylas.totality.api.magic.spell.SpellSlotComponents;
import zcylas.totality.api.rpg.combat.CastingRestrictionRegistry;
import zcylas.totality.networking.notification.SendNotificationPayload;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class ActivateAbilityHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                ActivateAbilityPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> handle(player, payload));
                }
        );
    }

    private static void handle(ServerPlayer player, ActivateAbilityPayload payload) {
        AbilityComponent comp = AbilityComponents.ABILITIES.get(
                (ComponentProvider) player);

        if (!comp.hasAbility(payload.abilityId())) return;
        if (comp.isOnCooldown(payload.abilityId())) return;

        Ability ability = AbilityRegistry.get(payload.abilityId());
        if (ability == null) return;

        if (ability instanceof Spell spell) {
            String restriction = CastingRestrictionRegistry.check(player);
            if (restriction != null) {
                SendNotificationPayload.send(player, restriction, 0xFFFF4444);
                return;
            }
            // Cantrips are free; leveled spells need an unspent slot at their own level. No
            // upcast tier picker yet — always consumes at the spell's own minimum level.
            if (!spell.isCantrip()) {
                SpellSlotComponent slots = SpellSlotComponents.get(player);
                if (!slots.hasSlot(spell.getSpellLevel())) {
                    SendNotificationPayload.send(player,
                            "No " + spell.getLevelDisplay() + " spell slots remaining.", 0xFFFF4444);
                    return;
                }
            }
        }

        // Reconstruct context from the block pos the client sent
        AbilityContext context = null;
        if (payload.pos() != null) {
            BlockPos pos = payload.pos();

            double maxRange = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0;
            if (!player.blockPosition().closerThan(pos, maxRange)) return;

            BlockState state = player.level().getBlockState(pos);
            context = new AbilityContext(pos, state, ability.getDisplayName());
        }

        if (!ability.canActivate(player, context)) {
            return;
        }

        if (ability instanceof Spell) Spell.resetCastResult();
        ability.onActivate(player, context);
        boolean castSucceeded = !(ability instanceof Spell) || Spell.didCastSucceed();

        if (castSucceeded && ability.getCooldownTicks() > 0) {
            comp.startCooldown(payload.abilityId());
        }
        if (castSucceeded && ability instanceof Spell spell && !spell.isCantrip()) {
            SpellSlotComponents.get(player).useSlot(spell.getSpellLevel());
        }
    }

    private ActivateAbilityHandler() {}
}