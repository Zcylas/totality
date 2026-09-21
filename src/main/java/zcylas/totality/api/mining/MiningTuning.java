package zcylas.totality.api.mining;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * EVERY provisional number and formula of the Block Breaking API lives here. Nothing in this
 * class is final balance — each constant is a tuning value chosen only so V1 is testable, and
 * nothing outside this class may hard-code a threshold, multiplier or timing.
 *
 * <p>The three mining axes are deliberately independent:
 * <ul>
 *   <li><b>Mining Power</b> — structural damage per successful impact (§ Mining Power).</li>
 *   <li><b>Mining Tier</b> — whether the active source may damage/harvest the material (§ Mining Tier).</li>
 *   <li><b>Mining Cadence</b> — how often normal swings happen (§ Cadence). The ONLY place vanilla
 *       break-speed modifiers (Efficiency, Haste, Fatigue, ...) are allowed to matter.</li>
 * </ul>
 */
public final class MiningTuning {

    private MiningTuning() {}

    // ── Block Durability (fallback formula) ──────────────────────────────────────────────────
    /** Max Durability = vanilla hardness x this. Stone (hardness 1.5) -> 100. PROVISIONAL. */
    public static final float DURABILITY_PER_HARDNESS = 100f / 1.5f;

    // ── Mining Power ─────────────────────────────────────────────────────────────────────────
    /** Structural damage per unit of the held tool's INTRINSIC mining speed (iron pickaxe on Stone: 6 x 6 = 36). PROVISIONAL. */
    public static final float TOOL_STRUCTURAL_PER_SPEED = 6f;
    /** Base structural damage of an eligible non-tool (hand) strike. */
    public static final float BARE_HAND_BASE_DAMAGE = 1f;
    /** No impact ever deals less than this, so negative STR modifiers cannot produce negative damage. */
    public static final float MIN_IMPACT_DAMAGE = 0.1f;

    /** Intrinsic-tool structural base: the held item's own effectiveness on this block only. */
    public static float toolBaseDamage(float intrinsicToolSpeed) {
        return intrinsicToolSpeed * TOOL_STRUCTURAL_PER_SPEED;
    }

    /** Normal (non-power) impact damage = max(min, base + STR modifier). Same formula for tools and hands;
     *  only the base differs. Efficiency/Haste/Fatigue never appear here. */
    public static float impactDamage(float base, int strModifier) {
        return Math.max(MIN_IMPACT_DAMAGE, base + strModifier);
    }

    /** Power Mining damage multiplier: 1 + force, force clamped 0..1 (so 1..2). STR is NOT applied again. */
    public static float powerDamageMultiplier(float force) {
        return 1f + clamp01(force);
    }

    public static float clamp01(float v) { return Float.isNaN(v) ? 0f : Math.max(0f, Math.min(1f, v)); }

    // ── Mining Tier ──────────────────────────────────────────────────────────────────────────
    public static final int TIER_MAX = 5;

    /** Tier of an unarmed actor from DEX (provisional; later Physiology/techniques/Permanent Buffs):
     *  DEX 10 -> 0, 12 -> 1, 16 -> 2, 20 -> 3, 24 -> 4, 28 -> 5. */
    public static int bareHandTier(int dex) {
        return Math.max(0, Math.min(TIER_MAX, (dex - 8) / 4));
    }

    // ── Cadence (normal swings only) ─────────────────────────────────────────────────────────
    /** Contact happens at this fraction of the item's SWING_ANIMATION duration. PROVISIONAL. */
    public static final float CONTACT_FRACTION = 0.5f;
    /** Ticks between contact and the next swing being allowed to begin, at cadence ratio 1. PROVISIONAL. */
    public static final int RECOVERY_TICKS = 7;
    /** Floors: contact never lands on the swing's own tick, and a swing cycle is never shorter than 3 ticks. */
    public static final int MIN_WINDUP_TICKS = 2;
    public static final int MIN_RECOVERY_TICKS = 1;
    /** Cadence ratio (vanilla break speed / intrinsic tool speed) is clamped to this range, which bounds
     *  every timing: no zero-tick swings, no packet spam, no effectively-infinite Fatigue stalls. */
    public static final float MIN_CADENCE_RATIO = 0.05f;
    public static final float MAX_CADENCE_RATIO = 20f;
    /** Power swings wind up this much longer than a normal swing. PROVISIONAL. */
    public static final float POWER_WINDUP_MULTIPLIER = 2.0f;

    public static float clampCadenceRatio(float ratio) {
        if (Float.isNaN(ratio)) return 1f;
        return Math.max(MIN_CADENCE_RATIO, Math.min(MAX_CADENCE_RATIO, ratio));
    }

