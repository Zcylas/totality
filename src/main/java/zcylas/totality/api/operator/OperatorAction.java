package zcylas.totality.api.operator;

import org.jetbrains.annotations.Nullable;

/**
 * The complete Operator Mode whitelist (voice API demonstration). A request names one of these by
 * ordinal only — never text — and the SERVER decides the target (always the requesting player or its
 * world), the permission and the result. Nothing else can be requested.
 */
public enum OperatorAction {
    /** Clear the weather, exactly as {@code /weather clear} with its default duration. */
    CLEAR_RAIN("clear rain"),
    /** The speaking player's own game mode to Survival. */
    SURVIVAL("survival"),
    /** The speaking player's own game mode to Creative. */
    CREATIVE("creative");

    /** The spoken command after the activation phrase (normalized: lower case, single spaces). */
    public final String spoken;

    OperatorAction(String spoken) {
        this.spoken = spoken;
    }

    private static final OperatorAction[] VALUES = values();

    /** Wire decoding: an unknown ordinal is null (the server refuses it), never an exception. */
    public static @Nullable OperatorAction byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }

    public static @Nullable OperatorAction bySpoken(String spoken) {
        for (OperatorAction a : VALUES) if (a.spoken.equals(spoken)) return a;
        return null;
    }
}
