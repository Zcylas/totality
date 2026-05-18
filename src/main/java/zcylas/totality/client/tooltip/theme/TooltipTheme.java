package zcylas.totality.client.tooltip.theme;

public record TooltipTheme(
        int border,
        int borderInner,
        int bgTop,
        int bgBottom,
        int name,
        int badgeBg,
        int badgeCutout,
        int sectionHeader,
        int body,
        int separator,
        int diamondFrame,
        int diamondFrameInner,
        int footerDot,
        int borderStyle,
        int typeStyle
) {
    public static TooltipTheme common(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFF9D9D9D, 0xFF6B6B6B, 0xFF2A2A2A, 0xFF1A1A1A,
                0xFFFFFFFF, 0xFF9D9D9D, 0xFF141414, 0xFFD0D0D0,
                0xFFBBBBBB, 0xFF6B6B6B, 0xFF9D9D9D, 0xFF2A2A2A,
                0xFF6B6B6B, borderStyle, typeStyle
        );
    }

    public static TooltipTheme uncommon(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFF55AA55, 0xFF2A6B2A, 0xFF112411, 0xFF0A180A,
                0xFF55AA55, 0xFF2A6B2A, 0xFF0A180A, 0xFF55AA55,
                0xFFBBBBBB, 0xFF2A6B2A, 0xFF55AA55, 0xFF112411,
                0xFF2A6B2A, borderStyle, typeStyle
        );
    }

    public static TooltipTheme rare(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFF5588FF, 0xFF2A44AA, 0xFF0F1F4A, 0xFF0A1530,
                0xFF5588FF, 0xFF2A44AA, 0xFF0A1530, 0xFF5588FF,
                0xFFBBBBBB, 0xFF2A44AA, 0xFF5588FF, 0xFF0F1F4A,
                0xFF2A44AA, borderStyle, typeStyle
        );
    }

    public static TooltipTheme epic(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFFAA55FF, 0xFF6A2AAA, 0xFF28103D, 0xFF1A0A28,
                0xFFAA55FF, 0xFF6A2AAA, 0xFF1A0A28, 0xFFAA55FF,
                0xFFBBBBBB, 0xFF6A2AAA, 0xFFAA55FF, 0xFF28103D,
                0xFF6A2AAA, borderStyle, typeStyle
        );
    }

    public static TooltipTheme legendary(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFFEF9F27, 0xFF9C6410, 0xFF3D1E00, 0xFF1F0F00,
                0xFFFFD700, 0xFF9C6410, 0xFF100800, 0xFFFFAA00,
                0xFFE6C87A, 0xFF9C6410, 0xFFEF9F27, 0xFF2A1A00,
                0xFF9C6410, borderStyle, typeStyle
        );
    }

    public static TooltipTheme mythical(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFFFF5555, 0xFFAA2222, 0xFF3D0A0A, 0xFF280606,
                0xFFFF5555, 0xFFAA2222, 0xFF280606, 0xFFFF5555,
                0xFFBBBBBB, 0xFFAA2222, 0xFFFF5555, 0xFF3D0A0A,
                0xFFAA2222, borderStyle, typeStyle
        );
    }

    public static TooltipTheme artifact(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFFD4A017, 0xFF8A6A00, 0xFF3D2A00, 0xFF281C00,
                0xFFD4A017, 0xFF8A6A00, 0xFF281C00, 0xFFD4A017,
                0xFFE6C87A, 0xFF8A6A00, 0xFFD4A017, 0xFF3D2A00,
                0xFF8A6A00, borderStyle, typeStyle
        );
    }

    public static TooltipTheme cursed(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFF8B1A35, 0xFF5A1020, 0xFF3D0610, 0xFF28040A,
                0xFF8B1A35, 0xFF5A1020, 0xFF28040A, 0xFF8B1A35,
                0xFFBBBBBB, 0xFF5A1020, 0xFF8B1A35, 0xFF3D0610,
                0xFF5A1020, borderStyle, typeStyle
        );
    }

    public static TooltipTheme quest(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFFFFD85A, 0xFFB88A2A, 0xFF3D3000, 0xFF282000,
                0xFFFFD85A, 0xFFB88A2A, 0xFF282000, 0xFFFFD85A,
                0xFFBBBBBB, 0xFFB88A2A, 0xFFFFD85A, 0xFF3D3000,
                0xFFB88A2A, borderStyle, typeStyle
        );
    }

    public static TooltipTheme blessed(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFFFFE6A3, 0xFFD9B86C, 0xFF3D2E10, 0xFF281E0A,
                0xFFFFE6A3, 0xFFD9B86C, 0xFF281E0A, 0xFFFFE6A3,
                0xFFBBBBBB, 0xFFD9B86C, 0xFFFFE6A3, 0xFF3D2E10,
                0xFFD9B86C, borderStyle, typeStyle
        );
    }

    public static TooltipTheme sacred(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFFD6A84F, 0xFFA8791F, 0xFF3D2A00, 0xFF281C00,
                0xFFD6A84F, 0xFFA8791F, 0xFF281C00, 0xFFD6A84F,
                0xFFBBBBBB, 0xFFA8791F, 0xFFD6A84F, 0xFF3D2A00,
                0xFFA8791F, borderStyle, typeStyle
        );
    }

    public static TooltipTheme celestial(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFFA8DFFF, 0xFF6A9FCC, 0xFF102038, 0xFF0A1528,
                0xFFA8DFFF, 0xFF6A9FCC, 0xFF0A1528, 0xFFA8DFFF,
                0xFFE6FAFF, 0xFF6A9FCC, 0xFFA8DFFF, 0xFF102038,
                0xFF6A9FCC, borderStyle, typeStyle
        );
    }

    public static TooltipTheme divine(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFFFFFFFF, 0xFFAABBCC, 0xFF102038, 0xFF0A1528,
                0xFFFFFFFF, 0xFFAABBCC, 0xFF0A1528, 0xFFFFFFFF,
                0xFFE6FAFF, 0xFFAABBCC, 0xFFFFFFFF, 0xFF102038,
                0xFFAABBCC, borderStyle, typeStyle
        );
    }

    public static TooltipTheme godforged(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFFE0B94A, 0xFFB8860B, 0xFF3D3000, 0xFF282000,
                0xFFE0B94A, 0xFFB8860B, 0xFF282000, 0xFFFFE08A,
                0xFFFFE08A, 0xFFB8860B, 0xFFE0B94A, 0xFF3D3000,
                0xFFB8860B, borderStyle, typeStyle
        );
    }

    public static TooltipTheme forbidden(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFF5A174F, 0xFF2A0E35, 0xFF2A0A38, 0xFF1C0628,
                0xFF5A174F, 0xFF2A0E35, 0xFF1C0628, 0xFF5A174F,
                0xFFBBBBBB, 0xFF2A0E35, 0xFF5A174F, 0xFF2A0A38,
                0xFF2A0E35, borderStyle, typeStyle
        );
    }

    public static TooltipTheme crude(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFF8A6F4D, 0xFF6B5A45, 0xFF2A1E10, 0xFF1C140A,
                0xFF8A6F4D, 0xFF6B5A45, 0xFF1C140A, 0xFFA0794A,
                0xFFBBBBBB, 0xFF6B5A45, 0xFF8A6F4D, 0xFF2A1E10,
                0xFF6B5A45, borderStyle, typeStyle
        );
    }

    public static TooltipTheme calibrated(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFF42F5D7, 0xFF6A8F9C, 0xFF0A2828, 0xFF061A1A,
                0xFF42F5D7, 0xFF6A8F9C, 0xFF061A1A, 0xFFB7FFF4,
                0xFFBBBBBB, 0xFF6A8F9C, 0xFF42F5D7, 0xFF0A2828,
                0xFF6A8F9C, borderStyle, typeStyle
        );
    }

    public static TooltipTheme reinforced(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFFB0B0B0, 0xFF7A7A7A, 0xFF282828, 0xFF1A1A1A,
                0xFFB0B0B0, 0xFF7A7A7A, 0xFF1A1A1A, 0xFFD8914A,
                0xFFBBBBBB, 0xFF7A7A7A, 0xFFB0B0B0, 0xFF282828,
                0xFF7A7A7A, borderStyle, typeStyle
        );
    }

    public static TooltipTheme prototype(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFF42F5D7, 0xFF2A3A3A, 0xFF0A2828, 0xFF061A1A,
                0xFF42F5D7, 0xFF2A3A3A, 0xFF061A1A, 0xFF8FFF7A,
                0xFFBBBBBB, 0xFF2A3A3A, 0xFF42F5D7, 0xFF0A2828,
                0xFF2A3A3A, borderStyle, typeStyle
        );
    }

    public static TooltipTheme overcharged(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFF42F5FF, 0xFF1E6CFF, 0xFF0A1A3D, 0xFF061028,
                0xFF42F5FF, 0xFF1E6CFF, 0xFF061028, 0xFFFFFFFF,
                0xFFBBBBBB, 0xFF1E6CFF, 0xFF42F5FF, 0xFF0A1A3D,
                0xFF1E6CFF, borderStyle, typeStyle
        );
    }

    public static TooltipTheme masterwork(int borderStyle, int typeStyle) {
        return new TooltipTheme(
                0xFFD8914A, 0xFF5A5A5A, 0xFF2A1E10, 0xFF1C140A,
                0xFFD8914A, 0xFF5A5A5A, 0xFF1C140A, 0xFFFFD89A,
                0xFFE6E6E6, 0xFF5A5A5A, 0xFFD8914A, 0xFF2A1E10,
                0xFF5A5A5A, borderStyle, typeStyle
        );
    }
}