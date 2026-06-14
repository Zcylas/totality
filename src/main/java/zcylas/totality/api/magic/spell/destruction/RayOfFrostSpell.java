package zcylas.totality.api.magic.spell.destruction;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellSchool;
import zcylas.totality.entity.magic.SpellBoltEntity;

/**
 * Ray of Frost — Wizard/Sorcerer cantrip, Destruction school.
 *
 * A frigid beam of blue-white light streaks toward a target.
 * Ranged spell attack using the caster's spellcasting ability.
 * On hit: 1d8 Frost damage + target is Slowed for 2 seconds
 * (speed reduced until the start of your next turn).
 *
 * Scales with character level (future):
 *   Level  5 → 2d8
 *   Level 11 → 3d8
 *   Level 17 → 4d8
 */
public class RayOfFrostSpell extends Spell {

    /** Icy cyan — matches the icon. */
    private static final int BOLT_COLOR = 0x44CCFF;

    /** On-hit effect ID — registered in SpellRegistry alongside this spell. */
    public static final Identifier ON_HIT_ID =
            Identifier.fromNamespaceAndPath("totality", "ray_of_frost");

    private static final int  DICE_COUNT = 1;
    private static final Dice DAMAGE_DIE = Dice.D8;

    public RayOfFrostSpell() {
        super(
                Identifier.fromNamespaceAndPath("totality", "ray_of_frost"),
                "Ray of Frost",
                "A frigid beam streaks toward a target. " +
                        "Roll d20 + your spellcasting modifier to hit. " +
                        "On a hit, deal 1d8 Frost damage and reduce the target's speed " +
                        "until the start of your next turn.",
                Type.ACTIVE,
                0,                       // cantrip
                SpellSchool.DESTRUCTION,
                false,
                false,
                20,                      // 1 second cooldown
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/ray_of_frost.png"),
                "Cold is just the absence of warmth. Let me help with that."
        );
    }

    @Override
    public boolean isDefault() { return true; }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext context) {
        return true;
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        SpellBoltEntity bolt = SpellBoltEntity.create(
                player.level(),
                player,
                "Ray of Frost",
                DamageTypes.FROST,
                DICE_COUNT,
                DAMAGE_DIE,
                getSpellcastingAbility(player),
                BOLT_COLOR,
                ON_HIT_ID // → SpellBoltOnHitRegistry → applies CHILLED on hit
        ).withSounds(SoundEvents.BOTTLE_FILL, SoundEvents.GLASS_BREAK);
        player.level().addFreshEntity(bolt);
    }
}