package zcylas.totality.api.magic.spell.destruction;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.magic.spell.CastType;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellActionType;
import zcylas.totality.api.magic.spell.SpellComponent;
import zcylas.totality.api.magic.spell.SpellSchool;
import zcylas.totality.entity.magic.SpellBoltEntity;

import java.util.EnumSet;

/**
 * Scorching Ray — Destruction, 2nd level. V, S.
 *
 * You create three rays of fire and hurl them at targets within range.
 * You can hurl them at one target or several. Make a ranged spell attack
 * for each ray. On a hit, the target takes 2d6 Fire damage.
 *
 * All three rays fire as separate SpellBoltEntity projectiles with slight
 * vertical spread so they're visually distinct.
 */
public class ScorchingRaySpell extends Spell {

    private static final int  BOLT_COLOR = 0xFF2200;
    private static final int  RAY_COUNT  = 3;
    private static final int  DICE_COUNT = 2;
    private static final Dice DAMAGE_DIE = Dice.D6;

    public ScorchingRaySpell() {
        super(
                Identifier.fromNamespaceAndPath("totality", "scorching_ray"),
                "Scorching Ray",
                "You hurl three rays of fire. Make a ranged spell attack for each ray. " +
                        "On a hit, the target takes 2d6 Fire damage.",
                Type.ACTIVE,
                2,
                SpellSchool.DESTRUCTION,
                false,
                false,
                SpellActionType.ACTION,
                EnumSet.of(SpellComponent.VERBAL, SpellComponent.SOMATIC),
                null,
                CastType.INSTANT,
                0,
                80,    // 4s cooldown
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/scorching_ray.png"),
                "Three streaks of searing heat."
        );
    }

    @Override public boolean isDefault() { return true; }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext ctx) {
        return true;
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext ctx) {
        // Fire 3 separate bolts with slight vertical spread
        double[] spreads = { -0.04, 0.0, 0.04 };
        for (int i = 0; i < RAY_COUNT; i++) {
            SpellBoltEntity bolt = SpellBoltEntity.create(
                    player.level(), player,
                    "Scorching Ray",
                    DamageTypes.FIRE,
                    DICE_COUNT, DAMAGE_DIE,
                    getSpellcastingAbility(player),
                    BOLT_COLOR,
                    null
            ).withSounds(SoundEvents.BLAZE_SHOOT, SoundEvents.BLAZE_SHOOT);

            // Apply vertical spread so three bolts are visually distinct
            net.minecraft.world.phys.Vec3 vel = bolt.getDeltaMovement();
            bolt.setDeltaMovement(vel.x, vel.y + spreads[i], vel.z);
            player.level().addFreshEntity(bolt);
        }
    }
}