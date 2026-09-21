package zcylas.totality.api.rpg.resources.food;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceAmount;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceOperationResult;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;

/**
 * The single translation point between vanilla's own {@code FoodData} engine (0-20, still the thing
 * that actually decides *when* Food changes via exhaustion thresholds and eating — Peaceful's own
 * automatic Food restore is disabled entirely, see {@code ServerPlayerPeacefulFoodRestoreAuthorityMixin})
 * and the true, authoritative {@code totality:food} {@code GENERIC_COMPONENT} resource (baseline 0-100, but
 * see {@link FoodMaximumResolver} — the resolved maximum is not architecturally fixed at 100). See
 * {@code Context/Audit/TOTALITY_FOOD_0_100_AND_TOTALITY_FOOD_ITEM_IMPLEMENTATION_REPORT_2026-09-17.md}.
 *
 * <h2>Two distinct kinds of math live here — do not conflate them</h2>
 * <p><b>Delta translation</b> ({@link #VANILLA_TO_TRUE_SCALE}, used by {@link #translateAndResync}
 * and the one-time save migration): a fixed, exact, absolute x5 — vanilla-triggered changes
 * (exhaustion decrementing by 1, eating N nutrition) always move the true
 * resource by a fixed absolute amount, the same way Pizza's authored +48 is a fixed absolute amount
 * regardless of a player's resolved maximum. This is intentional, not an oversight: Food deltas are
 * authored/derived as absolute amounts, never as a percentage of an exceptional player's capacity.
 *
 * <p><b>Corrected 2026-09-17 (second correction pass):</b> {@link #translateAndResync}'s
 * {@code vanillaDelta} parameter must always be the <b>intended/semantic</b> old-domain amount (the
 * literal nutrition value an item/effect/mechanic authors, e.g. an apple's {@code 4}, Cake's
 * {@code 2}, exhaustion's fixed {@code -1}) — <b>never</b> a delta
 * measured by reading vanilla's {@code foodLevel} field before and after letting vanilla's own
 * {@code eat(...)} run. Vanilla's {@code eat} internally clamps to {@code [0, 20]}
 * ({@code FoodData#add}), so a before/after measurement silently loses amount once the *mirror*
 * (not the true resource) is near its own ceiling — e.g. at true 140/150 (mirror 19/20), an apple's
 * real nutrition of 4 would measure as only "+1" once vanilla clamps its own field at 20, translating
 * to a wrong true +5 instead of the correct true +20. {@link #interceptFoodDataEat} now takes the
 * intended nutrition as an explicit parameter for exactly this reason — see its own Javadoc.
 *
 * <p><b>Mirror projection</b> ({@link #mirrorOf}): proportional, not fixed — {@code current / max}
 * mapped into vanilla's fixed 0-20 domain — but with a <b>locked endpoint invariant</b>, corrected
 * 2026-09-17 (second correction pass): {@code current <= 0} maps to exactly {@code 0} and
 * {@code current >= max} maps to exactly {@code 20}, full stop; every value strictly between maps to
 * a value strictly between {@code 1} and {@code 19}. A naive {@code round(current/max*20)} violates
 * this — e.g. {@code 1/100} would round to {@code 0} (falsely signaling "truly empty" to any vanilla
 * code that treats mirror {@code 0} as {@code needsFood()}-false/starving) and {@code 99/100} would
 * round to {@code 20} (falsely signaling "truly full" against vanilla's own starvation threshold at
 * the wrong boundary). See {@link #mirrorOf}'s own Javadoc for the exact corrected formula.
 *
 * <p>Every mixin that observes vanilla FoodData about to change its own {@code foodLevel} field by
 * some intended amount calls {@link #translateAndResync} with that exact amount (positive = restore,
 * negative = drain) and writes the returned value into vanilla's field instead of vanilla's own
 * uncorrected/lossy arithmetic. {@link #resyncMirrorIfStale} is the complementary safety net for
 * authoritative mutations that never go through a vanilla call site at all (Totality food items,
 * {@code /totality food set}, and any future Generic Food caller) — see
 * {@code zcylas.totality.networking.food.FoodMirrorServerTick}, which calls it once per player every
 * server tick. Together these make vanilla's {@code foodLevel} field a pure, always-consistent
 * write-back compatibility projection — never a second, independent authority.
 *
 * <p>If {@code totality:food} is not yet instantiated for this player (a synthetic/dev player built
 * outside the ordinary join lifecycle, so {@code BaselineResourceLifecycleEvents} never ran), this
 * falls back to vanilla's own uncorrected result rather than throwing or silently no-opping — the
 * player behaves exactly like pre-migration vanilla until real Food state exists.
 */
public final class FoodVanillaCompatibilityBridge {

