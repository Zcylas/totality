package zcylas.totality.client.vfx.screen;

/**
 * A Shared Screen FX channel. Requests on the same channel are merged (never summed without bound); each channel has
 * its own hard output limit, applied after merging.
 */
public enum ScreenFxChannel {
    /** Camera shake: a small rotation of the rendered view (never the player's aim). */
    SHAKE,
    /** Screen flash: a brief full-screen tint drawn under the HUD. */
    FLASH,
    /** Reserved for future impact frames: accepted only when enabled in the settings; no renderer exists yet. */
    IMPACT_FRAME
}
