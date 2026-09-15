package zcylas.totality.api.rpg.mana;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.rpg.mana.base.*;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceAmount;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceTarget;
import zcylas.totality.api.rpg.resources.integration.PlayerBaselineResources;


/**
 * Phase 4 migration (2026-09-15): compatibility facade over {@link PlayerResourceService} —
 * canonical §24.3's worked example, applied to Mana. {@code totality:mana} is now the authoritative,
 * {@code GENERIC_COMPONENT}-owned current value; this class no longer stores anything itself. Every
 * public method signature below is unchanged from the legacy authoritative implementation, so every
 * existing caller (spell casting, Heat Vision, alchemy, admin commands, ...) keeps working exactly
 * as before with zero call-site changes. {@link #getMaxMana}/{@link #getRegenPercent}/{@link
 * #calculateRegenAmount} are pure formula computations, untouched by this migration — {@link
 * ManaMaximumResolver} delegates back to {@link #getMaxMana} rather than duplicating it, so the
 * formula lives in exactly one place.
 *
 * <p>Operation mapping: {@link #removeMana} always clamps at the floor rather than rejecting a
 * too-large request (matches legacy exactly — no current caller ever treats "spent less than
 * requested" as a distinct failure from "the separate, prior {@link #hasMana} check already
 * happened"), so it maps to {@code drain}, not the all-or-nothing {@code trySpend}. {@link
 * #addMana}/{@link #removeMana} use {@code ADMIN_COMMAND}-neutral cause types
 * ({@code PASSIVE_REGENERATION}/{@code SPELL_COST}) since no canonical event bus or cause-branching
 * logic consumes {@link ResourceContext#cause()} yet (deferred, per the pre-Phase-4 foundation
 * report) — the exact choice is documentation/diagnostics only today, not behavior-affecting. {@link
 * #setMana} maps to the privileged {@code set} operation ({@code ADMIN_COMMAND} cause): its only
 * remaining callers after this migration are {@code PlayerResourceRecalculator}'s max-decrease clamp
 * and the two admin/debug "reset stats" commands — both genuinely privileged corrections, matching
 * canonical §12.2's own framing for {@code set}.
 *
 * <p>Every method self-heals via {@link PlayerBaselineResources#ensureInstantiated} before touching
 * Generic state — reproducing the legacy getters' own lazy-initialize-on-first-access behavior
 * (canonical query semantics forbid a read silently instantiating state; this legacy-facade layer is
 * explicitly exempted from that rule, exactly as it always behaved) without an ad hoc default: state
 * is instantiated through the real grant mechanism, not fabricated here. This also keeps dev-only
 * self-tests that construct a {@code ServerPlayer} via {@code TotalityFakePlayer} (which never fires
 * {@code ServerPlayConnectionEvents.JOIN}) working unchanged.
 */
public class PlayerManaManager {
    public static final int BASE_MAX_MANA = 100;
    // 2% of max mana per regen tick (every 20 ticks = 1 second)
    public static final float BASE_REGEN_PERCENT = 0.02f;

    public static int getMana(Player player) {
        if (!(player instanceof ServerPlayer sp)) return 0;
        PlayerBaselineResources.ensureInstantiated(sp);
        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(sp, PlayerResourceIds.MANA);
        return result instanceof ResourceQueryResult.Success success ? Math.toIntExact(success.snapshot().currentUnits()) : 0;
    }

    public static void setMana(Player player, int amount) {
        if (!(player instanceof ServerPlayer sp)) return;
        PlayerBaselineResources.ensureInstantiated(sp);
        PlayerResourceService.INSTANCE.set(sp, ResourceTarget.scalar(PlayerResourceIds.MANA, amount),
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.ADMIN_COMMAND)));
    }

    public static void addMana(Player player, int amount) {
        if (!(player instanceof ServerPlayer sp)) return;
        PlayerBaselineResources.ensureInstantiated(sp);
        PlayerResourceService.INSTANCE.restore(sp, ResourceAmount.scalar(PlayerResourceIds.MANA, amount),
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.PASSIVE_REGENERATION)));
    }

    public static void removeMana(Player player, int amount) {
        if (player.isCreative()) return;
        if (!(player instanceof ServerPlayer sp)) return;
        PlayerBaselineResources.ensureInstantiated(sp);
        PlayerResourceService.INSTANCE.drain(sp, ResourceAmount.scalar(PlayerResourceIds.MANA, amount),
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST)));
    }

    public static boolean hasMana(Player player, int amount) {
        if (player.isCreative()) return true;
        return getMana(player) >= amount;
    }

    public static int getMaxMana(Player player) {
        int max = BASE_MAX_MANA;

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            max += zcylas.totality.api.rpg.stats.StatsComponents.getStats(serverPlayer)
                    .getMaxManaBonus();
        }

        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ManaItem manaItem) {
                max += manaItem.getMaxMana(stack, ManaSource.ARMOR);
            }
        }
        if (player.hasEffect(zcylas.totality.init.ModEffects.FORTIFY_MANA)) {
            net.minecraft.world.effect.MobEffectInstance inst =
                    player.getEffect(zcylas.totality.init.ModEffects.FORTIFY_MANA);
            if (inst != null) {
                max += inst.getAmplifier() + 1;
            }
        }

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty() || !(stack.getItem() instanceof ManaItem manaItem))
                continue;
            boolean alreadyCounted = false;
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (player.getItemBySlot(slot).is(stack.getItem())) {
                    alreadyCounted = true;
                    break;
                }
            }
            if (!alreadyCounted)
                max += manaItem.getMaxMana(stack, ManaSource.ITEM);
        }

        MaxManaCalcEvent event = new MaxManaCalcEvent(player, max);
        ManaEvents.postMaxMana(event);
        return event.getMax();
    }

    public static float getRegenPercent(Player player) {
        float regen = BASE_REGEN_PERCENT;

        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ManaRegenItem regenItem) {
                regen += regenItem.getManaRegenMultiplier(stack, ManaSource.ARMOR);
            }
        }
        if (player.hasEffect(zcylas.totality.init.ModEffects.REGENERATE_MANA)) {
            net.minecraft.world.effect.MobEffectInstance inst =
                    player.getEffect(zcylas.totality.init.ModEffects.REGENERATE_MANA);
            if (inst != null) {
                regen *= (1f + (inst.getAmplifier() + 1) / 100f);
            }
        }

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty() || !(stack.getItem() instanceof ManaRegenItem regenItem))
                continue;
            boolean alreadyCounted = false;
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (player.getItemBySlot(slot).is(stack.getItem())) {
                    alreadyCounted = true;
                    break;
                }
            }
            if (!alreadyCounted)
                regen += regenItem.getManaRegenMultiplier(stack, ManaSource.ITEM);
        }

        ManaRegenCalcEvent event = new ManaRegenCalcEvent(player, regen);
        ManaEvents.postManaRegen(event);
        return event.getRegenPercent();
    }

    public static int calculateRegenAmount(Player player) {
        int max = getMaxMana(player);
        float percent = getRegenPercent(player);
        return Math.max(1, (int)(max * percent));
    }
}
