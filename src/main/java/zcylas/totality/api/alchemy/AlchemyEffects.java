package zcylas.totality.api.alchemy;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import zcylas.totality.Totality;

/**
 * All alchemy effects in Totality.
 *
 * Base magnitudes follow Skyrim's unskilled brew values:
 *   Restore effects     — 0.22f (22% of max value)
 *   Fortify effects     — 0.17f magnitude, 1200 ticks (60s) duration
 *   Resist effects      — 0.13f magnitude, 1200 ticks (60s) duration
 *   Harmful damage      — 0.13f (13% of max value)
 *   Waterbreathing      — 5160 ticks (258 seconds)
 *   Regenerate Mana     — 0.22f boost, 1200 ticks (60s)
 *
 * When the Alchemy skill is added, apply a skill multiplier to magnitude/duration.
 */
public final class AlchemyEffects {

    // ── Restoration ───────────────────────────────────────────────────────────

    public static final AlchemyEffect RESTORE_HEALTH = AlchemyEffect.register(
                    id("restore_health"), "Restore Health",
                    0.22f, 0, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                // Eat: heal 5% of max HP
                entity.heal(entity.getMaxHealth() * 0.05f);
            })
            .withDrinkEffect((entity, magnitude) -> {
                entity.heal(entity.getMaxHealth() * magnitude);
            });

    public static final AlchemyEffect FORTIFY_HEALTH = AlchemyEffect.register(
                    id("fortify_health"), "Fortify Health",
                    0.17f, 1200, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                int amplifier = hpPercentToAbsorptionAmplifier(entity, 0.04f);
                entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 3 * 20, amplifier, false, true, true));
            })
            .withDrinkEffect((entity, magnitude) -> {
                int amplifier = hpPercentToAbsorptionAmplifier(entity, magnitude);
                entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, amplifier, false, true, true));
            });

    public static final AlchemyEffect RESTORE_STAMINA = AlchemyEffect.register(
            id("restore_stamina"), "Restore Stamina",
            0.22f, 0, AlchemyEffectType.BENEFICIAL
    );
    // Wired to stamina system when added

    public static final AlchemyEffect FORTIFY_STAMINA = AlchemyEffect.register(
            id("fortify_stamina"), "Fortify Stamina",
            0.17f, 1200, AlchemyEffectType.BENEFICIAL
    );

    public static final AlchemyEffect FORTIFY_CONJURATION = AlchemyEffect.register(
            id("fortify_conjuration"), "Fortify Conjuration",
            0.22f, 1200, AlchemyEffectType.BENEFICIAL
    );
    // Conjuration spells last 22% longer — wired when spell system supports it

    public static final AlchemyEffect RESTORE_MANA = AlchemyEffect.register(
            id("restore_mana"), "Restore Mana",
            0.22f, 0, AlchemyEffectType.BENEFICIAL
    );
    // Wired to mana system when added

    public static final AlchemyEffect FORTIFY_MANA = AlchemyEffect.register(
            id("fortify_mana"), "Fortify Mana",
            0.17f, 1200, AlchemyEffectType.BENEFICIAL
    );
    // Mana increased by 17% for 60 seconds — wired to mana system

    public static final AlchemyEffect REGENERATE_HEALTH = AlchemyEffect.register(
                    id("regenerate_health"), "Regenerate Health",
                    0.22f, 1200, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 3 * 20, 0, false, true, true));
            })
            .withTimedDrinkEffect((entity, ticks) -> {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, 0, false, true, true));
            });

    public static final AlchemyEffect REGENERATE_MANA = AlchemyEffect.register(
            id("regenerate_mana"), "Regenerate Mana",
            0.22f, 1200, AlchemyEffectType.BENEFICIAL
    );
    // Mana regenerates 22% faster for 60 seconds — wired to mana regen system

    // ── Harmful ───────────────────────────────────────────────────────────────

    public static final AlchemyEffect DAMAGE_HEALTH = AlchemyEffect.register(
                    id("damage_health"), "Damage Health",
                    0.13f, 0, AlchemyEffectType.HARMFUL
            )
            .withConsumeEffect(entity -> {
                entity.hurt(entity.level().damageSources().magic(), entity.getMaxHealth() * 0.05f);
            })
            .withDrinkEffect((entity, magnitude) -> {
                entity.hurt(entity.level().damageSources().magic(), entity.getMaxHealth() * magnitude);
            });

    public static final AlchemyEffect DAMAGE_STAMINA_REGEN = AlchemyEffect.register(
            id("damage_stamina_regen"), "Damage Stamina Regen",
            0.13f, 1200, AlchemyEffectType.HARMFUL
    );

    public static final AlchemyEffect DAMAGE_MANA_REGEN = AlchemyEffect.register(
            id("damage_mana_regen"), "Damage Mana Regen",
            0.13f, 1200, AlchemyEffectType.HARMFUL
    );

    public static final AlchemyEffect LINGERING_DAMAGE_MANA = AlchemyEffect.register(
            id("lingering_damage_mana"), "Lingering Damage Mana",
            0.13f, 1200, AlchemyEffectType.HARMFUL
    );

    public static final AlchemyEffect DAMAGE_STAMINA = AlchemyEffect.register(
            id("damage_stamina"), "Damage Stamina",
            0.13f, 0, AlchemyEffectType.HARMFUL
    );

    public static final AlchemyEffect WEAKNESS_TO_MAGIC = AlchemyEffect.register(
            id("weakness_to_magic"), "Weakness to Magic",
            0.13f, 1200, AlchemyEffectType.HARMFUL
    );

    public static final AlchemyEffect RAVAGE_MANA = AlchemyEffect.register(
            id("ravage_mana"), "Ravage Mana",
            0.13f, 1200, AlchemyEffectType.HARMFUL
    );

    // ── Neutral ───────────────────────────────────────────────────────────────

    public static final AlchemyEffect WATERBREATHING = AlchemyEffect.register(
                    id("waterbreathing"), "Waterbreathing",
                    0f, 5160, AlchemyEffectType.NEUTRAL   // 258 seconds
            )
            .withConsumeEffect(entity -> {
                entity.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 3 * 20, 0, false, true, true));
            })
            .withTimedDrinkEffect((entity, ticks) -> {
                entity.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, ticks, 0, false, true, true));
            });

    public static final AlchemyEffect INVISIBILITY = AlchemyEffect.register(
                    id("invisibility"), "Invisibility",
                    0f, 1200, AlchemyEffectType.NEUTRAL    // 60 seconds base
            )
            .withConsumeEffect(entity -> {
                entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 3 * 20, 0, false, true, true));
            })
            .withTimedDrinkEffect((entity, ticks) -> {
                entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, true, true));
            });

    // ── Combat ────────────────────────────────────────────────────────────────

    public static final AlchemyEffect FORTIFY_ONE_HANDED = AlchemyEffect.register(
            id("fortify_one_handed"), "Fortify One-Handed",
            0.17f, 1200, AlchemyEffectType.BENEFICIAL
    );

    // ── Purple / Red Mountain Flower ──────────────────────────────────────────

    public static final AlchemyEffect FORTIFY_SNEAK = AlchemyEffect.register(
            id("fortify_sneak"), "Fortify Sneak",
            0.17f, 1200, AlchemyEffectType.BENEFICIAL
    );

    public static final AlchemyEffect RESIST_FROST = AlchemyEffect.register(
            id("resist_frost"), "Resist Frost",
            0.13f, 1200, AlchemyEffectType.BENEFICIAL
    );

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Converts a percentage of max HP to an Absorption amplifier.
     * Absorption I = 4 HP, II = 8 HP etc.
     */
    private static int hpPercentToAbsorptionAmplifier(net.minecraft.world.entity.LivingEntity entity, float percent) {
        float targetHp = entity.getMaxHealth() * percent;
        int amplifier = (int)(targetHp / 4.0f) - 1;
        return Math.max(0, amplifier);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private AlchemyEffects() {}

    public static void register() {}

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, name);
    }
}