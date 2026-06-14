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
 * Fire Bolt — Wizard/Sorcerer cantrip, Destruction school.
 *
 * A mote of fire hurls toward a target. Ranged spell attack using the caster's
 * spellcasting ability (INT for Wizard, CHA for Sorcerer).
 * On hit: 1d10 Fire damage.
 *
 * TODO: Sets flammable objects on fire on hit.
 *
 * Scales with character level (future):
 *   Level  5 → 2d10
 *   Level 11 → 3d10
 *   Level 17 → 4d10
 */
public class FireboltSpell extends Spell {

    /** Orange-red to match the icon. */
    private static final int BOLT_COLOR = 0xFF5500;

    private static final int  DICE_COUNT = 1;
    private static final Dice DAMAGE_DIE = Dice.D10;

    public FireboltSpell() {
        super(
                Identifier.fromNamespaceAndPath("totality", "fire_bolt"),
                "Fire Bolt",
                "Hurl a mote of fire at a target within range. " +
                        "Roll d20 + your spellcasting modifier to hit. " +
                        "On a hit, deal 1d10 Fire damage. " +
                        "Flammable objects hit by this spell ignite.",
                Type.ACTIVE,
                0,                       // cantrip
                SpellSchool.DESTRUCTION,
                false,                   // no concentration
                false,                   // not a ritual
                20,                      // 1 second cooldown
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/firebolt.png"),
                "Fire given purpose."
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
                "Fire Bolt",
                DamageTypes.FIRE,
                DICE_COUNT,
                DAMAGE_DIE,
                getSpellcastingAbility(player),
                BOLT_COLOR,
                null
        ).withSounds(SoundEvents.FIRECHARGE_USE, SoundEvents.BLAZE_SHOOT);
        player.level().addFreshEntity(bolt);
    }
}