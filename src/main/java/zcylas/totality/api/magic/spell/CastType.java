package zcylas.totality.api.magic.spell;

/**
 * Defines whether a spell has a cast bar and whether the player can
 * move while casting.
 *
 * INSTANT     — fires immediately with no cast bar. Most 1-action D&D spells
 *               and fast cantrips. The brief visual (particles, voice line)
 *               plays but doesn't delay the spell.
 *
 * MOBILE      — has a cast bar, player can walk freely while casting.
 *               Good for buff spells, quick enchantments, movement abilities.
 *               Movement doesn't interrupt the cast.
 *
 * STATIONARY  — has a cast bar, player MUST stand still. Moving interrupts
 *               the cast and wastes the spell slot (if leveled).
 *               Used for Teleport, long conjurations, powerful ritual-style
 *               casts, and any spell requiring intense concentration.
 *
 * The duration of the cast bar is set by {@link Spell#getCastTimeTicks()}.
 * CastType only controls movement restriction — it has no effect when
 * castTimeTicks is 0 (instant fires regardless).
 */
public enum CastType {
    INSTANT,
    MOBILE,
    STATIONARY;

    public boolean hasCastBar()         { return this != INSTANT; }
    public boolean requiresStationary() { return this == STATIONARY; }
}