    /** Vanilla's ceiling (20) to the true baseline ceiling (100) — the fixed, absolute exchange rate
     *  for vanilla-triggered DELTAS (exhaustion, eating) and the one-time save
     *  migration. Never used for the mirror's own current/max projection — see {@link #mirrorOf}. */
    public static final int VANILLA_TO_TRUE_SCALE = 5;

    /** The fixed size of vanilla's own compatibility domain — never changes, regardless of the
     *  authoritative resource's resolved maximum. */
    public static final int VANILLA_DOMAIN_MAXIMUM = 20;

    /**
     * @param vanillaDelta the intended/semantic old-domain amount an authored vanilla mechanic
     *                     represents (positive = restore, negative = drain) — e.g. an item's real
     *                     nutrition, or exhaustion's fixed {@code -1}. Must never be a value measured
     *                     by diffing vanilla's own (lossy, clamped-at-20) {@code foodLevel} field
     *                     before/after letting vanilla run — see this class's own Javadoc. {@code 0}
     *                     is a no-op fast path.
     * @param uncorrectedVanillaResult what vanilla's own arithmetic already computed for its field,
     *                                 used verbatim only if {@code totality:food} isn't instantiated.
     * @return the value to actually write into vanilla's {@code foodLevel} field.
     */
    public static int translateAndResync(ServerPlayer player, int vanillaDelta, int uncorrectedVanillaResult) {
        if (vanillaDelta == 0) {
            return uncorrectedVanillaResult;
        }

        ResourceAmount amount = ResourceAmount.scalar(PlayerResourceIds.FOOD,
                (long) Math.abs(vanillaDelta) * VANILLA_TO_TRUE_SCALE);
        ResourceContext context = ResourceContext.of(ResourceCause.of(vanillaDelta > 0
                ? ResourceContext.CauseTypes.FOOD_RESTORE
                : ResourceContext.CauseTypes.ENVIRONMENT_DRAIN));

        ResourceOperationResult result = vanillaDelta > 0
                ? PlayerResourceService.INSTANCE.restore(player, amount, context)
                : PlayerResourceService.INSTANCE.drain(player, amount, context);

        if (!(result instanceof ResourceOperationResult.Success success)) {
            return uncorrectedVanillaResult;
        }
        return mirrorOf(success.after().currentUnits(), success.after().maximumUnits());
    }

    /**
     * Maps {@code current / max} into vanilla's fixed {@code [0, 20]} domain, preserving the real
     * empty/full endpoints (corrected 2026-09-17, second correction pass): {@code current <= 0}
     * always maps to exactly {@code 0}, {@code current >= max} always maps to exactly {@code 20},
     * and every value strictly between {@code 0} and {@code max} always maps to a value strictly
     * between {@code 1} and {@code 19} (a plain proportional round, clamped into that interior range
     * so near-boundary interior values — e.g. {@code 1/100} or {@code 99/100} — can never be
     * misreported as the true endpoint). This matters because vanilla code treats mirror {@code 0}
     * as "empty" ({@code needsFood()}-false is the opposite; starvation's own {@code foodLevel <= 0}
     * check is exact) and mirror {@code 20} as "truly full" ({@code needsFood()} gates Peaceful's
     * restore branch on it) — a lossy naive rounding would misfire both.
     *
     * <p>Examples: {@code 0/100 -> 0}, {@code 1/100 -> 1} (not rounded down to {@code 0}),
     * {@code 50/100 -> 10}, {@code 99/100 -> 19} (not rounded up to {@code 20}), {@code 100/100 ->
     * 20}; identically {@code 0/150 -> 0}, {@code 1/150 -> 1}, {@code 75/150 -> 10},
     * {@code 149/150 -> 19}, {@code 150/150 -> 20}.
     */
    public static int mirrorOf(long trueFoodCurrent, long trueFoodMaximum) {
        if (trueFoodMaximum <= 0 || trueFoodCurrent <= 0) {
            return 0;
        }
        if (trueFoodCurrent >= trueFoodMaximum) {
            return VANILLA_DOMAIN_MAXIMUM;
        }
        long rounded = Math.round((double) trueFoodCurrent / trueFoodMaximum * VANILLA_DOMAIN_MAXIMUM);
        return (int) Math.max(1, Math.min(VANILLA_DOMAIN_MAXIMUM - 1, rounded));
    }

