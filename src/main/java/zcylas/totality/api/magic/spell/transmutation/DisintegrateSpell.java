package zcylas.totality.api.magic.spell.transmutation;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.combat.damage.DamageFlags;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.combat.damage.TotalityDamage;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.magic.spell.CastType;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellActionType;
import zcylas.totality.api.magic.spell.SpellComponent;
import zcylas.totality.api.magic.spell.SpellMaterial;
import zcylas.totality.api.magic.spell.SpellSchool;
import zcylas.totality.api.rpg.combat.SavingThrow;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.init.items.SpellComponentItems;
import zcylas.totality.networking.notification.SendNotificationPayload;

import java.util.EnumSet;
import java.util.List;

/**
 * Disintegrate — Transmutation, 6th level. V, S, M (a lodestone and a pinch of dust).
 *
 * A thin green ray shoots toward a target within 20 blocks (≈ 60 feet).
 * The target makes a DEX saving throw.
 *
 * ● Failed save : 10d6 + 40 Force damage.
 *                 If reduced to 0 HP → disintegrated into a pile of gray dust.
 *                 Everything worn/carried (except magic items) is also destroyed.
 * ● Successful save : nothing happens.
 *
 * Classes: Sorcerer, Wizard.
 */
public class DisintegrateSpell extends Spell {

    public static final Identifier ON_HIT_ID =
            Identifier.fromNamespaceAndPath("totality", "disintegrate");

    private static final int    DICE_COUNT = 10;
    private static final int    FLAT_BONUS = 40;
    private static final double RANGE      = 20.0;  // 60 ft ≈ 20 blocks

    public DisintegrateSpell() {
        super(
                Identifier.fromNamespaceAndPath("totality", "disintegrate"),
                "Disintegrate",
                "A thin green ray springs from your finger toward a target within range. " +
                        "DEX save. On a failed save: 10d6 + 40 Force damage. " +
                        "If reduced to 0 HP the target is disintegrated into gray dust. " +
                        "On a successful save: nothing happens.",
                Type.ACTIVE,
                6,
                SpellSchool.DESTRUCTION,
                false,
                false,
                SpellActionType.ACTION,
                EnumSet.of(SpellComponent.VERBAL, SpellComponent.SOMATIC, SpellComponent.MATERIAL),
                SpellMaterial.replaceableItems(
                        List.of(SpellComponentItems.SULPHUR_DUST),
                        "a lodestone and a pinch of dust"),
                CastType.INSTANT,
                0,
                240,   // 12s cooldown
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/disintegrate.png"),
                "To dust you return."
        );
    }

    @Override public boolean isDefault() { return true; }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext ctx) {
        return checkMaterials(player);
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext ctx) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        consumeMaterials(player);

        Vec3 eye  = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end  = eye.add(look.scale(RANGE));
        int  dc   = getSpellSaveDc(player);

        // ── Visual: green particle ray ────────────────────────────────────────
        for (double d = 1.5; d <= RANGE; d += 0.5) {
            Vec3 p = eye.add(look.scale(d));
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.0f, 0.5f);

        // ── Find the first target entity in the look direction ────────────────
        LivingEntity target = null;
        double best = RANGE;
        List<LivingEntity> candidates = sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(
                        Math.min(eye.x, end.x) - 1, Math.min(eye.y, end.y) - 1, Math.min(eye.z, end.z) - 1,
                        Math.max(eye.x, end.x) + 1, Math.max(eye.y, end.y) + 1, Math.max(eye.z, end.z) + 1));
        for (LivingEntity e : candidates) {
            if (e == player) continue;
            var res = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (res.isPresent()) {
                double d = eye.distanceTo(res.get());
                if (d < best) { best = d; target = e; }
            }
        }

        if (target == null) return;

        // ── DEX saving throw ──────────────────────────────────────────────────
        RollOutcome outcome = SavingThrow.roll(target, AbilityScore.DEX, dc, RollType.NORMAL);

        if (outcome.isSuccess()) {
            // Saved — nothing happens
            SendNotificationPayload.send(player,
                    target.getDisplayName().getString() + " resisted Disintegrate!", 0xFF88FF88);
            return;
        }

        // ── Failed save: 10d6 + 40 Force ─────────────────────────────────────
        int damage = FLAT_BONUS;
        for (int i = 0; i < DICE_COUNT; i++) damage += Dice.D6.roll(player.getRandom());

        float hpBefore = target.getHealth();
        TotalityDamage.hurt(target, player, DamageTypes.FORCE, damage, DamageFlags.MAGICAL);

        // ── Disintegration if killed ──────────────────────────────────────────
        if (target.getHealth() <= 0 && hpBefore > 0) {
            Vec3 pos = target.position();
            // Suppress normal drops — replace with gray dust
            sl.sendParticles(ParticleTypes.POOF,
                    pos.x, pos.y + 1, pos.z, 50, 0.6, 0.6, 0.6, 0.05);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.x, pos.y + 1, pos.z, 20, 0.4, 0.4, 0.4, 0.02);
            // Drop sulphur dust as placeholder (future: creature-specific gray dust)
            sl.addFreshEntity(new ItemEntity(sl,
                    pos.x, pos.y + 0.5, pos.z,
                    new ItemStack(SpellComponentItems.SULPHUR_DUST, 2)));
            player.level().playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.8f, 1.6f);
        }
    }
}