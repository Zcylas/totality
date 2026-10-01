package zcylas.totality.screen.phone;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * The phone's operating-system look: wallpaper, text, accents, icon tiles, badges and notification severities.
 * It is independent of the physical device ({@link PhoneDeviceStyle}): a copper casing never recolours the OS,
 * and a future OS theme never changes the casing.
 *
 * <p>Only {@link #DEFAULT} exists: a dark charcoal/navy interface with cyan-blue accents.
 */
public record PhoneTheme(
        int wallpaperTop, int wallpaperBottom, int wallpaperGlow,
        int background, int backgroundLow,
        int text, int textDim, int textFaint,
        int accent, int accentBright, int onAccent,
        int tile, int tileLight, int tileDark, int tileActive, int tileLocked,
        int line, int headerBackground,
        int iconBackground, int iconLight, int iconDark, int iconLocked,
        int dock, int dockLine,
        int badge, int badgeText,
        int shade, int card, int cardLine,
        int normal, int important, int critical,
        Identifier lockSprite) {

    public static final PhoneTheme DEFAULT = new PhoneTheme(
            0xFF0B1119, 0xFF0F2233, 0x2A2FB4E6,
            0xFF0E1620, 0xFF0B121A,
            0xFFE4EDF5, 0xFF8FA1B3, 0xFF4F6072,
            0xFF3CC4EE, 0xFF8BE2FF, 0xFF06131D,
            0xFF162333, 0xFF213448, 0xFF0A1118, 0xFF173A52, 0xFF111A24,
            0xFF1E2E40, 0xFF0C141D,
            0xFF172A3D, 0xFF24405A, 0xFF0D1824, 0xFF111C28,
            0xC80B1520, 0xFF1E3245,
            0xFFC9D3DC, 0xFF0B141D,
            0xFF0A1118, 0xFF122030, 0xFF1F3245,
            0xFF4FD67A, 0xFFF0A030, 0xFFFF4D4D,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "phone/os/lock"));

    /** A severity's colour: green (normal), orange (important) or red (critical). */
    public int severity(PhoneNotificationShade.Severity s) {
        return switch (s) {
            case NORMAL -> normal;
            case IMPORTANT -> important;
            case CRITICAL -> critical;
        };
    }
}
