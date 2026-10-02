package zcylas.totality.client.vfx.screen;

/** Who perceives a request. */
public enum ScreenFxAudience {
    /** Every player whose camera is within the request's falloff range. */
    EVERYONE_IN_RANGE,
    /** Only the player named as the request's subject (e.g. the caster or the target of an impact frame). */
    SUBJECT_ONLY
}
