package zcylas.totality.api.operator;

/** The server's answer to one Operator Mode request. */
public enum OperatorResult {
    /** Authorized and performed. */
    EXECUTED,
    /** Authorized; the world/player was already in that state (nothing changed). */
    UNCHANGED,
    /** The player is not an operator (checked on the server, for this request). */
    DENIED,
    /** Malformed, repeated, stale or too frequent: refused without looking further. */
    REJECTED;

    private static final OperatorResult[] VALUES = values();

    public static OperatorResult byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : REJECTED;
    }
}
