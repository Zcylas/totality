package zcylas.totality.client.mining;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import zcylas.totality.Totality;
import zcylas.totality.api.mining.MiningTuning;
import zcylas.totality.client.hud.HudSprites;
import zcylas.totality.client.hud.HudSprites.Sprite;
import zcylas.totality.networking.mining.PowerStrikeResultPayload;

/**
 * Power Mining HUD V2: a reticle-adjacent force indicator in two parts that read the SAME charge — the
 * meter value {@link MiningTuning#meterValue} gives for the current hold ({@link ClientMiningController}),
 * the function the server evaluates on its own clock:
 * <ul>
 *   <li><b>Angular side brackets</b> — the physical buildup and the stage: they start close to the
 *       crosshair and move OUTWARD as the charge rises (and back in as the meter sweeps down), and change
 *       weight, glow and detail at each real threshold (White → Green → Orange → Red, the bands of
 *       {@link MiningTuning#presentationBand}); a short pop marks a threshold crossing, Red adds restrained
 *       stress lines.</li>
 *   <li><b>Segmented force rail</b> under the crosshair — the exact amount: segments fill continuously,
 *       markers sit at the real thresholds ({@link MiningTuning#WHITE_ZONE_MAX},
 *       {@link MiningTuning#GREEN_ZONE_MAX}, {@link MiningTuning#RED_ZONE}), unreached zones stay faintly
 *       tinted so the next threshold is always visible.</li>
 * </ul>
 * Alt alone shows the compact idle reticle (empty rail, close brackets). A release shows a short
 * discharge; impact/shatter feedback only when the server reports the strike really landed
 * ({@link PowerStrikeResultPayload}). A cancel collapses the brackets. No gameplay rule lives here.
 *
 * <p>Client limitation: a tool OVERLOAD (force load above the tool's tolerance) also resolves to Red on the
 * server; the client does not know the player's STR there, so the HUD shows the force band only.
 */
public final class PowerMiningMeterHud {

    static final int IDLE = 0x9FE9FF, WHITE = 0xF4F7FF, GREEN = 0x46FF7E, ORANGE = 0xFF9A2E, RED = 0xFF3A3A;

    private static final Sprite[] BRACKETS = {
            Sprite.of("mining/bracket_1", 40, 72), Sprite.of("mining/bracket_2", 40, 72),
            Sprite.of("mining/bracket_3", 40, 72), Sprite.of("mining/bracket_4", 40, 72)};
    private static final Sprite BRACKET_GLOW = Sprite.of("mining/bracket_glow", 88, 120);
    private static final Sprite STRESS = Sprite.of("mining/stress", 64, 96);
    private static final Sprite RAIL = Sprite.of("mining/rail_frame", 256, 32);
    private static final Sprite SEGMENT = Sprite.of("mining/segment", 12, 20);
    private static final Sprite MARKER = Sprite.of("mining/marker", 20, 28);
    private static final Sprite BURST = Sprite.of("mining/burst", 192, 192);
    private static final Sprite SHOCK = Sprite.of("mining/shock", 128, 128);

    private static final int SEGMENTS = 20;
    private static final float RAIL_W = 46, RAIL_H = 6, RAIL_Y = 11;
    private static final long DISCHARGE_NANOS = 260_000_000L, IMPACT_NANOS = 420_000_000L, CANCEL_NANOS = 180_000_000L;
    /** A strike result belongs to the last release when it arrives this soon after it. */
    private static final long RESULT_WINDOW_NANOS = 1_500_000_000L;

    private static int lastBand;
    private static long popNanos = -1;
    private static float shownCharge;
    private static int shownBand = MiningTuning.BAND_WHITE;
    private static long lastFrame;
    private static float appear;

    /** Diagnostics (capture run): what the last frame drew. */
    static String lastState = "hidden";
    static float lastBracketOffset;

    private PowerMiningMeterHud() {}

    /** Diagnostics: "hidden", "idle", "charging_&lt;band&gt;", "discharge", "impact" or "cancelled". */
    public static String lastState() {
        return lastState;
    }

