package zcylas.totality.client.vfx.screen;

/**
 * The resolved Shared Screen FX output of one frame, after merging, hard caps, the flash budget and accessibility
 * scaling. {@code shake} and {@code flash} are 0..1 fractions of their channels' maximum output.
 *
 * @param shake            camera shake strength
 * @param flash            flash strength actually shown
 * @param flashDemand      flash strength requested by the merged contributions before the anti-strobe limits
 * @param flashColour      0xRRGGBB tint of the flash
 * @param strongestShake   the strongest single shake contribution (for verifying the merge bound)
 * @param strongestFlash   the strongest single flash contribution
 * @param activeShake      live shake requests
 * @param activeFlash      live flash requests
 */
public record ScreenFxFrame(double shake, double flash, double flashDemand, int flashColour, double strongestShake,
                            double strongestFlash, int activeShake, int activeFlash) {

    public static final ScreenFxFrame NONE = new ScreenFxFrame(0, 0, 0, 0xFFFFFF, 0, 0, 0, 0);
}