    /** Wind-up ticks. Normal swings scale with cadence; power swings never do (a deliberate committed action). */
    public static int windUpTicks(int swingDuration, boolean power, float cadenceRatio) {
        int base = Math.max(MIN_WINDUP_TICKS, Math.round(swingDuration * CONTACT_FRACTION));
        if (power) return Math.round(base * POWER_WINDUP_MULTIPLIER);
        return Math.max(MIN_WINDUP_TICKS, Math.round(base / clampCadenceRatio(cadenceRatio)));
    }

    public static int recoveryTicks(float cadenceRatio) {
        return Math.max(MIN_RECOVERY_TICKS, Math.round(RECOVERY_TICKS / clampCadenceRatio(cadenceRatio)));
    }

    /** Whole normal-swing cycle length (wind-up + recovery). */
    public static int cycleTicks(int swingDuration, float cadenceRatio) {
        return windUpTicks(swingDuration, false, cadenceRatio) + recoveryTicks(cadenceRatio);
    }

    // ── Power Mining (force selection) ───────────────────────────────────────────────────────
    /** Meter oscillation: ticks for one full 0 -> 1 -> 0 sweep. PROVISIONAL. */
    public static final int METER_PERIOD_TICKS = 40;
    /** A power hold longer than this is discarded server-side. */
    public static final int MAX_POWER_HOLD_TICKS = 200;
    /** Force at/above this (0..1) is drawn as the red zone. */
    public static final float RED_ZONE = 0.8f;

    /** Meter value 0 -> 1 -> 0 for a hold of {@code ticks}. The SERVER evaluates this from its own clock;
     *  the client only uses it to draw the meter. Deliberately NOT affected by Efficiency/Haste/Fatigue. */
    public static float meterValue(long ticks) {
        float phase = (ticks % METER_PERIOD_TICKS) / (METER_PERIOD_TICKS / 2f);
        return phase <= 1f ? phase : 2f - phase;
    }

    // ── Force load / tool stress (SEPARATE from structural damage) ───────────────────────────
    /** Physical load a swing puts through the tool, in Force Tolerance units: 1 at force 0, up to 1 + STR/10
     *  at force 1 (STR 10 -> 2, STR 100 -> 11). It never depends on damage, Efficiency, Haste or Fatigue. PROVISIONAL. */
    public static float forceLoad(float force, int str) {
        return 1f + clamp01(force) * Math.max(0f, str / 10f);
    }

    /** Provisional Force Tolerance per tool material, in force-load units. Distinct from item durability
     *  (gold is deliberately the softest transmitter regardless of speed/durability; netherite is not
     *  indestructible: load above its tolerance still overloads it). PROVISIONAL. */
    public static final float TOLERANCE_GOLD = 2.0f;
    public static final float TOLERANCE_WOOD = 2.5f;
    public static final float TOLERANCE_STONE = 3.5f;
    public static final float TOLERANCE_IRON = 6.0f;
    public static final float TOLERANCE_DIAMOND = 9.0f;
    public static final float TOLERANCE_NETHERITE = 16.0f;

    /** Material identified through the tool's OWN repair rule (data-driven), not an item list. */
    private static final Object[][] MATERIALS = {
            {Items.GOLD_INGOT, TOLERANCE_GOLD},
            {Items.OAK_PLANKS, TOLERANCE_WOOD},
            {Items.COBBLESTONE, TOLERANCE_STONE},
            {Items.IRON_INGOT, TOLERANCE_IRON},
            {Items.DIAMOND, TOLERANCE_DIAMOND},
            {Items.NETHERITE_INGOT, TOLERANCE_NETHERITE},
    };

    /** Force a tool can transmit safely. Falls back to a tier-based value for tools with no recognised repair material. */
    public static float forceTolerance(ItemStack tool) {
        for (Object[] m : MATERIALS) {
            if (tool.isValidRepairItem(new ItemStack((Item) m[0]))) return (Float) m[1];
        }
        return switch (MiningTier.ofTool(tool)) {
            case 0 -> 1.5f;
            case 1 -> TOLERANCE_WOOD;
            case 2 -> TOLERANCE_STONE;
            case 3 -> TOLERANCE_IRON;
            case 4 -> TOLERANCE_DIAMOND;
            default -> TOLERANCE_NETHERITE;
        };
    }

    /** Fraction of the tool's max durability lost when the load reaches its tolerance (the "soft" strain term). PROVISIONAL. */
    public static final float STRAIN_FRACTION = 0.0625f;
    /** Fraction of the tool's max durability lost per unit of load ABOVE tolerance. PROVISIONAL. */
    public static final float OVERLOAD_FRACTION_PER_LOAD = 0.2f;

