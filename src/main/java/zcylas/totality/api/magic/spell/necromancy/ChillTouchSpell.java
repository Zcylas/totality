package zcylas.totality.api.magic.spell.necromancy;

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
 * Chill Touch — Necromancy cantrip. V, S.
 *
 * You create a ghostly skeletal hand that clutches at a creature within range.
 * Ranged spell attack using the caster's spellcasting modifier.
 * On hit: 1d8 Necrotic damage. The target cannot regain hit points until the
 * start of your next turn.
 *
 * Scales:
 *   Level  5 → 2d8
 *   Level 11 → 3d8
 *   Level 17 → 4d8
 */
public class ChillTouchSpell extends Spell {

    private static final int  BOLT_COLOR  = 0x44FFAA;  // pale ghost green
    private static final int  DICE_COUNT  = 1;
    private static final Dice DAMAGE_DIE  = Dice.D8;

    public ChillTouchSpell() {
        super(
                Identifier.fromNamespaceAndPath("totality", "chill_touch"),
                "Chill Touch",
                "A ghostly skeletal hand clutches at a creature. Roll d20 + your spellcasting " +
                        "modifier to hit. On hit: 1d8 Necrotic damage. Target cannot heal " +
                        "until the start of your next turn.",
                Type.ACTIVE,
                0,                           // cantrip
                SpellSchool.NECROMANCY,
                false,
                false,
                24,                          // 1.2s cooldown
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/chill_touch.png"),
                "A chill of death in your grasp."
        );
    }

    @Override public boolean isDefault() { return true; }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext context) {
        return true;
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        SpellBoltEntity bolt = SpellBoltEntity.create(
                player.level(),
                player,
                "Chill Touch",
                DamageTypes.NECROTIC,
                DICE_COUNT,
                DAMAGE_DIE,
                getSpellcastingAbility(player),
                BOLT_COLOR,
                Identifier.fromNamespaceAndPath("totality", "chill_touch")
        ).withSounds(SoundEvents.ELDER_GUARDIAN_CURSE, SoundEvents.ELDER_GUARDIAN_CURSE);
        player.level().addFreshEntity(bolt);
    }
}