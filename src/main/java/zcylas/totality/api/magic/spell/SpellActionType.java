package zcylas.totality.api.magic.spell;

/**
 * The action economy cost of casting a spell.
 *
 * ACTION      — standard cast, uses your action for the turn (most spells)
 * BONUS_ACTION — quick cast, uses bonus action (e.g. Healing Word, Misty Step)
 * REACTION    — cast in response to a trigger outside your turn (e.g. Shield,
 *               Counterspell). These need special trigger hooks in the damage
 *               pipeline — not yet implemented.
 */
public enum SpellActionType {
    ACTION,
    BONUS_ACTION,
    REACTION;

    public String displayName() {
        return switch (this) {
            case ACTION       -> "Action";
            case BONUS_ACTION -> "Bonus Action";
            case REACTION     -> "Reaction";
        };
    }
}