    /**
     * EXTRA item damage (on top of the normal per-impact wear) from pushing {@code load} through {@code tool}:
     * {@code maxDamage x [STRAIN_FRACTION x min(load/tolerance, 1)^2 + OVERLOAD_FRACTION x max(0, load - tolerance)]}.
     * Load 1 (a normal swing) costs nothing.
     */
    public static int toolStress(ItemStack tool, float load) {
        if (!tool.isDamageableItem() || load <= 1f) return 0;
        float tolerance = forceTolerance(tool);
        float ratio = Math.min(load / tolerance, 1f);
        float fraction = STRAIN_FRACTION * ratio * ratio + OVERLOAD_FRACTION_PER_LOAD * Math.max(0f, load - tolerance);
        return (int) Math.ceil(fraction * tool.getMaxDamage());
    }

    // ── Durability accounting ────────────────────────────────────────────────────────────────
    /** V1 TOOL POLICY (not a universal rule): EXACTLY this much base item durability per successful tool impact, regardless of vanilla {@code Tool.damagePerBlock}.
     *  Future sources (drills, powered/magical tools, bare hands) will get their own explicit impact cost policy (energy, fuel, resource, body strain, none...).
     *  A future source/tool that wants a different rule must get an explicit Totality mining rule. */
    public static final int BASE_WEAR_PER_IMPACT = 1;

    /** Manual base wear for an outcome: only DAMAGED charges it. BROKEN is charged by the (normalised, see
     *  {@code BlockBreaking}) vanilla terminal break; MISS/DENIED/INEFFECTIVE/INVALID are free. */
    public static int baseWear(MiningResult.Outcome outcome, ItemStack tool) {
        return outcome == MiningResult.Outcome.DAMAGED && tool.isDamageableItem() ? BASE_WEAR_PER_IMPACT : 0;
    }

    // ── Power force bands: ONE mapping, used by text presentation, bare-hand body strain and the future HUD ──
    public static final int BAND_DEFAULT = 0;   // normal mining (no Power force)
    public static final int BAND_LOW = 1;       // force  0 .. LOW_FORCE_MAX      "safe"
    public static final int BAND_MID = 2;       // force LOW_FORCE_MAX .. MID_FORCE_MAX   "safe"
    public static final int BAND_ORANGE = 3;    // force MID_FORCE_MAX .. RED_ZONE        "orange"
    public static final int BAND_RED = 4;       // force >= RED_ZONE, or a tool overloaded   "red / danger"
    public static final float LOW_FORCE_MAX = 0.35f;
    public static final float MID_FORCE_MAX = 0.65f;
    /* ORANGE begins at MID_FORCE_MAX (0.65) and RED begins at RED_ZONE (0.8). Boundaries are PROVISIONAL. */

    /** Band of a force 0..1 (a tool that is overloaded is always RED). Presentation AND body-strain classification. */
    public static int presentationBand(float force, boolean overloaded) {
        float f = clamp01(force);
        if (overloaded || f >= RED_ZONE) return BAND_RED;
        if (f <= 0f) return BAND_DEFAULT;
        if (f < LOW_FORCE_MAX) return BAND_LOW;
        if (f < MID_FORCE_MAX) return BAND_MID;
        return BAND_ORANGE;
    }

    // ── Bare-hand Power Mining body strain (PROVISIONAL V1 test values; NOT final balance) ────
    /** Flat self-damage, in vanilla health points (Totality shows x5: 2 HP = 10, 6 HP = 30), for a SUCCESSFUL
     *  bare-hand Power impact in the ORANGE / RED band. Safe bands cost nothing. The body is deliberately NOT
     *  modelled as a Force Tolerance yet (future: Physiology, CON, buffs, gloves, techniques). */
    public static final float BODY_STRAIN_ORANGE = 2f;
    public static final float BODY_STRAIN_RED = 6f;

    /** Self-damage for an outcome/band: only a successful damaging contact (DAMAGED or BROKEN) can hurt. */
    public static float bodyStrain(MiningResult.Outcome outcome, int band) {
        if (outcome != MiningResult.Outcome.DAMAGED && outcome != MiningResult.Outcome.BROKEN) return 0f;
        return switch (band) {
            case BAND_RED -> BODY_STRAIN_RED;
            case BAND_ORANGE -> BODY_STRAIN_ORANGE;
            default -> 0f;
        };
    }

    // ── Recovery (lazy, timestamp based). Exact policy is NOT decided. PROVISIONAL. ──────────
    /** Ticks after the last impact before integrity starts to recover. */
    public static final long RECOVERY_DELAY_TICKS = 600;
    /** Fraction of max durability recovered per tick once recovery begins (full in 60s). */
    public static final float RECOVERY_FRACTION_PER_TICK = 1f / 1200f;
    /** How often loaded damaged blocks are re-synced/pruned. Must stay well under the client's 400 tick crack expiry. */
    public static final int SWEEP_INTERVAL_TICKS = 40;
    /** Integrity left when the terminal break was denied, so the next impact retries the break. */
    public static final float DENIED_BREAK_INTEGRITY = 0.001f;
}
