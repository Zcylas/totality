package zcylas.totality.api.alchemy;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import zcylas.totality.Totality;

/**
 * Central registry of all alchemy effects in Totality.
 *
 * Consume effects (raw ingredient eaten) — weak and brief (~3 seconds).
 * Drink effects (brewed potion consumed) — full strength and duration.
 *
 * All magnitudes are percentage-based (fraction of entity.getMaxHealth())
 * so they scale automatically if max HP changes later.
 *
 * When the Alchemy skill is added, apply a skill multiplier to the
 * percentage before computing the final value.
 */
public final class AlchemyEffects {

    // -------------------------------------------------------------------------
    // Restoration
    // -------------------------------------------------------------------------

    public static final AlchemyEffect RESTORE_HEALTH = AlchemyEffect.register(
                    id("restore_health"), "Restore Health",
                    5.0f, 0, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                // Eat: heal 5% of max HP instantly (half heart at default 20 HP)
                float heal = entity.getMaxHealth() * 0.05f;
                entity.heal(heal);
            })
            .withDrinkEffect(entity -> {
                // Drink: heal 25% of max HP instantly (5 HP / 2.5 hearts at default 20 HP)
                float heal = entity.getMaxHealth() * 0.25f;
                entity.heal(heal);
            });

    public static final AlchemyEffect FORTIFY_HEALTH = AlchemyEffect.register(
                    id("fortify_health"), "Fortify Health",
                    4.0f, 1200, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                // Eat: absorption equal to 4% of max HP for 3 seconds (0.8 HP at default 20 HP)
                int amplifier = hpPercentToAbsorptionAmplifier(entity, 0.04f);
                entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 3 * 20, amplifier, false, true, true));
            })
            .withDrinkEffect(entity -> {
                // Drink: absorption equal to 20% of max HP for 60 seconds (4 HP / 2 hearts at default 20 HP)
                int amplifier = hpPercentToAbsorptionAmplifier(entity, 0.20f);
                entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60 * 20, amplifier, false, true, true));
            });

    public static final AlchemyEffect RESTORE_STAMINA = AlchemyEffect.register(
            id("restore_stamina"), "Restore Stamina",
            5.0f, 0, AlchemyEffectType.BENEFICIAL
    );
    // Stamina hooks into sprint/exhaustion system — wired when that system exists

    public static final AlchemyEffect FORTIFY_STAMINA = AlchemyEffect.register(
            id("fortify_stamina"), "Fortify Stamina",
            4.0f, 1200, AlchemyEffectType.BENEFICIAL
    );

    /** Strengthens Conjuration spells temporarily. */
    public static final AlchemyEffect FORTIFY_CONJURATION = AlchemyEffect.register(
            id("fortify_conjuration"), "Fortify Conjuration",
            4.0f, 1200, AlchemyEffectType.BENEFICIAL
    );

    public static final AlchemyEffect RESTORE_MANA = AlchemyEffect.register(
            id("restore_mana"), "Restore Mana",
            5.0f, 0, AlchemyEffectType.BENEFICIAL
    );
    // Wired to mana system when added

    public static final AlchemyEffect FORTIFY_MANA = AlchemyEffect.register(
            id("fortify_mana"), "Fortify Mana",
            4.0f, 1200, AlchemyEffectType.BENEFICIAL
    );

    public static final AlchemyEffect REGENERATE_HEALTH = AlchemyEffect.register(
                    id("regenerate_health"), "Regenerate Health",
                    25.0f, 6000, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                // Eat: Regeneration I for 3 seconds
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 3 * 20, 0, false, true, true));
            })
            .withDrinkEffect(entity -> {
                // Drink: Regeneration I for 60 seconds
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60 * 20, 0, false, true, true));
            });

    public static final AlchemyEffect REGENERATE_MANA = AlchemyEffect.register(
            id("regenerate_mana"), "Regenerate Mana",
            25.0f, 6000, AlchemyEffectType.BENEFICIAL
    );
    // Wired to mana regen system when added

    // -------------------------------------------------------------------------
    // Harmful
    // -------------------------------------------------------------------------

    public static final AlchemyEffect DAMAGE_HEALTH = AlchemyEffect.register(
                    id("damage_health"), "Damage Health",
                    3.0f, 0, AlchemyEffectType.HARMFUL
            )
            .withConsumeEffect(entity -> {
                // Eat: deal 5% of max HP as magic damage instantly
                float damage = entity.getMaxHealth() * 0.05f;
                entity.hurt(entity.level().damageSources().magic(), damage);
            })
            .withDrinkEffect(entity -> {
                // Drink (as poison): deal 15% of max HP as magic damage
                float damage = entity.getMaxHealth() * 0.15f;
                entity.hurt(entity.level().damageSources().magic(), damage);
            });

    public static final AlchemyEffect DAMAGE_STAMINA_REGEN = AlchemyEffect.register(
            id("damage_stamina_regen"), "Damage Stamina Regen",
            100.0f, 600, AlchemyEffectType.HARMFUL
    );

    public static final AlchemyEffect DAMAGE_MANA_REGEN = AlchemyEffect.register(
            id("damage_mana_regen"), "Damage Mana Regen",
            100.0f, 600, AlchemyEffectType.HARMFUL
    );

    public static final AlchemyEffect LINGERING_DAMAGE_MANA = AlchemyEffect.register(
            id("lingering_damage_mana"), "Lingering Damage Mana",
            2.0f, 200, AlchemyEffectType.HARMFUL
    );

    // -------------------------------------------------------------------------
    // Neutral
    // -------------------------------------------------------------------------

    public static final AlchemyEffect WATERBREATHING = AlchemyEffect.register(
                    id("waterbreathing"), "Waterbreathing",
                    1.0f, 1200, AlchemyEffectType.NEUTRAL
            )
            .withConsumeEffect(entity -> {
                // Eat: Water Breathing for 3 seconds
                entity.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 3 * 20, 0, false, true, true));
            })
            .withDrinkEffect(entity -> {
                // Drink: Water Breathing for 60 seconds (faithful to Skyrim)
                entity.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60 * 20, 0, false, true, true));
            });

    public static final AlchemyEffect INVISIBILITY = AlchemyEffect.register(
                    id("invisibility"), "Invisibility",
                    1.0f, 600, AlchemyEffectType.NEUTRAL
            )
            .withConsumeEffect(entity -> {
                // Eat: Invisibility for 3 seconds
                entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 3 * 20, 0, false, true, true));
            })
            .withDrinkEffect(entity -> {
                // Drink: Invisibility for 30 seconds
                entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30 * 20, 0, false, true, true));
            });

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------


    // -------------------------------------------------------------------------
    // Combat (no callbacks yet — wired when combat system is in place)
    // -------------------------------------------------------------------------

    /** Increases one-handed weapon damage temporarily. */
    public static final AlchemyEffect FORTIFY_ONE_HANDED = AlchemyEffect.register(
            id("fortify_one_handed"), "Fortify One-Handed",
            4.0f, 1200, AlchemyEffectType.BENEFICIAL
    );

    /** Reduces target's stamina directly. */
    public static final AlchemyEffect DAMAGE_STAMINA = AlchemyEffect.register(
            id("damage_stamina"), "Damage Stamina",
            3.0f, 0, AlchemyEffectType.HARMFUL
    );

    /** Target takes increased magic damage. */
    public static final AlchemyEffect WEAKNESS_TO_MAGIC = AlchemyEffect.register(
            id("weakness_to_magic"), "Weakness to Magic",
            100.0f, 600, AlchemyEffectType.HARMFUL
    );

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a percentage of max HP to an Absorption amplifier.
     * Absorption I = 4 HP, II = 8 HP, etc.
     * We clamp to amplifier 0 minimum (4 HP) since that's the smallest useful amount.
     *
     * Formula: amplifier = floor((maxHp * percent) / 4) - 1, min 0
     */
    private static int hpPercentToAbsorptionAmplifier(net.minecraft.world.entity.LivingEntity entity, float percent) {
        float targetHp = entity.getMaxHealth() * percent;
        int amplifier = (int)(targetHp / 4.0f) - 1;
        return Math.max(0, amplifier);
    }

    // ── New effects for Purple and Red Mountain Flower ────────────────────────

    public static final AlchemyEffect FORTIFY_SNEAK = AlchemyEffect.register(
            id("fortify_sneak"), "Fortify Sneak",
            4.0f, 1200, AlchemyEffectType.BENEFICIAL
    );

    public static final AlchemyEffect RESIST_FROST = AlchemyEffect.register(
            id("resist_frost"), "Resist Frost",
            4.0f, 1200, AlchemyEffectType.BENEFICIAL
    );

    public static final AlchemyEffect RAVAGE_MANA = AlchemyEffect.register(
            id("ravage_mana"), "Ravage Mana",
            3.0f, 600, AlchemyEffectType.HARMFUL
    );

    // -------------------------------------------------------------------------

    private AlchemyEffects() {}

    public static void register() {}

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, name);
    }
}