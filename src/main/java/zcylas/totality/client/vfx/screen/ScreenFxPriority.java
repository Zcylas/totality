package zcylas.totality.client.vfx.screen;

/**
 * While a request of a higher priority is contributing on a channel, lower-priority requests on that channel are
 * ignored (a cinematic shake replaces gameplay shake instead of adding to it).
 */
public enum ScreenFxPriority {
    AMBIENT, NORMAL, MAJOR, CINEMATIC
}
