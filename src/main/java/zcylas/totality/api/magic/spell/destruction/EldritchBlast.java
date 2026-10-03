package zcylas.totality.api.magic.spell.destruction;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
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
 * Each beam is a separate {@link SpellBoltEntity} fired in the look direction. A multi-beam cast fires its beams one
 * after another, {@link #BEAM_INTERVAL_TICKS} apart, each aimed where the caster looks at that moment (Eldritch Blast V2;
 * only the development override uses it until the scaling is wired).
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
        fireBeam(player);
        // Further beams follow one by one, each aimed where the caster is looking when it fires.
        int beams = beamCount();
        for (int i = 1; i < beams; i++) {
            ServerScheduler.getInstance().queue(server -> {
                if (player.isAlive() && !player.isRemoved()) fireBeam(player);
            }, i * BEAM_INTERVAL_TICKS);
        }
    }

    // ── Beams (Eldritch Blast V2) ─────────────────────────────────────────────

    /** Ticks between the beams of one multi-beam cast (0.2 s: separate, readable pulses). */
    static final int BEAM_INTERVAL_TICKS = 4;

    /** Development only: beams per cast to preview the future Warlock-level scaling (0 = the normal count). */
    private static volatile int devBeamOverride;

    /** Beams per cast: 1 until Warlock-level scaling is wired (see the class comment), or the development override. */
    public static int beamCount() {
        return devBeamOverride > 0 ? devBeamOverride : 1;
    }

    /** Development environment only (ignored elsewhere): beams per cast, 1..4, or 0 for the normal count. */
    public static void setDevBeamOverride(int beams) {
        if (!VerificationReporter.isDevEnvironment()) return;
        devBeamOverride = Math.clamp(beams, 0, 4);
    }

    /**
     * One beam: the unchanged bolt (speed, lifetime, collision, attack roll, 1d10 Force), drawn by the V2 client
     * presentation, which also plays the cast and impact sounds (so they stay in step with what is drawn).
     */
    private void fireBeam(ServerPlayer player) {
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
        ).withVisualStyle(SpellBoltEntity.VisualStyle.ELDRITCH);
        player.level().addFreshEntity(bolt);
    }
}