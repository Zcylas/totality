package zcylas.totality.api.magic.spell;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.combat.condition.ConditionComponent;
import zcylas.totality.api.combat.condition.Conditions;
import zcylas.totality.api.magic.spell.destruction.ConeOfColdSpell;
import zcylas.totality.api.magic.spell.destruction.CrownOfStarsSpell;
import zcylas.totality.api.magic.spell.conjuration.MistyStepSpell;
import zcylas.totality.api.magic.spell.conjuration.PoisonSpraySpell;
import zcylas.totality.api.magic.spell.destruction.*;
import zcylas.totality.api.magic.spell.necromancy.ChillTouchSpell;
import zcylas.totality.api.magic.spell.restoration.BlessSpell;
import zcylas.totality.api.magic.spell.transmutation.DisintegrateSpell;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SpellRegistry {

    private static final Map<Identifier, Spell> SPELLS = new LinkedHashMap<>();

    // ── On-hit effects ────────────────────────────────────────────────────────
    static {
        SpellBoltOnHitRegistry.register(
                RayOfFrostSpell.ON_HIT_ID,
                target -> ConditionComponent.apply(target, Conditions.CHILLED, 40, null));

        SpellBoltOnHitRegistry.register(
                Identifier.fromNamespaceAndPath("totality", "chill_touch"),
                target -> ConditionComponent.apply(target, Conditions.NO_HEALING, 40, null));

        // Disintegrate handles everything in onActivate (saving throw + damage + dust).
        // No bolt entity — no on-hit registration needed.
    }

    // ── Cantrips — Destruction ────────────────────────────────────────────────
    public static final EldritchBlast    ELDRITCH_BLAST = register(new EldritchBlast());
    public static final FireboltSpell    FIRE_BOLT      = register(new FireboltSpell());
    public static final RayOfFrostSpell  RAY_OF_FROST   = register(new RayOfFrostSpell());

    // ── Cantrips — Necromancy ────────────────────────────────────────────────
    public static final ChillTouchSpell  CHILL_TOUCH    = register(new ChillTouchSpell());

    // ── Cantrips — Conjuration ────────────────────────────────────────────────
    public static final PoisonSpraySpell POISON_SPRAY   = register(new PoisonSpraySpell());

    // ── 1st level ─────────────────────────────────────────────────────────────
    public static final BlessSpell        BLESS         = register(new BlessSpell());
    public static final MagicMissileSpell MAGIC_MISSILE = register(new MagicMissileSpell());

    // ── 2nd level ─────────────────────────────────────────────────────────────
    public static final MistyStepSpell   MISTY_STEP     = register(new MistyStepSpell());
    public static final ScorchingRaySpell SCORCHING_RAY = register(new ScorchingRaySpell());

    // ── 3rd level ─────────────────────────────────────────────────────────────
    public static final FireballSpell    FIREBALL       = register(new FireballSpell());
    public static final LightningBoltSpell LIGHTNING_BOLT = register(new LightningBoltSpell());

    // ── 5th level ─────────────────────────────────────────────────────────────
    public static final ConeOfColdSpell  CONE_OF_COLD   = register(new ConeOfColdSpell());

    // ── 6th level ─────────────────────────────────────────────────────────────
    public static final DisintegrateSpell DISINTEGRATE   = register(new DisintegrateSpell());

    // ── 7th level ─────────────────────────────────────────────────────────────
    public static final CrownOfStarsSpell CROWN_OF_STARS = register(new CrownOfStarsSpell());

    // ── Registry helpers ──────────────────────────────────────────────────────

    private static <T extends Spell> T register(T spell) {
        SPELLS.put(spell.getId(), spell);
        AbilityRegistry.add(spell);
        return spell;
    }

    @Nullable
    public static Spell get(Identifier id) { return SPELLS.get(id); }

    public static Collection<Spell> all() {
        return Collections.unmodifiableCollection(SPELLS.values());
    }

    public static void init() {}

    private SpellRegistry() {}
}