package zcylas.totality.api.magic.spell.conjuration;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellSchool;
import zcylas.totality.api.rpg.combat.SavingThrow;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.util.List;

/**
 * Poison Spray — Conjuration cantrip. V, S.
 * Point blank range (5 blocks). CON save — on fail: 1d12 Poison damage only — does NOT apply
 * Minecraft's ongoing Poison status effect (see the canonical balance notes: "poison DAMAGE only,
 * does NOT apply Poisoned condition"). {@code onActivate} below already matches this; this
 * comment previously said "+ POISONED," which was stale/incorrect relative to both the actual
 * code and the canonical design — corrected here, no behavior changed.
 */
public class PoisonSpraySpell extends Spell {

    private static final double RANGE = 5.0;

    public PoisonSpraySpell() {
        super(
                Identifier.fromNamespaceAndPath("totality", "poison_spray"),
                "Poison Spray",
                "Project a puff of noxious gas at a creature within 5 blocks. " +
                        "Target makes a CON saving throw. On fail: 1d12 Poison damage.",
                Type.ACTIVE, 0, SpellSchool.CONJURATION, false, false,
                20,
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/poison_spray.png"),
                "A puff of death."
        );
    }

    @Override public boolean isDefault() { return true; }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext context) {
        return true;
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        if (!(player.level() instanceof ServerLevel sl)) return;

        Vec3 look = player.getLookAngle();
        Vec3 end  = player.getEyePosition().add(look.scale(RANGE));

        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                end.x, end.y, end.z, 30, 0.4, 0.4, 0.4, 0.0);

        int dc = getSpellSaveDc(player);
        List<LivingEntity> targets = sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(end.x-1.5, end.y-1.5, end.z-1.5, end.x+1.5, end.y+1.5, end.z+1.5));

        for (LivingEntity target : targets) {
            if (target == player) continue;
            RollOutcome outcome = SavingThrow.roll(target, AbilityScore.CON, dc, RollType.NORMAL);
            if (!outcome.isSuccess()) {
                int damage = Dice.D12.roll(player.getRandom());
                // NO_CONDITIONS: without it, TotalityDamage.hurt's shared
                // ConditionComponent.applyFromDamageType bridge auto-applies Conditions.POISONED
                // for 100 ticks on any POISON-type hit, which ConditionServerTick then ticks as
                // repeated 1-damage poison hits every 10 ticks — visually indistinguishable from
                // ongoing vanilla Poison, and exactly the reproduced bug. This is the same
                // documented opt-out spell bolts already use to manage their own conditions (see
                // DamageFlags.NO_CONDITIONS); Poison Spray simply hadn't been passing it. The
                // bridge itself is untouched — FIRE/FROST damage, and any other current or future
                // POISON damage source that wants the auto-applied condition, are unaffected.
                TotalityDamage.hurt(target, player, DamageTypes.POISON, damage,
                        DamageFlags.MAGICAL, DamageFlags.NO_CONDITIONS);
            }
        }
    }
}