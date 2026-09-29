package zcylas.totality.client.hologram;

/**
 * Colour theme of a System hologram. All hologram textures are authored white, so one asset set is
 * tinted per style: the accent drives the frame, glow, icon and buttons; the surface is the dark
 * translucent panel behind the text.
 */
public enum HologramStyle {
    /** The default System voice: deep navy surface, cyan light. */
    SYSTEM(0x3FC8FF, 0x040C1A, 0xEAF8FF, 0xB6D6EC, 0x6FE0FF, HologramIcon.SYSTEM, HologramSound.OPEN),
    /** Completion and readiness: the System blue, a touch brighter and cooler. */
    SUCCESS(0x52DAFF, 0x040D1A, 0xEFFBFF, 0xB8DAEE, 0x86EEFF, HologramIcon.CHECK, HologramSound.READY),
    /** Caution: amber light on a dark bronze surface. */
    WARNING(0xFFB347, 0x140C04, 0xFFF4E4, 0xE6CFB2, 0xFFD27A, HologramIcon.WARNING, HologramSound.WARNING),
    /** Failure or danger: red light on a dark crimson surface. */
    ERROR(0xFF4B5E, 0x150409, 0xFFECEE, 0xE8BCC2, 0xFF8A97, HologramIcon.ERROR, HologramSound.WARNING);

    /** RGB, no alpha. */
    public final int accent;
    public final int surface;
    public final int title;
    public final int body;
    public final int highlight;
    public final HologramIcon defaultIcon;
    public final HologramSound defaultSound;

    HologramStyle(int accent, int surface, int title, int body, int highlight, HologramIcon defaultIcon,
                  HologramSound defaultSound) {
        this.accent = accent;
        this.surface = surface;
        this.title = title;
        this.body = body;
        this.highlight = highlight;
        this.defaultIcon = defaultIcon;
        this.defaultSound = defaultSound;
    }
}
