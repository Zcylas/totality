package zcylas.totality.api.magic.spell;

/**
 * The physical/vocal requirements for casting a spell.
 *
 * VERBAL   — requires speaking an incantation. Blocked by the Silence spell.
 * SOMATIC  — requires precise hand gestures. Blocked if hands are restrained,
 *            bound, or (future) injured.
 * MATERIAL — requires a physical component. Satisfied by:
 *             1. Arcane Focus in equipment slot (replaces non-costly components)
 *             2. Component Pouch in equipment slot (stores components, checked first)
 *             3. The specific item in inventory
 *             Non-replaceable components (see {@link SpellMaterial}) always need
 *             the actual item regardless of focus or pouch.
 *
 * Each spell declares which it needs:
 * {@code components = EnumSet.of(SpellComponent.VERBAL, SpellComponent.SOMATIC)}
 */
public enum SpellComponent {
    VERBAL,
    SOMATIC,
    MATERIAL;

    public String displayName() {
        return switch (this) {
            case VERBAL   -> "V";
            case SOMATIC  -> "S";
            case MATERIAL -> "M";
        };
    }
}