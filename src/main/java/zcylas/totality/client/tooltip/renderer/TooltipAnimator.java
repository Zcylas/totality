package zcylas.totality.client.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class TooltipAnimator {

    public static void drawFlickerText(GuiGraphicsExtractor graphics, Font font, String text,
                                       int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;
        double flicker = Math.sin(timeMs * 0.0030) * Math.cos(timeMs * 0.0020 + 1.2);
        float factor = (float)(flicker * 0.45 + 0.20);
        int baseA = (color >>> 24) & 0xFF;
        int r = Math.min(255, Math.max(0, ((color >>> 16) & 0xFF) + (int)((255 - ((color >>> 16) & 0xFF)) * factor)));
        int g = Math.min(255, Math.max(0, ((color >>> 8) & 0xFF) + (int)((255 - ((color >>> 8) & 0xFF)) * factor)));
        int b = Math.min(255, Math.max(0, (color & 0xFF) + (int)((255 - (color & 0xFF)) * factor)));
        int c = (baseA << 24) | (r << 16) | (g << 8) | b;
        graphics.text(font, text, x, y, c, true);
    }

    public static void drawSlowShineText(GuiGraphicsExtractor graphics, Font font, String text,
                                         int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        final int length = text.length();
        final long idleMs = 3000L;
        final long activeMs = 800L + (long)(length * 80L);
        final long cycleMs = activeMs + idleMs;
        final double spread = 2.0;

        long cyclePos = Math.floorMod(timeMs, cycleMs);
        boolean active = cyclePos < activeMs;
        double progress = active ? (double) cyclePos / activeMs : 2.0;
        double peakPos = -spread + (length - 1 + 2.0 * spread) * progress;

        // Shine color — a brighter, more saturated blue-white
        int shineR = 180, shineG = 220, shineB = 255;

        int baseA = (color >>> 24) & 0xFF;
        int baseR = (color >>> 16) & 0xFF;
        int baseG = (color >>> 8) & 0xFF;
        int baseB = color & 0xFF;

        int cursorX = x;
        for (int i = 0; i < length; i++) {
            String ch = String.valueOf(text.charAt(i));

            double dist = Math.abs(i - peakPos);
            double shine = dist < spread ? 0.5 * (1.0 + Math.cos(Math.PI * dist / spread)) : 0.0;
            float factor = (float)(shine * 0.75);

            int r = Math.min(255, baseR + (int)((shineR - baseR) * factor));
            int g = Math.min(255, baseG + (int)((shineG - baseG) * factor));
            int b = Math.min(255, baseB + (int)((shineB - baseB) * factor));
            int c = (baseA << 24) | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX, y, finalColor, true);
            cursorX += font.width(ch);
        }
    }

    public static void drawArtifactText(GuiGraphicsExtractor graphics, Font font, String text,
                                        int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        final int length = text.length();
        final long charDelay = 200L;
        final long holdMs = 3000L;
        final long cycleMs = length * charDelay + holdMs;

        long cyclePos = Math.floorMod(timeMs, cycleMs);
        int activated = (int) Math.min(length, cyclePos / charDelay);

        // Metallic color cycle — bronze → gold → cyan → back
        float metalPhase = (float)((timeMs % 5000L) / 5000.0f);
        int activeR, activeG, activeB;
        if (metalPhase < 0.33f) {
            // Bronze
            float t = metalPhase / 0.33f;
            activeR = (int)(205 + (212 - 205) * t);
            activeG = (int)(127 + (175 - 127) * t);
            activeB = (int)(50  + (55  - 50)  * t);
        } else if (metalPhase < 0.66f) {
            // Gold
            float t = (metalPhase - 0.33f) / 0.33f;
            activeR = (int)(212 + (100 - 212) * t);
            activeG = (int)(175 + (220 - 175) * t);
            activeB = (int)(55  + (220 - 55)  * t);
        } else {
            // Cyan
            float t = (metalPhase - 0.66f) / 0.34f;
            activeR = (int)(100 + (205 - 100) * t);
            activeG = (int)(220 + (127 - 220) * t);
            activeB = (int)(220 + (50  - 220) * t);
        }

        int cursorX = x;
        for (int i = 0; i < length; i++) {
            String ch = String.valueOf(text.charAt(i));

            int c;
            int yOffset = 0;

            if (i < activated) {
                // Fully activated — metallic color, sitting on baseline
                c = 0xFF000000 | (activeR << 16) | (activeG << 8) | activeB;
            } else if (i == activated) {
                // Currently activating — lift up with a flash
                long activateMs = cyclePos - activated * charDelay;
                float progress = Math.min(1.0f, activateMs / 150.0f);
                // Start high, settle to baseline
                yOffset = (int)(-(1.0f - progress) * 4);
                // Flash from white to metallic
                int flashR = (int)(255 + (activeR - 255) * progress);
                int flashG = (int)(255 + (activeG - 255) * progress);
                int flashB = (int)(255 + (activeB - 255) * progress);
                c = 0xFF000000 | (flashR << 16) | (flashG << 8) | flashB;
            } else {
                // Not yet activated — very dim bronze
                c = 0xFF3D2A0A;
            }

            final int finalColor = c;
            final int finalY = y + yOffset;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX, finalY, finalColor, true);
            cursorX += font.width(ch);
        }
    }
    public static void drawHeatDistortText(GuiGraphicsExtractor graphics, Font font, String text,
                                           int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        int cursorX = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));

            // Subtle per-character wobble — different phase per letter
            double t = timeMs * 0.0018 + i * 0.9;
            int yOffset = (int) Math.round(Math.sin(t) * 0.8 + Math.cos(t * 0.7 + 1.1) * 0.4);

            // Molten color shift — orange to gold per character
            double heat = 0.5 * (1.0 + Math.sin(timeMs * 0.0022 + i * 0.6));
            int r = Math.min(255, (int)(210 + heat * 45));
            int g = Math.min(255, (int)(100 + heat * 80));
            int b = (int)(0 + heat * 20);
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX, y + yOffset, finalColor, true);
            cursorX += font.width(ch);
        }
    }

    public static void drawEpicWaveText(GuiGraphicsExtractor graphics, Font font, String text,
                                        int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        int cursorX = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));

            // Smooth sine wave — each letter offset by phase
            double t = timeMs * 0.0020 + i * 0.6;
            int yOffset = (int) Math.round(Math.sin(t) * 1.5);

            // Color shifts from purple to lavender based on wave position
            double wave = 0.5 * (1.0 + Math.sin(t));
            int r = (int)(170 + wave * 50);  // 170 → 220
            int g = (int)(85  + wave * 85);  // 85  → 170
            int b = 255;
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX, y + yOffset, finalColor, true);
            cursorX += font.width(ch);
        }
    }
    public static void drawMythicalText(GuiGraphicsExtractor graphics, Font font, String text,
                                        int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        int cursorX = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));

            // Subtle multi-directional drift — each letter has unique phase
            double tx = timeMs * 0.0015 + i * 1.1;
            double ty = timeMs * 0.0018 + i * 0.8;
            int xOffset = (int) Math.round(Math.sin(tx) * 0.7 + Math.cos(tx * 0.6) * 0.3);
            int yOffset = (int) Math.round(Math.sin(ty) * 0.7 + Math.cos(ty * 0.7 + 1.0) * 0.3);

            // Prismatic — full hue cycle, each letter offset in hue
            float hue = ((i / (float) text.length()) + (float)(timeMs % 8000L) / 8000.0f) % 1.0f;
            if (hue < 0) hue += 1.0f;
            int c = rainbowColor(hue);

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX + xOffset, y + yOffset, finalColor, true);
            cursorX += font.width(ch);
        }
    }
    public static void drawCrudeText(GuiGraphicsExtractor graphics, Font font, String text,
                                     int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        int cursorX = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));

            // Occasional random shake — only triggers on irregular intervals
            double trigger = Math.sin(timeMs * 0.0041 + i * 2.3) * Math.cos(timeMs * 0.0073 + i * 1.1);
            int xOffset = trigger > 0.85 ? (int) Math.round(trigger * 1.5) : 0;
            int yOffset = trigger > 0.85 ? (int) Math.round(Math.cos(timeMs * 0.003 + i) * 1.0) : 0;

            // Rusty flicker — color shifts between dirty brown and rusted bronze
            double flicker = 0.5 * (1.0 + Math.sin(timeMs * 0.0035 + i * 0.9));
            int r = (int)(0x6B + flicker * (0xA0 - 0x6B));
            int g = (int)(0x5A + flicker * (0x79 - 0x5A));
            int b = (int)(0x45 + flicker * (0x4A - 0x45));
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX + xOffset, y + yOffset, finalColor, true);
            cursorX += font.width(ch);
        }
    }

    public static void drawCalibratedText(GuiGraphicsExtractor graphics, Font font, String text,
                                          int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        final int length = text.length();
        final long idleMs = 2000L;
        final long activeMs = 500L + (long)(length * 65L);
        final long cycleMs = activeMs + idleMs;
        final double spread = 2.0;

        long cyclePos = Math.floorMod(timeMs, cycleMs);
        boolean active = cyclePos < activeMs;
        double progress = active ? (double) cyclePos / activeMs : 2.0;
        double peakPos = -spread + (length - 1 + 2.0 * spread) * progress;

        int baseR = 0x6A, baseG = 0x8F, baseB = 0x9C;
        int shineR = 0xB7, shineG = 0xFF, shineB = 0xF4;

        int cursorX = x;
        for (int i = 0; i < length; i++) {
            String ch = String.valueOf(text.charAt(i));

            double dist = Math.abs(i - peakPos);
            double shine = dist < spread ? 0.5 * (1.0 + Math.cos(Math.PI * dist / spread)) : 0.0;
            float factor = (float)(shine * 0.85);

            int r = (int)(baseR + (shineR - baseR) * factor);
            int g = (int)(baseG + (shineG - baseG) * factor);
            int b = (int)(baseB + (shineB - baseB) * factor);
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX, y, finalColor, true);
            cursorX += font.width(ch);
        }
    }

    public static void drawReinforcedText(GuiGraphicsExtractor graphics, Font font, String text,
                                          int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        // Heavy metal pulse — all letters move together, slowly
        double pulse = 0.5 * (1.0 + Math.sin(timeMs * 0.0014));
        int yOffset = (int) Math.round(pulse * 1.5); // 0 to 1.5 px, very slow

        // Color pulses between dark steel and iron with copper accent flash
        int r, g, b;
        if (pulse > 0.85) {
            // Copper accent flash at peak
            r = 0xD8; g = 0x91; b = 0x4A;
        } else {
            r = (int)(0x7A + pulse * (0xB0 - 0x7A));
            g = (int)(0x7A + pulse * (0xB0 - 0x7A));
            b = (int)(0x7A + pulse * (0xB0 - 0x7A));
        }
        int c = 0xFF000000 | (r << 16) | (g << 8) | b;

        graphics.text(font, text, x, y + yOffset, c, true);
    }

    public static void drawPrototypeGlitchText(GuiGraphicsExtractor graphics, Font font, String text,
                                               int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        int cursorX = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));

            // Occasional 1px left/right jump glitch
            double glitchTrigger = Math.sin(timeMs * 0.0091 + i * 3.1) * Math.cos(timeMs * 0.0067 + i * 1.7);
            int xOffset = 0;
            if (glitchTrigger > 0.88) xOffset = 1;
            else if (glitchTrigger < -0.88) xOffset = -1;

            // Color cycles between cyan, acid green, white flash
            double phase = (timeMs * 0.0008 + i * 0.3) % 1.0;
            int r, g, b;
            if (glitchTrigger > 0.88 || glitchTrigger < -0.88) {
                // White flash during glitch
                r = 0xFF; g = 0xFF; b = 0xFF;
            } else if (phase < 0.5) {
                float t = (float)(phase / 0.5);
                r = (int)(0x42 + (0x8F - 0x42) * t);
                g = (int)(0xF5 + (0xFF - 0xF5) * t);
                b = (int)(0xD7 + (0x7A - 0xD7) * t);
            } else {
                float t = (float)((phase - 0.5) / 0.5);
                r = (int)(0x8F + (0x42 - 0x8F) * t);
                g = (int)(0xFF + (0xF5 - 0xFF) * t);
                b = (int)(0x7A + (0xD7 - 0x7A) * t);
            }
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX + xOffset, y, finalColor, true);
            cursorX += font.width(ch);
        }
    }

    public static void drawOverchargedText(GuiGraphicsExtractor graphics, Font font, String text,
                                           int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        // Energy pulse cycle — calm then surge
        double pulse = Math.sin(timeMs * 0.0030);
        boolean surging = pulse > 0.75;

        int cursorX = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));

            int xOffset = 0, yOffset = 0;
            int r, g, b;

            if (surging) {
                // Electric surge — shake and flash white/cyan
                double surge = Math.sin(timeMs * 0.05 + i * 2.1);
                xOffset = (int) Math.round(surge * 1.2);
                yOffset = (int) Math.round(Math.cos(timeMs * 0.04 + i * 1.5) * 0.8);
                double flash = 0.5 * (1.0 + Math.sin(timeMs * 0.08 + i));
                r = (int)(0x1E + flash * (0xFF - 0x1E));
                g = (int)(0x6C + flash * (0xFF - 0x6C));
                b = 0xFF;
            } else {
                // Calm — deep blue breathing gently
                double breathe = 0.5 * (1.0 + Math.sin(timeMs * 0.0020 + i * 0.4));
                r = (int)(0x1E + breathe * (0x42 - 0x1E));
                g = (int)(0x6C + breathe * (0xF5 - 0x6C));
                b = 0xFF;
            }
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX + xOffset, y + yOffset, finalColor, true);
            cursorX += font.width(ch);
        }
    }

    public static void drawMasterworkShineText(GuiGraphicsExtractor graphics, Font font, String text,
                                               int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        final int length = text.length();
        final long idleMs = 2800L;
        final long activeMs = 700L + (long)(length * 55L);
        final long cycleMs = activeMs + idleMs;
        final double spread = 1.8;

        long cyclePos = Math.floorMod(timeMs, cycleMs);
        boolean active = cyclePos < activeMs;
        double progress = active ? (double) cyclePos / activeMs : 2.0;
        double peakPos = -spread + (length - 1 + 2.0 * spread) * progress;

        int baseR = 0xD8, baseG = 0x91, baseB = 0x4A; // copper-gold base
        int shineR = 0xFF, shineG = 0xD8, shineB = 0x9A; // bright polished copper shine

        int cursorX = x;
        for (int i = 0; i < length; i++) {
            String ch = String.valueOf(text.charAt(i));

            double dist = Math.abs(i - peakPos);
            double shine = dist < spread ? 0.5 * (1.0 + Math.cos(Math.PI * dist / spread)) : 0.0;
            float factor = (float)(shine * 0.85);

            int r = Math.min(255, baseR + (int)((shineR - baseR) * factor));
            int g = Math.min(255, baseG + (int)((shineG - baseG) * factor));
            int b = Math.min(255, baseB + (int)((shineB - baseB) * factor));
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX, y, finalColor, true);
            cursorX += font.width(ch);
        }
    }
    public static void drawUncommonText(GuiGraphicsExtractor graphics, Font font, String text,
                                        int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        double pulse = 0.5 * (1.0 + Math.sin(timeMs * 0.0018));
        float factor = (float)(pulse * 0.12); // very subtle

        int baseA = (color >>> 24) & 0xFF;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + (int)((255 - ((color >>> 16) & 0xFF)) * factor));
        int g = Math.min(255, ((color >>> 8)  & 0xFF) + (int)((255 - ((color >>> 8)  & 0xFF)) * factor));
        int b = Math.min(255, (color          & 0xFF)  + (int)((255 - (color          & 0xFF)) * factor));
        int c = (baseA << 24) | (r << 16) | (g << 8) | b;

        graphics.text(font, text, x, y, c, true);
    }
    public static void drawCursedText(GuiGraphicsExtractor graphics, Font font, String text,
                                      int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        int cursorX = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));

            // Occasional 1px shake — irregular per letter
            double trigger = Math.sin(timeMs * 0.0091 + i * 2.7) * Math.cos(timeMs * 0.0061 + i * 1.4);
            int xOffset = 0;
            if (trigger > 0.88) xOffset = 1;
            else if (trigger < -0.88) xOffset = -1;

            // Color flickers between dark blood red and purple accent
            double flicker = Math.sin(timeMs * 0.0045 + i * 1.1) * Math.cos(timeMs * 0.0033 + i * 0.7);
            int r, g, b;
            if (trigger > 0.88 || trigger < -0.88) {
                // Corruption flash during shake
                r = 0xB0; g = 0x20; b = 0x45;
            } else if (flicker < -0.5) {
                // Dark purple accent
                r = 0x4B; g = 0x00; b = 0x82;
            } else {
                // Blood red base
                double t = 0.5 * (1.0 + flicker);
                r = (int)(0x5A + t * (0x8B - 0x5A));
                g = (int)(0x10 + t * (0x1A - 0x10));
                b = (int)(0x20 + t * (0x35 - 0x20));
            }
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX + xOffset, y, finalColor, true);
            cursorX += font.width(ch);
        }
    }

    public static void drawQuestText(GuiGraphicsExtractor graphics, Font font, String text,
                                     int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        // Whole word rises 1px very slowly then returns
        double rise = 0.5 * (1.0 + Math.sin(timeMs * 0.0014));
        int yOffset = rise > 0.85 ? -1 : 0;

        // Color pulses between muted gold and warm yellow, rare white flash at peak
        double pulse = 0.5 * (1.0 + Math.sin(timeMs * 0.0020));
        int r, g, b;
        if (pulse > 0.92) {
            // Rare bright flash
            r = 0xFF; g = 0xFF; b = 0xFF;
        } else {
            r = (int)(0xB8 + pulse * (0xFF - 0xB8));
            g = (int)(0x8A + pulse * (0xF2 - 0x8A));
            b = (int)(0x2A + pulse * (0xA6 - 0x2A));
        }
        int c = 0xFF000000 | (r << 16) | (g << 8) | b;

        graphics.text(font, text, x, y + yOffset, c, true);
    }

    public static void drawBlessedText(GuiGraphicsExtractor graphics, Font font, String text,
                                       int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        int cursorX = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));

            // Soft holy wave — very slow, fractional pixel float
            double t = timeMs * 0.0010 + i * 0.5;
            double wave = 0.5 * (1.0 + Math.sin(t));
            int yOffset = wave > 0.85 ? -1 : 0; // only lifts at peak

            // Color breathes from soft old gold to pale warm light
            float factor = (float)(wave * 0.55);
            int r = Math.min(255, 0xD9 + (int)((0xFF - 0xD9) * factor));
            int g = Math.min(255, 0xB8 + (int)((0xF8 - 0xB8) * factor));
            int b = Math.min(255, 0x6C + (int)((0xD6 - 0x6C) * factor));
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX, y + yOffset, finalColor, true);
            cursorX += font.width(ch);
        }
    }

    public static void drawRadianceText(GuiGraphicsExtractor graphics, Font font, String text,
                                        int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        final int length = text.length();

        // Sunburst wave — burstCenter travels slowly across
        double burstProgress = (timeMs % 4000L) / 4000.0;
        double burstCenter = -1.0 + (length + 2.0) * burstProgress;
        final double spread = 2.5;

        int cursorX = x;
        for (int i = 0; i < length; i++) {
            String ch = String.valueOf(text.charAt(i));

            double dist = Math.abs(i - burstCenter);
            double influence = dist < spread ? 0.5 * (1.0 + Math.cos(Math.PI * dist / spread)) : 0.0;

            // Lift 1px at peak
            int yOffset = influence > 0.6 ? -1 : 0;

            // Color: edge=light blue, middle=parchment, center=white
            int r, g, b;
            if (influence > 0.7) {
                // Center — white
                float t = (float)((influence - 0.7) / 0.3);
                r = (int)(0xFF + (0xFF - 0xFF) * t);
                g = (int)(0xF2 + (0xFF - 0xF2) * t);
                b = (int)(0xA6 + (0xFF - 0xA6) * t);
            } else if (influence > 0.2) {
                // Middle — parchment
                float t = (float)((influence - 0.2) / 0.5);
                r = (int)(0x66 + (0xFF - 0x66) * t);
                g = (int)(0xCC + (0xF2 - 0xCC) * t);
                b = (int)(0xFF + (0xA6 - 0xFF) * t);
            } else {
                // Edge — light blue base
                r = 0x66; g = 0xCC; b = 0xFF;
            }
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX, y + yOffset, finalColor, true);
            cursorX += font.width(ch);
        }
    }
    public static void drawSacredText(GuiGraphicsExtractor graphics, Font font, String text,
                                      int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        final int length = text.length();
        final long idleMs = 2500L;
        final long activeMs = 600L + (long)(length * 70L);
        final long cycleMs = activeMs + idleMs;
        final double spread = 1.8;

        long cyclePos = Math.floorMod(timeMs, cycleMs);
        boolean active = cyclePos < activeMs;
        double progress = active ? (double) cyclePos / activeMs : 2.0;
        double peakPos = -spread + (length - 1 + 2.0 * spread) * progress;

        int cursorX = x;
        for (int i = 0; i < length; i++) {
            String ch = String.valueOf(text.charAt(i));

            double dist = Math.abs(i - peakPos);
            double shine = dist < spread ? 0.5 * (1.0 + Math.cos(Math.PI * dist / spread)) : 0.0;
            float factor = (float)(shine * 0.80);

            // Lift the peak letter by 1px
            int yOffset = shine > 0.7 ? -1 : 0;

            // Color from darker ceremonial gold to warm holy shine
            int r = Math.min(255, 0xA8 + (int)((0xFF - 0xA8) * factor));
            int g = Math.min(255, 0x79 + (int)((0xF4 - 0x79) * factor));
            int b = Math.min(255, 0x1F + (int)((0xCC - 0x1F) * factor));
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX, y + yOffset, finalColor, true);
            cursorX += font.width(ch);
        }
    }

    public static void drawCelestialText(GuiGraphicsExtractor graphics, Font font, String text,
                                         int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        final int length = text.length();
        final long idleMs = 1800L;
        final long activeMs = 700L + (long)(length * 75L);
        final long cycleMs = activeMs + idleMs;
        final double spread = 2.5;

        long cyclePos = Math.floorMod(timeMs, cycleMs);
        boolean active = cyclePos < activeMs;
        double progress = active ? (double) cyclePos / activeMs : 2.0;
        double peakPos = -spread + (length - 1 + 2.0 * spread) * progress;

        int cursorX = x;
        for (int i = 0; i < length; i++) {
            String ch = String.valueOf(text.charAt(i));

            double dist = Math.abs(i - peakPos);
            double shine = dist < spread ? 0.5 * (1.0 + Math.cos(Math.PI * dist / spread)) : 0.0;
            float factor = (float)(shine * 0.85);

            // Lift slightly as wave passes
            int yOffset = shine > 0.5 ? -1 : 0;

            // Blend from soft heavenly blue toward pure white shine
            int r = Math.min(255, 0xA8 + (int)((0xFF - 0xA8) * factor));
            int g = Math.min(255, 0xDF + (int)((0xFF - 0xDF) * factor));
            int b = Math.min(255, 0xFF);
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX, y + yOffset, finalColor, true);
            cursorX += font.width(ch);
        }
    }

    public static void drawGodforgedText(GuiGraphicsExtractor graphics, Font font, String text,
                                         int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        final int length = text.length();
        double center = (length - 1) / 2.0;

        final long expandMs  = 600L + (long)(length * 50L);
        final long holdMs    = 800L;
        final long collapseMs = 600L + (long)(length * 50L);
        final long idleMs    = 2000L;
        final long cycleMs   = expandMs + holdMs + collapseMs + idleMs;
        final double maxSpread = length / 2.0 + 1.0;

        long cyclePos = Math.floorMod(timeMs, cycleMs);

        boolean expanding  = cyclePos < expandMs;
        boolean holding    = cyclePos >= expandMs && cyclePos < expandMs + holdMs;
        boolean collapsing = cyclePos >= expandMs + holdMs && cyclePos < expandMs + holdMs + collapseMs;
        boolean idle       = cyclePos >= expandMs + holdMs + collapseMs;

        int cursorX = x;
        for (int i = 0; i < length; i++) {
            String ch = String.valueOf(text.charAt(i));
            double distFromCenter = Math.abs(i - center);

            double influence;
            int yOffset = 0;

            if (idle) {
                influence = 0.0;
            } else if (holding) {
                influence = 1.0;
                yOffset = -1;
            } else if (expanding) {
                double burstRadius = (cyclePos / (double) expandMs) * maxSpread;
                if (distFromCenter <= burstRadius) {
                    double edgeDist = burstRadius - distFromCenter;
                    influence = Math.min(1.0, edgeDist / 1.2);
                    if (influence > 0.5) yOffset = -1;
                } else {
                    influence = 0.0;
                }
            } else {
                // Collapsing — reverse wave from outside inward
                long collapsePos = cyclePos - expandMs - holdMs;
                double collapseRadius = maxSpread - (collapsePos / (double) collapseMs) * maxSpread;
                if (distFromCenter <= collapseRadius) {
                    double edgeDist = collapseRadius - distFromCenter;
                    influence = Math.min(1.0, edgeDist / 1.2);
                    if (influence > 0.5) yOffset = -1;
                } else {
                    influence = 0.0;
                }
            }

            float factor = (float) influence;
            int r, g, b;
            if (factor > 0.90) {
                r = 0xFF; g = 0xFF; b = 0xFF;
            } else if (factor > 0.70) {
                float t = (factor - 0.70f) / 0.30f;
                r = 0xFF;
                g = (int)(0xE0 + (0xF7 - 0xE0) * t);
                b = (int)(0x8A + (0xCC - 0x8A) * t);
            } else if (factor > 0.35) {
                float t = (factor - 0.35f) / 0.35f;
                r = (int)(0xE0 + (0xFF - 0xE0) * t);
                g = (int)(0xB9 + (0xE0 - 0xB9) * t);
                b = (int)(0x4A + (0x8A - 0x4A) * t);
            } else if (factor > 0.0) {
                float t = factor / 0.35f;
                r = (int)(0xB8 + (0xE0 - 0xB8) * t);
                g = (int)(0x86 + (0xB9 - 0x86) * t);
                b = (int)(0x0B + (0x4A - 0x0B) * t);
            } else {
                r = 0xB8; g = 0x86; b = 0x0B;
            }
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor)),
                    cursorX, y + yOffset, finalColor, true);
            cursorX += font.width(ch);
        }
    }
    public static void drawForbiddenText(GuiGraphicsExtractor graphics, Font font, String text,
                                         int x, int y, int color, long timeMs) {
        if (text == null || text.isEmpty()) return;

        final int length = text.length();
        char[] display = text.toCharArray();

        // Occasionally swap adjacent letters
        for (int i = 0; i < length - 1; i++) {
            double swapTrigger = Math.sin(timeMs * 0.0031 + i * 2.3)
                    * Math.cos(timeMs * 0.0041 + i * 1.9);
            if (swapTrigger > 0.92 && !Character.isWhitespace(display[i])
                    && !Character.isWhitespace(display[i + 1])) {
                char temp = display[i];
                display[i] = display[i + 1];
                display[i + 1] = temp;
            }
        }

        int cursorX = x;
        for (int i = 0; i < length; i++) {
            String ch = String.valueOf(display[i]);

            // Subtle obfuscation
            double obfuscateTrigger = Math.sin(timeMs * 0.0044 + i * 1.7)
                    * Math.cos(timeMs * 0.0028 + i * 2.5);
            boolean obfuscate = !Character.isWhitespace(display[i]) && obfuscateTrigger > 0.91;

            // Bright enough to read against dark background
            double pulse = 0.5 * (1.0 + Math.sin(timeMs * 0.0022 + i * 0.6));
            int r = (int)(0x5A + pulse * (0xB0 - 0x5A));
            int g = (int)(0x17 + pulse * (0x20 - 0x17));
            int b = (int)(0x4F + pulse * (0x8A - 0x4F));
            int c = 0xFF000000 | (r << 16) | (g << 8) | b;

            final boolean finalObfuscate = obfuscate;
            final int finalColor = c;
            graphics.text(font,
                    net.minecraft.network.chat.Component.literal(ch)
                            .withStyle(s -> s.withColor(finalColor).withObfuscated(finalObfuscate)),
                    cursorX, y, finalColor, true);
            cursorX += font.width(ch);
        }
    }

    private static int rainbowColor(float hue) {
        hue = hue % 1.0f;
        if (hue < 0) hue += 1.0f;
        float h = hue * 6.0f;
        float x = 1.0f - Math.abs(h % 2.0f - 1.0f);
        float r, g, b;
        if (h < 1)      { r = 1; g = x; b = 0; }
        else if (h < 2) { r = x; g = 1; b = 0; }
        else if (h < 3) { r = 0; g = 1; b = x; }
        else if (h < 4) { r = 0; g = x; b = 1; }
        else if (h < 5) { r = x; g = 0; b = 1; }
        else            { r = 1; g = 0; b = x; }
        return 0xFF000000 | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    private TooltipAnimator() {}
}