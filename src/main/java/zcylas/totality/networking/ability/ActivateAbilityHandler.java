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
import zcylas.totality.api.rpg.combat.CastingRestrictionRegistry;
import zcylas.totality.api.rpg.resources.PartitionSelectionPolicy;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceCost;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
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

    /** Public (not {@code private}), correction pass (2026-09-16, Finding 4), so a dev-only
     *  verification (a different package, like every other production entry point these
     *  verifications already call — {@code PlayerResourceService.trySpend}, {@code
     *  BarbarianRageAbility.registerChargePool}, etc.) can exercise the real successful-cast-only
     *  ordering directly against this real production entry point, rather than reimplementing this
     *  control flow inside the verification. */
    public static void handle(ServerPlayer player, ActivateAbilityPayload payload) {
        AbilityComponent comp = AbilityComponents.ABILITIES.get(
                (ComponentProvider) player);

        if (!comp.hasAbility(payload.abilityId())) return;
        if (comp.isOnCooldown(payload.abilityId())) return;

        Ability ability = AbilityRegistry.get(payload.abilityId());
        if (ability == null) return;

        // Computed once, before onActivate runs (so it reflects the spell's state as of the
        // moment this activation began, not after onActivate may have mutated it — see Crown of
        // Stars, whose own onActivate flips a mote-remaining counter) and reused for BOTH the
        // pre-check below and the post-cast spend at the bottom of this method, so the two can
        // never disagree. Defaults to false for every ability that isn't a Spell, and for every
        // Spell that doesn't override Spell#isActiveInstanceAction — i.e. every existing spell's
        // behavior is unchanged.
        boolean chargesSlot = ability instanceof Spell spell
                && !spell.isCantrip() && !spell.isActiveInstanceAction(player);

        if (ability instanceof Spell spell) {
            String restriction = CastingRestrictionRegistry.check(player);
            if (restriction != null) {
                SendNotificationPayload.send(player, restriction, 0xFFFF4444);
                return;
            }
            // Cantrips are free; leveled spells need an unspent slot at their own level. No
            // upcast tier picker yet — always consumes at the spell's own minimum level. Phase 6
            // migration (2026-09-16): totality:spell_slots is now GENERIC_COMPONENT-authority —
            // this is a pure availability query (mirrors the retired SpellSlotComponent.hasSlot
            // exactly), never a mutation; the slot itself is only ever spent below, after the cast
            // has actually resolved. A follow-up action on an already-active instance (Crown of
            // Stars firing a mote) is exempt — see chargesSlot above.
            if (chargesSlot) {
                ResourceQueryResult slotsQuery = PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.SPELL_SLOTS);
                boolean hasSlot = slotsQuery instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(spell.getSpellLevel())
                                .map(partition -> partition.currentUnits() > 0)
                                .orElse(false);
                if (!hasSlot) {
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
        if (castSucceeded && ability instanceof Spell spell && chargesSlot) {
            // Successful-cast-only commitment preserved exactly: this only runs after
            // ability.onActivate has resolved and Spell.didCastSucceed() confirmed the cast actually
            // took effect (see the pre-check above for the "does a slot exist" query). EXACT_TIER
            // spends precisely the selected spell's own level — no upcast, no fallback tier search.
            // Gated on the same chargesSlot computed before onActivate ran, not a fresh
            // !spell.isCantrip() re-check, so a follow-up action (Crown of Stars firing a mote)
            // can never be charged here even though onActivate's own state mutation already
            // happened by this point.
            PlayerResourceService.INSTANCE.trySpend(player,
                    new ResourceCost.Partitioned(PlayerResourceIds.SPELL_SLOTS, spell.getSpellLevel(), 1, PartitionSelectionPolicy.EXACT_TIER),
                    ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST)));
        }
    }

    private ActivateAbilityHandler() {}
}