    /** Diagnostics: the charge the last frame showed. */
    public static float lastCharge() {
        return shownCharge;
    }

    /** Diagnostics: the bracket distance the last frame used (GUI px). */
    public static float lastBracketOffset() {
        return lastBracketOffset;
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "power_mining_meter"),
                (graphics, delta) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || mc.gui.hud.isHidden()) return;
                    draw(graphics, delta.getGameTimeDeltaPartialTick(false));
                });
    }

    /** The charge the HUD shows: the gameplay meter function, interpolated between client ticks. */
    static float charge(float partialTick) {
        int t = ClientMiningController.meterTicks();
        float a = MiningTuning.meterValue(t), b = MiningTuning.meterValue(t + 1);
        return Mth.lerp(Mth.clamp(partialTick, 0, 1), a, b);
    }

    static int colour(int band) {
        return switch (band) {
            case MiningTuning.BAND_GREEN -> GREEN;
            case MiningTuning.BAND_ORANGE -> ORANGE;
            case MiningTuning.BAND_RED -> RED;
            default -> WHITE;
        };
    }

    /** Bracket inner edge distance from the crosshair centre (GUI px) for a charge 0..1. */
    static float bracketOffset(float charge, int band) {
        // Continuous outward travel with a step at each threshold, so a stage change reads as a push.
        float stageStep = switch (band) {
            case MiningTuning.BAND_GREEN -> 2.0f;
            case MiningTuning.BAND_ORANGE -> 3.5f;
            case MiningTuning.BAND_RED -> 5.0f;
            default -> 0;
        };
        return 7.5f + 16f * charge + stageStep;
    }

    private static void draw(GuiGraphicsExtractor g, float partial) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
        boolean charging = ClientMiningController.isMeterActive();
        boolean idle = !charging && ClientMiningController.isIdleArmed();

        long release = ClientMiningController.lastReleaseNanos();
        long cancel = ClientMiningController.lastCancelNanos();
        long strike = ClientMiningController.lastStrikeNanos();
        boolean discharging = !charging && release > 0 && now - release < DISCHARGE_NANOS;
        boolean impact = !charging && strike > 0 && release > 0 && strike >= release && strike - release < RESULT_WINDOW_NANOS
                && now - strike < IMPACT_NANOS && ClientMiningController.lastStrikeOutcome().landed();
        boolean cancelling = !charging && cancel > 0 && now - cancel < CANCEL_NANOS && (release < 0 || cancel > release);

        if (charging) {
            shownCharge = charge(partial);
            shownBand = MiningTuning.presentationBand(shownCharge, false);
            if (shownBand != lastBand) {
                if (shownBand > lastBand) popNanos = now;      // a threshold was reached
                lastBand = shownBand;
            }
        } else if (!discharging && !cancelling) {
            lastBand = MiningTuning.BAND_WHITE;
        }
        boolean visible = charging || idle || discharging || cancelling || impact;
        appear += ((visible ? 1 : 0) - appear) * (1 - (float) Math.exp(-dt / (visible ? 0.05f : 0.09f)));

        if (impact) drawImpact(g, cx, cy, (now - strike) / (float) IMPACT_NANOS);
        if (appear < 0.02f) {
            lastState = impact ? "impact" : "hidden";
            return;
        }
        float a = appear;
        if (charging) {
            float pop = popNanos > 0 ? Math.max(0, 1 - (now - popNanos) / 260e6f) : 0;
            drawBrackets(g, cx, cy, bracketOffset(shownCharge, shownBand) + 2.5f * pop, shownBand, a, pop, now);
            drawRail(g, cx, cy, shownCharge, shownBand, a);
            lastState = "charging_" + bandName(shownBand);
            lastBracketOffset = bracketOffset(shownCharge, shownBand);
        } else if (discharging) {
            float k = (now - release) / (float) DISCHARGE_NANOS;
            int band = MiningTuning.presentationBand(ClientMiningController.lastReleaseForce(), false);
            // Brackets flare outward and fade; a ring discharges from the reticle toward the strike.
            drawBrackets(g, cx, cy, bracketOffset(ClientMiningController.lastReleaseForce(), band) + 9 * k, band,
                    a * (1 - k), 0, now);
            HudSprites.drawCentred(g, SHOCK, cx, cy, 0.35f + 0.9f * k, 0, HudSprites.argb(0.8f * (1 - k), colour(band)), true);
            drawRail(g, cx, cy, ClientMiningController.lastReleaseForce(), band, a * (1 - k));
            lastState = "discharge";
        } else if (cancelling) {
            float k = (now - cancel) / (float) CANCEL_NANOS;
            drawBrackets(g, cx, cy, bracketOffset(shownCharge, shownBand) * (1 - 0.5f * k), MiningTuning.BAND_WHITE,
                    a * (1 - k), 0, now, 0x8A96A8);
            drawRail(g, cx, cy, 0, MiningTuning.BAND_WHITE, a * (1 - k));
            lastState = "cancelled";
        } else {
            // Idle: close brackets, empty rail, restrained holographic cyan-white.
            drawBrackets(g, cx, cy, bracketOffset(0, MiningTuning.BAND_WHITE) - 1, 0, a * 0.85f, 0, now, IDLE);
            drawRail(g, cx, cy, 0, 0, a * 0.85f);
            lastState = impact ? "impact" : "idle";
            lastBracketOffset = bracketOffset(0, MiningTuning.BAND_WHITE) - 1;
        }
    }

    private static void drawBrackets(GuiGraphicsExtractor g, float cx, float cy, float offset, int band, float a, float pop, long now) {
        drawBrackets(g, cx, cy, offset, band, a, pop, now, band == 0 ? IDLE : colour(band));
    }

    private static void drawBrackets(GuiGraphicsExtractor g, float cx, float cy, float offset, int band, float a, float pop,
                                     long now, int rgb) {
        Sprite sprite = BRACKETS[Mth.clamp(band <= 0 ? 0 : band - 1, 0, 3)];
        float scale = switch (band) {
            case MiningTuning.BAND_GREEN -> 0.82f;
            case MiningTuning.BAND_ORANGE -> 0.9f;
            case MiningTuning.BAND_RED -> 0.98f;
            default -> 0.72f;
        };
        float w = sprite.guiWidth() * scale, h = sprite.guiHeight() * scale;
        float jitter = band == MiningTuning.BAND_RED ? 0.35f * Mth.sin(now / 1e9f * 47f) : 0;
        float lx = cx - offset - w + jitter, rx = cx + offset - jitter, y = cy - h / 2;
        float glowA = a * (band <= 1 ? 0.22f : 0.28f + 0.08f * band + 0.5f * pop);
        float gw = BRACKET_GLOW.guiWidth() * scale, gh = BRACKET_GLOW.guiHeight() * scale;
        float gx = (gw - w) / 2;
        HudSprites.draw(g, BRACKET_GLOW, lx - gx, cy - gh / 2, gw, gh, HudSprites.argb(glowA, rgb), true);
        HudSprites.drawMirrored(g, BRACKET_GLOW, rx - gx, cy - gh / 2, gw, gh, HudSprites.argb(glowA, rgb), true);
        // A dark underlay keeps the bracket legible on bright snow, sand or daylight sky.
        HudSprites.draw(g, sprite, lx + 0.5f, y + 0.5f, w, h, HudSprites.argb(a * 0.45f, 0x000000), false);
        HudSprites.drawMirrored(g, sprite, rx - 0.5f, y + 0.5f, w, h, HudSprites.argb(a * 0.45f, 0x000000), false);
        HudSprites.draw(g, sprite, lx, y, w, h, HudSprites.argb(a, rgb), false);
        HudSprites.drawMirrored(g, sprite, rx, y, w, h, HudSprites.argb(a, rgb), false);
        if (band == MiningTuning.BAND_RED) {
            float flicker = 0.35f + 0.25f * (0.5f + 0.5f * Mth.sin(now / 1e9f * 23f));
            float sw = STRESS.guiWidth() * 0.8f, sh = STRESS.guiHeight() * 0.8f;
            HudSprites.draw(g, STRESS, lx - sw * 0.35f, cy - sh / 2, sw, sh, HudSprites.argb(a * flicker, rgb), true);
            HudSprites.drawMirrored(g, STRESS, rx + w - sw * 0.65f, cy - sh / 2, sw, sh, HudSprites.argb(a * flicker, rgb), true);
        }
    }

    /** The rail: frame, 20 slanted segments (filled to the exact charge), markers at the real thresholds. */
    private static void drawRail(GuiGraphicsExtractor g, float cx, float cy, float charge, int band, float a) {
        float x = cx - RAIL_W / 2, y = cy + RAIL_Y;
        HudSprites.threeSlice(g, RAIL, x, y, RAIL_W, RAIL_H, 5, HudSprites.argb(a * 0.55f, 0x02060E), false);
        HudSprites.threeSlice(g, RAIL, x, y, RAIL_W, RAIL_H, 5, HudSprites.argb(a * 0.9f, band == 0 ? IDLE : colour(band)), false);
        float inner = RAIL_W - 6, pitch = inner / SEGMENTS;
        float filled = charge * SEGMENTS;
        int fill = band == 0 ? IDLE : colour(band);
        for (int i = 0; i < SEGMENTS; i++) {
            float mid = (i + 0.5f) / SEGMENTS;
            int zone = MiningTuning.presentationBand(mid, false);
            float sx = x + 3 + i * pitch, sy = y + 1.1f;
            float part = Mth.clamp(filled - i, 0, 1);
            // Unreached segments stay faintly tinted with their own zone, so the next threshold is visible.
            HudSprites.draw(g, SEGMENT, sx, sy, pitch * 0.85f, RAIL_H - 2.2f,
                    HudSprites.argb(a * (band == 0 ? 0.16f : 0.2f), band == 0 ? IDLE : colour(zone)), false);
            if (part > 0) {
                HudSprites.draw(g, SEGMENT, sx, sy, pitch * 0.85f, RAIL_H - 2.2f, HudSprites.argb(a * (0.35f + 0.65f * part), fill), false);
            }
        }
        float[] thresholds = {MiningTuning.WHITE_ZONE_MAX, MiningTuning.GREEN_ZONE_MAX, MiningTuning.RED_ZONE};
        int[] colours = {GREEN, ORANGE, RED};
        for (int i = 0; i < thresholds.length; i++) {
            float mx = x + 3 + inner * thresholds[i];
            boolean reached = charge >= thresholds[i];
            int col = band == 0 ? IDLE : colours[i];
            float ma = a * (band == 0 ? 0.35f : reached ? 1f : 0.55f);
            HudSprites.draw(g, MARKER, mx - 1.75f, y - 4.2f, 3.5f, 4.9f, HudSprites.argb(ma, col), false);
        }
    }

    /** Impact: a compact burst at the strike point, only for a strike the server reports as landed. */
    private static void drawImpact(GuiGraphicsExtractor g, float cx, float cy, float k) {
        int band = MiningTuning.presentationBand(ClientMiningController.lastReleaseForce(), false);
        float ease = 1 - (1 - k) * (1 - k);
        HudSprites.drawCentred(g, BURST, cx, cy, 0.45f + 0.55f * ease, k * 0.6f,
                HudSprites.argb(0.85f * (1 - k), colour(band)), true);
        HudSprites.drawCentred(g, BURST, cx, cy, 0.3f + 0.4f * ease, -k * 0.4f, HudSprites.argb(0.5f * (1 - k), 0xFFFFFF), true);
    }

    static String bandName(int band) {
        return switch (band) {
            case MiningTuning.BAND_GREEN -> "green";
            case MiningTuning.BAND_ORANGE -> "orange";
            case MiningTuning.BAND_RED -> "red";
            default -> "white";
        };
    }
}
