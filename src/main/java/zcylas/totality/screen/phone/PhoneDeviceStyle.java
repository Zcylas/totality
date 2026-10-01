package zcylas.totality.screen.phone;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * How one phone DEVICE looks when opened: its casing (a nine-slice frame sprite and its insets), physical
 * proportions and fixed hardware details. It says nothing about what the phone does or how its operating system
 * looks — the screens ({@link PhoneAppGridScreen}, {@link PhoneSetupScreen}, {@link BankScreen}) draw through
 * {@link PhoneFrameRenderer} and {@link PhoneUi}, and the display's colours come from {@link PhoneTheme}, so a
 * future tier or skin is a new constant here plus its sprites, not new screen code.
 *
 * <p>Only {@link #CRUDE} exists: the Basic Copper Phone, derived from its item texture
 * ({@code textures/item/basic_copper_phone.png}). Its sprites are generated from that texture by
 * {@code Context/Tools/basic-copper-phone-phase1/generate_basic_copper_phone_gui.py}.
 */
public record PhoneDeviceStyle(
        Identifier frameSprite, int insetLeft, int insetTop, int insetRight, int insetBottom,
        /* body width / height */ float aspect,
        Identifier speakerSprite, int speakerWidth, int speakerHeight, int speakerY,
        Identifier cameraSprite, int cameraSize, /* gap between the camera and the speaker */ int cameraGap,
        /* chin detail, centred; {@code grilleY} counts up from the body's bottom edge */
        Identifier grilleSprite, int grilleWidth, int grilleHeight, int grilleY,
        /* right-edge buttons: {top, bottom} as fractions of the body height */ float[][] sideButtons,
        int outline, int buttonFace, int bezel) {

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, "phone/" + path);
    }

    /**
     * The Basic Copper Phone ("Crude" tier), Phase 1 redesign: a 1:2 smartphone body (the item is 36:52), thick
     * basic-tier bezels, the item's copper with a lit top-left rim, an engraved face-plate groove, corner screws,
     * the item's speaker slot (plus a front camera), a recessed microphone grille on the chin, and the item's two
     * right-edge buttons at the same relative heights (texel rows 15-20 and 23-26 of its 52-texel body).
     */
    public static final PhoneDeviceStyle CRUDE = new PhoneDeviceStyle(
            sprite("crude/frame"), 8, 18, 8, 18,
            1f / 2f,
            sprite("crude/speaker"), 18, 4, 7,
            sprite("crude/camera"), 4, 4,
            sprite("crude/grille"), 12, 4, 10,
            new float[][] {{(15 - 6) / 52f, (21 - 6) / 52f}, {(23 - 6) / 52f, (27 - 6) / 52f}},
            0xFF35190F, 0xFF88412C, 0xFF12100E);

    /**
     * The style for a phone item's frame. Only the Basic Copper Phone ({@link PhoneFrame#COPPER}) exists as
     * an item; the other {@link PhoneFrame} constants have no device art yet and use {@link #CRUDE}.
     */
    public static PhoneDeviceStyle of(PhoneFrame frame) {
        return CRUDE;
    }
}
