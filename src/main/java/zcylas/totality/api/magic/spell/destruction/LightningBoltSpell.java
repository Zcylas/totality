package zcylas.totality.api.magic.spell.destruction;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
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

import java.util.EnumSet;
import java.util.List;

/**
 * Lightning Bolt — Destruction, 3rd level. V, S, M (a bit of fur and a glass rod).
 *
 * A stroke of lightning forming a line 1 block wide and 30 blocks long blasts out
 * in the direction you point. Each creature in the line makes a DEX saving throw.
 * Fail: 8d6 Lightning. Save: half.
 *
 * Material is replaceable — satisfied by Component Pouch or Arcane Focus.
 */
public class LightningBoltSpell extends Spell {

    private static final int    DICE_COUNT = 8;
    private static final double LENGTH     = 30.0;
    private static final double WIDTH      = 1.5; // half-width of bolt hitbox

    public LightningBoltSpell() {
        super(
                Identifier.fromNamespaceAndPath("totality", "lightning_bolt"),
                "Lightning Bolt",
                "A stroke of lightning blasts 30 blocks in your direction. " +
                        "DEX save vs your spell save DC. Fail: 8d6 Lightning. Save: half.",
                Type.ACTIVE,
                3,
                SpellSchool.DESTRUCTION,
                false,
                false,
                SpellActionType.ACTION,
                EnumSet.of(SpellComponent.VERBAL, SpellComponent.SOMATIC, SpellComponent.MATERIAL),
                SpellMaterial.replaceableItems(
                        List.of(SpellComponentItems.FUR, SpellComponentItems.GLASS_ROD),
                        "a bit of fur and a glass rod"),
                CastType.INSTANT,
                0,
                100,  // 5s cooldown
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/lightning_bolt.png"),
                "The sky's fury, in your hands."
        );
    }

    @Override public boolean isDefault() { return true; }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext context) {
        return checkMaterials(player);
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        consumeMaterials(player);

        Vec3 origin  = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle().normalize();
        Vec3 end     = origin.add(lookDir.scale(LENGTH));
        int  dc      = getSpellSaveDc(player);

        // Visual: dense electric spark trail along the bolt
        for (double d = 0; d < LENGTH; d += 0.4) {
            Vec3 p = origin.add(lookDir.scale(d));
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    p.x, p.y, p.z, 3, 0.15, 0.15, 0.15, 0.02);
        }
        // Impact flash at end of bolt
        sl.sendParticles(ParticleTypes.SONIC_BOOM, end.x, end.y, end.z, 1, 0, 0, 0, 0);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2f, 1.0f);

        // Build bounding box encompassing the entire bolt line
        double minX = Math.min(origin.x, end.x) - WIDTH;
        double minY = Math.min(origin.y, end.y) - WIDTH;
        double minZ = Math.min(origin.z, end.z) - WIDTH;
        double maxX = Math.max(origin.x, end.x) + WIDTH;
        double maxY = Math.max(origin.y, end.y) + WIDTH;
        double maxZ = Math.max(origin.z, end.z) + WIDTH;

        List<LivingEntity> candidates = sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(minX, minY, minZ, maxX, maxY, maxZ));

        for (LivingEntity target : candidates) {
            if (target == player) continue;
            // Check if entity center is close to the bolt line
            Vec3 toEnt = target.getBoundingBox().getCenter().subtract(origin);
            double along = toEnt.dot(lookDir);
            if (along < 0 || along > LENGTH) continue;
            Vec3 closest = origin.add(lookDir.scale(along));
            if (target.getBoundingBox().getCenter().distanceTo(closest) > WIDTH + 1.0) continue;

            // Roll saving throw
            int raw = 0;
            for (int i = 0; i < DICE_COUNT; i++) raw += Dice.D6.roll(player.getRandom());

            RollOutcome outcome = SavingThrow.roll(target, AbilityScore.DEX, dc, RollType.NORMAL);
            float damage = outcome.isSuccess() ? raw / 2f : raw;
            TotalityDamage.hurt(target, player, DamageTypes.LIGHTNING, damage, DamageFlags.MAGICAL);
        }
    }
}