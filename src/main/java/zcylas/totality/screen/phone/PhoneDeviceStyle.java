package zcylas.totality.screen.phone;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * How one phone DEVICE looks when opened: its casing (a nine-slice frame sprite and its insets), physical
 * proportions, fixed hardware details, and the palette its display uses. It says nothing about what the
 * phone does — the screens ({@link PhoneAppGridScreen}, {@link PhoneSetupScreen}, {@link BankScreen}) draw
 * through {@link PhoneFrameRenderer} and {@link PhoneUi} with whatever style the held item resolves to, so a
 * future tier or skin is a new constant here plus its sprites, not new screen code.
 *
 * <p>Only {@link #CRUDE} exists: it is derived from the Basic Copper Phone's item texture
 * ({@code textures/item/basic_copper_phone.png}). Its sprites are generated from that texture by
 * {@code Totality-Research/phone-screen-v2/tools/generate_crude_phone_gui.py}.
 */
public record PhoneDeviceStyle(
        Identifier frameSprite, int insetLeft, int insetTop, int insetRight, int insetBottom,
        /* body width / height */ float aspect,
        Identifier speakerSprite, int speakerWidth, int speakerHeight, int speakerY,
        /* right-edge buttons: {top, bottom} as fractions of the body height */ float[][] sideButtons,
        int outline, int buttonFace,
        Identifier glassSprite, Identifier lockSprite,
        Display display) {

    /**
     * The display's colours. {@code accent} marks selection and titles; everything else is neutral, so
     * the device's own material stays the only strong colour on screen.
     */
    public record Display(int background, int backgroundLow, int bezel, int text, int textDim, int textFaint,
                          int accent, int accentBright, int tile, int tileLight, int tileDark, int tileActive,
                          int tileLocked, int line, int headerBackground) {}

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, "phone/" + path);
    }

    /**
     * The Basic Copper Phone ("Crude" tier). From the item texture (36x52 body in a 64x64 texture): copper
     * casing with a dark chamfered outline, a lit top-left edge, a shaded right edge and heavier chin, a
     * 6-texel speaker slot, two buttons on the right edge (texel rows 15-20 and 23-26 of the body), a 1-texel
     * near-black bezel (#12100e) and a dark neutral glass (#1f201f). Accents are the casing's own copper
     * (#d3714f face, #f69771 lit edge).
     */
    public static final PhoneDeviceStyle CRUDE = new PhoneDeviceStyle(
            sprite("crude/frame"), 10, 18, 10, 20,
            36f / 52f,
            sprite("crude/speaker"), 24, 5, 6,
            new float[][] {{(15 - 6) / 52f, (21 - 6) / 52f}, {(23 - 6) / 52f, (27 - 6) / 52f}},
            0xFF35190F, 0xFF88412C,
            sprite("crude/glass"), sprite("crude/lock"),
            new Display(0xFF1F201F, 0xFF1A1B1A, 0xFF12100E, 0xFFE6DFD2, 0xFF9D968B, 0xFF5F5B55,
                    0xFFD3714F, 0xFFF69771, 0xFF2A2B28, 0xFF363733, 0xFF151614, 0xFF3A2B24,
                    0xFF1D1E1C, 0xFF2F302D, 0xFF181917));

    /**
     * The style for a phone item's frame. Only the Basic Copper Phone ({@link PhoneFrame#COPPER}) exists as
     * an item; the other {@link PhoneFrame} constants have no device art yet and use {@link #CRUDE}.
     */
    public static PhoneDeviceStyle of(PhoneFrame frame) {
        return CRUDE;
    }
}
