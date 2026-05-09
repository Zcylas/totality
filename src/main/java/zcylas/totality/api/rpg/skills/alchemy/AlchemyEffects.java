package zcylas.totality.api.rpg.skills.alchemy;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import zcylas.totality.Totality;

/**
 * All alchemy effects in Totality.
 *
 * Base magnitudes follow Skyrim's unskilled brew values (flat points):
 *   Restore Health/Mana/Stamina — 22 points
 *   Fortify effects             — 0.17f magnitude, 1200 ticks (60s) duration
 *   Resist effects              — 0.13f magnitude, 1200 ticks (60s) duration
 *   Harmful damage              — 13 points
 *   Waterbreathing              — 5160 ticks (258 seconds)
 *   Regenerate Mana             — 0.22f boost, 1200 ticks (60s)
 *
 * When the Alchemy skill is added, apply a skill multiplier to magnitude/duration.
 */
public final class AlchemyEffects {

    // ── Restoration ───────────────────────────────────────────────────────────

    public static final AlchemyEffect RESTORE_HEALTH = AlchemyEffect.register(
                    id("restore_health"), "Restore Health",
                    22f, 0, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                // Eating raw ingredient: small fixed heal
                entity.heal(5f);
            })
            .withDrinkEffect((entity, magnitude) -> {
                // magnitude < 0 = ULTIMATE — fully restore
                float amount = magnitude < 0 ? entity.getMaxHealth() : magnitude;
                entity.heal(amount);
            })
            .withDescription((magnitude, ticks) ->
                    magnitude < 0
                            ? "Completely restore Health."
                            : String.format("Restore %.0f points of Health.", magnitude));

    public static final AlchemyEffect FORTIFY_HEALTH = AlchemyEffect.register(
                    id("fortify_health"), "Fortify Health",
                    0.17f, 1200, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                int amplifier = hpPercentToAbsorptionAmplifier(entity, 0.04f);
                entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 3 * 20, amplifier, false, true, true));
            })
            .withDrinkEffect((entity, magnitude) -> {
                // magnitude > 1 = flat points (FortifyTier), otherwise percentage (brewed)
                int amplifier = magnitude > 1
                        ? (int)(magnitude / 4f) - 1
                        : hpPercentToAbsorptionAmplifier(entity, magnitude);
                entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, Math.max(0, amplifier), false, true, true));
            })
            .withDescription((magnitude, ticks) ->
                    String.format("Health is increased by %.0f points for %d seconds.", magnitude, ticks / 20));

    public static final AlchemyEffect RESTORE_STAMINA = AlchemyEffect.register(
                    id("restore_stamina"), "Restore Stamina",
                    22f, 0, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                    zcylas.totality.api.rpg.stamina.PlayerStaminaManager.addStamina(player, 5);
                    zcylas.totality.networking.stamina.StaminaServerTick.syncStamina(player);
                }
            })
            .withDrinkEffect((entity, magnitude) -> {
                if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                    int max = zcylas.totality.api.rpg.stamina.PlayerStaminaManager.getMaxStamina(player);
                    int amount = magnitude < 0 ? max : Math.round(magnitude);
                    zcylas.totality.api.rpg.stamina.PlayerStaminaManager.addStamina(player, amount);
                    zcylas.totality.networking.stamina.StaminaServerTick.syncStamina(player);
                }
            })
            .withDescription((magnitude, ticks) ->
                    magnitude < 0 ? "Completely restore Stamina." : String.format("Restore %.0f points of Stamina.", magnitude));

    public static final AlchemyEffect FORTIFY_STAMINA = AlchemyEffect.register(
                    id("fortify_stamina"), "Fortify Stamina",
                    0.17f, 1200, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                if (entity instanceof net.minecraft.world.entity.player.Player player) {
                    int currentMax = zcylas.totality.api.rpg.stamina.PlayerStaminaManager.getMaxStamina(player);
                    zcylas.totality.effect.FortifyStaminaEffect.applyBonus(player, 0.04f, 3 * 20, currentMax);
                }
            })
            .withDrinkEffect((entity, magnitude) -> {
                if (entity instanceof net.minecraft.world.entity.player.Player player) {
                    int currentMax = zcylas.totality.api.rpg.stamina.PlayerStaminaManager.getMaxStamina(player);
                    zcylas.totality.effect.FortifyStaminaEffect.applyBonus(player, magnitude, 1200, currentMax);
                }
            })
            .withDescription((magnitude, ticks) ->
                    String.format("Stamina is increased by %.0f points for %d seconds.", magnitude, ticks / 20));

    public static final AlchemyEffect FORTIFY_CONJURATION = AlchemyEffect.register(
                    id("fortify_conjuration"), "Fortify Conjuration",
                    0.22f, 1200, AlchemyEffectType.BENEFICIAL
            )
            .withDescription((magnitude, ticks) ->
                    String.format("Conjuration spells last %.0f%% longer for %d seconds.", magnitude * 100, ticks / 20));
    // Wired when spell system supports it

    public static final AlchemyEffect RESTORE_MANA = AlchemyEffect.register(
                    id("restore_mana"), "Restore Mana",
                    22f, 0, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                    // Eating raw ingredient: small fixed mana restore
                    zcylas.totality.api.rpg.mana.PlayerManaManager.addMana(player, 5);
                    zcylas.totality.networking.mana.ManaServerTick.syncMana(player);
                }
            })
            .withDrinkEffect((entity, magnitude) -> {
                if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                    int max = zcylas.totality.api.rpg.mana.PlayerManaManager.getMaxMana(player);
                    // magnitude < 0 = ULTIMATE — fully restore
                    int amount = magnitude < 0 ? max : Math.round(magnitude);
                    zcylas.totality.api.rpg.mana.PlayerManaManager.addMana(player, amount);
                    zcylas.totality.networking.mana.ManaServerTick.syncMana(player);
                }
            })
            .withDescription((magnitude, ticks) ->
                    magnitude < 0
                            ? "Completely restore Mana."
                            : String.format("Restore %.0f points of Mana.", magnitude));

    public static final AlchemyEffect FORTIFY_MANA = AlchemyEffect.register(
                    id("fortify_mana"), "Fortify Mana",
                    0.17f, 1200, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                if (entity instanceof net.minecraft.world.entity.player.Player player) {
                    int currentMax = zcylas.totality.api.rpg.mana.PlayerManaManager.getMaxMana(player);
                    zcylas.totality.effect.FortifyManaEffect.applyBonus(player, 0.04f, 3 * 20, currentMax);
                }
            })
            .withDrinkEffect((entity, magnitude) -> {
                if (entity instanceof net.minecraft.world.entity.player.Player player) {
                    int currentMax = zcylas.totality.api.rpg.mana.PlayerManaManager.getMaxMana(player);
                    zcylas.totality.effect.FortifyManaEffect.applyBonus(player, magnitude, 1200, currentMax);
                }
            })
            .withDescription((magnitude, ticks) ->
                    String.format("Mana is increased by %.0f points for %d seconds.", magnitude, ticks / 20));

    public static final AlchemyEffect REGENERATE_HEALTH = AlchemyEffect.register(
                    id("regenerate_health"), "Regenerate Health",
                    0.22f, 1200, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 3 * 20, 0, false, true, true));
            })
            .withDrinkEffect((entity, magnitude) -> {
                // magnitude from RegenerateTier (0.50-1.00), amplifier scales regen speed
                int amplifier = Math.max(0, (int)(magnitude * 4) - 1);
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 6000, amplifier, false, true, true));
            })
            .withTimedDrinkEffect((entity, ticks) -> {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, 0, false, true, true));
            })
            .withDescription((magnitude, ticks) ->
                    String.format("Health regenerates %.0f%% faster for %d seconds.", magnitude * 100, ticks / 20));

    public static final AlchemyEffect REGENERATE_MANA = AlchemyEffect.register(
                    id("regenerate_mana"), "Regenerate Mana",
                    0.22f, 1200, AlchemyEffectType.BENEFICIAL
            )
            .withConsumeEffect(entity -> {
                if (entity instanceof net.minecraft.world.entity.player.Player player) {
                    zcylas.totality.effect.RegenerateManaEffect.applyBonus(player, 0.05f, 3 * 20);
                }
            })
            .withDrinkEffect((entity, magnitude) -> {
                // magnitude = regenBoost from RegenerateTier (0.50-1.00) or brewed (0.22)
                if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                    zcylas.totality.effect.RegenerateManaEffect.applyBonus(player, magnitude, 1200);
                }
            })
            .withFullDrinkEffect((entity, args) -> {
                // args[0]=regenBoost, args[1]=durationTicks from RegenerateTier
                if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                    zcylas.totality.effect.RegenerateManaEffect.applyBonus(player, args[0], (int) args[1]);
                }
            })
            .withDescription((magnitude, ticks) ->
                    String.format("Mana regenerates %.0f%% faster for %d seconds.", magnitude * 100, ticks / 20));

    // ── Harmful ───────────────────────────────────────────────────────────────

    public static final AlchemyEffect DAMAGE_HEALTH = AlchemyEffect.register(
                    id("damage_health"), "Damage Health",
                    13f, 0, AlchemyEffectType.HARMFUL
            )
            .withConsumeEffect(entity -> {
                entity.hurt(entity.level().damageSources().magic(), 5f);
            })
            .withDrinkEffect((entity, magnitude) -> {
                entity.hurt(entity.level().damageSources().magic(), magnitude);
            })
            .withDescription((magnitude, ticks) ->
                    String.format("Causes %.0f points of poison damage.", magnitude));

    public static final AlchemyEffect DAMAGE_STAMINA_REGEN = AlchemyEffect.register(
                    id("damage_stamina_regen"), "Damage Stamina Regen",
                    0.13f, 1200, AlchemyEffectType.HARMFUL
            )
            .withDescription((magnitude, ticks) ->
                    String.format("Stamina regenerates %.0f%% slower for %d seconds.", magnitude * 100, ticks / 20));

    public static final AlchemyEffect DAMAGE_MANA_REGEN = AlchemyEffect.register(
                    id("damage_mana_regen"), "Damage Mana Regen",
                    0.13f, 1200, AlchemyEffectType.HARMFUL
            )
            .withDescription((magnitude, ticks) ->
                    String.format("Mana regenerates %.0f%% slower for %d seconds.", magnitude * 100, ticks / 20));

    public static final AlchemyEffect LINGERING_DAMAGE_MANA = AlchemyEffect.register(
                    id("lingering_damage_mana"), "Lingering Damage Mana",
                    0.13f, 1200, AlchemyEffectType.HARMFUL
            )
            .withDescription((magnitude, ticks) ->
                    String.format("Drain mana for %.0f points over %d seconds.", magnitude, ticks / 20));

    public static final AlchemyEffect DAMAGE_STAMINA = AlchemyEffect.register(
                    id("damage_stamina"), "Damage Stamina",
                    13f, 0, AlchemyEffectType.HARMFUL
            )
            .withConsumeEffect(entity -> {
                if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                    zcylas.totality.api.rpg.stamina.PlayerStaminaManager.removeStamina(player, 5);
                    zcylas.totality.networking.stamina.StaminaServerTick.syncStamina(player);
                }
            })
            .withDrinkEffect((entity, magnitude) -> {
                if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                    zcylas.totality.api.rpg.stamina.PlayerStaminaManager.removeStamina(player, Math.round(magnitude));
                    zcylas.totality.networking.stamina.StaminaServerTick.syncStamina(player);
                }
            })
            .withDescription((magnitude, ticks) ->
                    String.format("Drain stamina for %.0f points.", magnitude));

    public static final AlchemyEffect WEAKNESS_TO_MAGIC = AlchemyEffect.register(
                    id("weakness_to_magic"), "Weakness to Magic",
                    0.13f, 1200, AlchemyEffectType.HARMFUL
            )
            .withDescription((magnitude, ticks) ->
                    String.format("Target is %.0f%% weaker to magic damage for %d seconds.", magnitude * 100, ticks / 20));

    public static final AlchemyEffect RAVAGE_MANA = AlchemyEffect.register(
                    id("ravage_mana"), "Ravage Mana",
                    0.13f, 1200, AlchemyEffectType.HARMFUL
            )
            .withDescription((magnitude, ticks) ->
                    String.format("Drain mana for %.0f points per second for %d seconds.", magnitude, ticks / 20));

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
            })
            .withDescription((magnitude, ticks) ->
                    String.format("Can breathe underwater for %d seconds.", ticks / 20));

    public static final AlchemyEffect INVISIBILITY = AlchemyEffect.register(
                    id("invisibility"), "Invisibility",
                    0f, 1200, AlchemyEffectType.NEUTRAL    // 60 seconds base
            )
            .withConsumeEffect(entity -> {
                entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 3 * 20, 0, false, true, true));
            })
            .withTimedDrinkEffect((entity, ticks) -> {
                entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, true, true));
            })
            .withDescription((magnitude, ticks) ->
                    String.format("Invisible for %d seconds.", ticks / 20));

    // ── Combat ────────────────────────────────────────────────────────────────

    public static final AlchemyEffect FORTIFY_ONE_HANDED = AlchemyEffect.register(
                    id("fortify_one_handed"), "Fortify One-Handed",
                    0.17f, 1200, AlchemyEffectType.BENEFICIAL
            )
            .withDescription((magnitude, ticks) ->
                    String.format("One-handed attacks do %.0f%% more damage for %d seconds.", magnitude * 100, ticks / 20));

    // ── Purple / Red Mountain Flower ──────────────────────────────────────────

    public static final AlchemyEffect FORTIFY_SNEAK = AlchemyEffect.register(
                    id("fortify_sneak"), "Fortify Sneak",
                    0.17f, 1200, AlchemyEffectType.BENEFICIAL
            )
            .withDescription((magnitude, ticks) ->
                    String.format("Sneaking is %.0f%% harder to detect for %d seconds.", magnitude * 100, ticks / 20));

    public static final AlchemyEffect RESIST_FROST = AlchemyEffect.register(
                    id("resist_frost"), "Resist Frost",
                    0.13f, 1200, AlchemyEffectType.BENEFICIAL
            )
            .withDescription((magnitude, ticks) ->
                    String.format("Resist %.0f%% of frost damage for %d seconds.", magnitude * 100, ticks / 20));

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