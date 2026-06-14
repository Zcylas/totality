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
 * Eldritch Blast — Warlock cantrip, Destruction school.
 *
 * Fires a crackling beam of dark Force energy. Each hit resolves a d20 spell attack roll
 * using the caster's Charisma modifier. On hit: 1d10 Force damage.
 *
 * Scales with Warlock level (future — not yet wired):
 *   Level  5 → 2 beams
 *   Level 11 → 3 beams
 *   Level 17 → 4 beams
 *
 * Each beam is a separate {@link SpellBoltEntity} fired in the look direction.
 * Multi-beam versions will spread slightly (future).
 */
public class EldritchBlast extends Spell {

    /** Eldritch purple — matches the Gemini-generated icon. */
    private static final int BOLT_COLOR = 0xAA00FF;

    private static final int    DICE_COUNT = 1;
    private static final Dice   DAMAGE_DIE = Dice.D10;

    public EldritchBlast() {
        super(
                Identifier.fromNamespaceAndPath("totality", "eldritch_blast"),
                "Eldritch Blast",
                "A crackling beam of dark energy streaks toward your target. " +
                        "Roll d20 + Charisma to hit. On a hit, deal 1d10 Force damage. " +
                        "Gains additional beams at Warlock levels 5, 11, and 17.",
                Type.ACTIVE,
                0,                       // cantrip — no spell slot consumed
                SpellSchool.DESTRUCTION,
                false,                   // no concentration
                false,                   // not a ritual
                20,                      // 1 second cooldown (1 action per turn equivalent)
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/eldritch_blast.png"),
                "The whispers of your patron take form."
        );
    }

    // ── Testing ───────────────────────────────────────────────────────────────

    /** true during development so it shows up without requiring the Warlock class. */
    @Override
    public boolean isDefault() { return true; }

    // ── Activation ────────────────────────────────────────────────────────────

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext context) {
        return true; // future: require Warlock class
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        SpellBoltEntity bolt = SpellBoltEntity.create(
                player.level(),
                player,
                "Eldritch Blast",
                DamageTypes.FORCE,
                DICE_COUNT,
                DAMAGE_DIE,
                getSpellcastingAbility(player),
                BOLT_COLOR,
                null
        ).withSounds(SoundEvents.EVOKER_CAST_SPELL, SoundEvents.EVOKER_FANGS_ATTACK);
        player.level().addFreshEntity(bolt);
    }
}