package zcylas.totality.api.magic.spell.conjuration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.magic.spell.CastType;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellActionType;
import zcylas.totality.api.magic.spell.SpellComponent;
import zcylas.totality.api.magic.spell.SpellMaterial;
import zcylas.totality.api.magic.spell.SpellSchool;

import java.util.EnumSet;
import java.util.Set;

/**
 * Misty Step — Conjuration, 2nd level. V only.
 * BONUS_ACTION. Teleport up to 30 blocks in the look direction.
 */
public class MistyStepSpell extends Spell {

    private static final double MAX_RANGE = 30.0;

    public MistyStepSpell() {
        super(
                Identifier.fromNamespaceAndPath("totality", "misty_step"),
                "Misty Step",
                "Briefly surrounded by silvery mist, you teleport up to 30 blocks to " +
                        "an unoccupied space you can see. Costs a Bonus Action.",
                Type.ACTIVE,
                2,
                SpellSchool.CONJURATION,
                false,
                false,
                SpellActionType.BONUS_ACTION,
                EnumSet.of(SpellComponent.VERBAL),
                null,
                CastType.INSTANT,
                0,
                120,   // 6s cooldown
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/misty_step.png"),
                "A whispered word, a flash of silver, and you are elsewhere."
        );
    }

    @Override public boolean isDefault() { return true; }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext ctx) {
        return true;
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext ctx) {
        if (!(player.level() instanceof ServerLevel sl)) return;

        Vec3 origin  = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle().normalize();

        // Walk along look direction, find last safe standing position
        Vec3 dest = origin;
        for (double d = 1.0; d <= MAX_RANGE; d += 0.5) {
            Vec3 candidate = origin.add(lookDir.scale(d));
            BlockPos feet  = BlockPos.containing(candidate.x, candidate.y - 1, candidate.z);
            BlockPos body  = BlockPos.containing(candidate);
            BlockPos head  = BlockPos.containing(candidate.x, candidate.y + 1, candidate.z);
            if (!sl.getBlockState(feet).isAir()
                    && sl.getBlockState(body).isAir()
                    && sl.getBlockState(head).isAir()) {
                dest = new Vec3(candidate.x, feet.getY() + 1, candidate.z);
                break;
            }
        }

        // Depart particles
        sl.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1, player.getZ(), 10, 0.3, 0.5, 0.3, 0.05);

        final Vec3 finalDest = dest;
        player.teleport(new TeleportTransition(
                sl,
                finalDest,
                Vec3.ZERO,
                player.getYRot(), player.getXRot(),
                Set.of(),
                TeleportTransition.DO_NOTHING));

        // Arrive particles + sound
        sl.sendParticles(ParticleTypes.END_ROD,
                finalDest.x, finalDest.y, finalDest.z, 10, 0.3, 0.5, 0.3, 0.05);
        player.level().playSound(null, finalDest.x, finalDest.y, finalDest.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.2f);
    }
}