    /**
     * Self-healing safety net for authoritative Food mutations that never pass through a vanilla
     * call site (a {@code TotalityFoodItem} eating, {@code /totality food set}, and any future
     * Generic Food caller) — those never trigger {@link #translateAndResync}, so nothing would ever
     * refresh vanilla's own {@code foodLevel} field for them without this. Purely one-directional
     * (authoritative current/max -> mirror; never the reverse), so it can never create a feedback
     * loop and never mutates the authoritative value — only ever vanilla's own field, and only when
     * it has actually drifted from the correct projection. Called once per player every server tick
     * by {@code FoodMirrorServerTick}; cheap when nothing changed (one query, one int comparison).
     */
    public static void resyncMirrorIfStale(ServerPlayer player) {
        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.FOOD);
        if (!(result instanceof ResourceQueryResult.Success success)) {
            return;
        }
        int expectedMirror = mirrorOf(success.snapshot().currentUnits(), success.snapshot().maximumUnits());
        if (player.getFoodData().getFoodLevel() != expectedMirror) {
            player.getFoodData().setFoodLevel(expectedMirror);
        }
    }

    /**
     * Shared interception body for every vanilla call site that invokes {@code FoodData#eat(int,
     * float)} or {@code FoodData#eat(FoodProperties)} directly (ordinary item consumption, Cake, the
     * Saturation mob effect) — reused by {@code FoodPropertiesEatAuthorityMixin}, {@code
     * CakeBlockEatAuthorityMixin}, and {@code SaturationMobEffectEatAuthorityMixin} so the
     * apply/translate/resync sequence exists exactly once.
     *
     * <p><b>Corrected 2026-09-17 (second correction pass):</b> {@code intendedNutrition} must be the
     * real, authored nutrition value the caller is about to feed into vanilla's {@code eat(...)}
     * (e.g. the literal {@code food} argument, or a {@code FoodProperties}'s own
     * {@code nutrition()}) — this method no longer measures a before/after delta on vanilla's own
     * {@code foodLevel} field, because that field clamps at 20 and would silently under-report the
     * true restoration near vanilla's own ceiling (see this class's own Javadoc for the exact bug
     * this replaces). {@code applyVanillaEat} is still invoked — and must still be — so vanilla's own
     * Saturation side effect (a real, if temporary, part of the compatibility model) keeps updating
     * exactly as before; only the *authoritative Food amount* is now taken directly from
     * {@code intendedNutrition} rather than inferred from vanilla's own lossy field.
     *
     * <p><b>Corrected 2026-09-17 (Pizza/Saturation correction pass):</b> a zero {@code
     * intendedNutrition} now skips {@code applyVanillaEat} entirely instead of still running it.
     * {@code TotalityFoodItem} deliberately carries a zero-nutrition, zero-saturation
     * {@link net.minecraft.world.food.FoodProperties} (so it can reach this same shared translation
     * point via {@code FoodPropertiesEatAuthorityMixin}, purely to satisfy {@code
     * InventoryActionHandler}'s {@code stack.has(DataComponents.FOOD)} gate and {@code
     * Consumable#canConsume}'s {@code canAlwaysEat} check) — but the real decompiled 26.2
     * {@code FoodData#add(int, float)} is {@code this.saturationLevel = Mth.clamp(saturation +
     * this.saturationLevel, 0.0F, this.foodLevel)}: even a literal {@code add(0, 0.0F)} still clamps
     * {@code saturationLevel} down to the *current* {@code foodLevel} mirror. Since the mirror can be
     * arbitrarily lower than real Saturation at the moment a Totality food item is eaten (e.g. true
     * Food near 0, mirror near 0, Saturation still 8 from earlier play), running vanilla's own
     * zero-value {@code eat} was silently destroying real Saturation on every Totality food
     * consumption for no legitimate reason — nothing was actually being restored. A genuinely zero
     * intended nutrition has nothing for vanilla to legitimately apply, so skipping {@code
     * applyVanillaEat} entirely is a true no-op (not merely a same-value one): the mirror is
     * unaffected (nothing wrote to it) and {@code translateAndResync}'s own {@code vanillaDelta == 0}
     * fast path would have produced the identical mirror value anyway. {@code TotalityFoodItem}'s own
     * authored, temporary Saturation contribution is applied separately and deliberately in {@code
     * TotalityFoodItem#finishUsingItem} — never through this vanilla-mutation-triggered path.
     *
     * <p>Only translates for a {@code ServerPlayer} — the same call sites also run for non-player
     * {@code LivingEntity} targets (Cake) or client-side prediction, where vanilla's own {@code eat}
     * is left to run exactly as before with no resource-service call.
     */
    public static void interceptFoodDataEat(FoodData foodData, LivingEntity user, int intendedNutrition, Runnable applyVanillaEat) {
        if (intendedNutrition == 0) {
            return;
        }
        applyVanillaEat.run();
        int uncorrectedResult = foodData.getFoodLevel();
        if (user instanceof ServerPlayer player) {
            int mirrored = translateAndResync(player, intendedNutrition, uncorrectedResult);
            foodData.setFoodLevel(mirrored);
        }
    }

    private FoodVanillaCompatibilityBridge() {}
}
