package zcylas.totality.client.hologram;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import zcylas.totality.init.ModKeybinds;

/**
 * Renders System holograms as real world-space panels, from a pass that runs after the world has
 * been fully drawn (terrain, entities, water, particles, weather, clouds) and before the first-person
 * hand — invoked by {@code GameRendererHologramMixin}.
 *
 * <p><b>Why this pass.</b> Vanilla has just switched to its fixed 70° "hud" perspective (the one the
 * hand uses: unaffected by FOV setting, sprinting or speed effects, near plane 0.05) and cleared depth.
 * The panel is positioned and oriented in camera-relative world space under the true view rotation,
 * so it is a genuinely 3D object seen in perspective, yet walls, water and weather can never cut
 * through it and the near plane can never clip it; the hand still draws in front. It is drawn only on
 * this client: no entity, no block, nothing another player could see.
 *
 * <p><b>Size.</b> One panel unit is ¾ of a GUI pixel on screen, so holograms follow the player's GUI-scale
 * choice like the rest of the interface, shrinking if needed to stay within ~62% of the window width
 * and ~60% of its height at any resolution.
 */
public final class HologramRenderer {

    private static final HologramAnchor ANCHOR = new HologramAnchor();
    /** Third person: anchored at the CHARACTER's eye, along the character's view (not the camera's). */
    private static final HologramAnchor CHARACTER_ANCHOR = new HologramAnchor();
    private static final SubmitNodeStorage STORAGE = new SubmitNodeStorage();

    /** Panel units per GUI pixel: a hologram is a notch smaller than the flat GUI at the same scale. */
    private static final float UNIT_PER_GUI_PIXEL = 0.75f;
    private static final float REST_GAP_DEG = 2.5f;
    private static final float MAX_TOP_DEG = 30;
    private static final float MAX_SCREEN_W = 0.62f;
    private static final float MAX_SCREEN_H = 0.60f;
    private static final float GHOST_SCALE = 0.6f;
    private static final float GHOST_ALPHA = 0.5f;
    private static final float AIM_TOLERANCE = 0.5f;
    private static final int WHITE = 0xFFFFFF;

    // Third-person projected window (see renderWorldPass).
    /** Share of the view width the window aims for, within the physical limits below. */
    private static final double WORLD_VIEW_SHARE = 0.42;
    private static final double WORLD_MIN_WIDTH = 1.4;
    private static final double WORLD_MAX_WIDTH = 4.4;
    /** Its bottom edge sits this far above the character's eyes: the head only brushes the lower margin. */
    private static final double WORLD_BOTTOM_ABOVE_EYES = 0.05;
    /**
     * The window stays a mostly upright physical pane: it takes only this share of the character's
     * pitch, within the limits below, so a steep camera never tips it into (or over) the character.
     */
    private static final float WORLD_PITCH_SHARE = 0.3f;
    private static final float WORLD_PITCH_MIN = -8;
    private static final float WORLD_PITCH_MAX = 7;

    private static long lastFrameNanos;
    private static @Nullable CameraType lastCameraType;
    private static double smoothedWidth = -1;
    /** What drew the last frame: first_person, third_person or third_person_rear_face (diagnostics). */
    static String lastPlacement = "first_person";
    private static @Nullable HologramLayout lastLayout;

    private static float firstPersonBottom;
    private static long firstPersonBottomNanos;

    private HologramRenderer() {}

    /**
     * The first-person panel's lower edge as drawn in the latest frame, as a fraction of the half screen
     * height below the centre (0 = crosshair, 1 = bottom edge; negative = above), or NaN when no
     * first-person panel is on screen. Render thread (HUD layout).
     */
    public static float firstPersonPanelBottom() {
        HologramStack.Entry active = HologramManager.stack().active();
        boolean recent = System.nanoTime() - firstPersonBottomNanos < 150_000_000L;
        return active != null && recent && "first_person".equals(lastPlacement) ? firstPersonBottom : Float.NaN;
    }

    /** A hologram (re)appeared: project it in front of the current view. */
    static void onOpened() {
        ANCHOR.reset();
        CHARACTER_ANCHOR.reset();
        smoothedWidth = -1;
    }

    /** Common per-frame preparation; null when nothing should be drawn this frame. */
    private static @Nullable HologramLayout prepare(Minecraft mc, HologramStack.Entry active, long now) {
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
            clearAim(active);
            return null;
        }
        CameraType cameraType = mc.options.getCameraType();
        if (cameraType != lastCameraType) {
            lastCameraType = cameraType;
            onOpened();
        }
        float dt = lastFrameNanos == 0 ? 0 : Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        HologramLayout layout = new HologramLayout(active.spec(), mc.font);
        lastLayout = layout;
        if (active.displayedHeight < 0) active.displayedHeight = layout.height;
        active.displayedHeight += (layout.height - active.displayedHeight) * (1 - (float) Math.exp(-dt * 14));
        return layout;
    }

    /**
     * First-person pass (hand pass: fixed hud projection, depth cleared). Client/render thread, once
     * per frame. Third person is drawn by {@link #renderWorldPass} instead.
     */
    public static void renderPass(GameRenderer gameRenderer) {
        Minecraft mc = Minecraft.getInstance();
        HologramStack stack = HologramManager.stack();
        HologramStack.Entry active = stack.active();
        if (active == null || !mc.options.getCameraType().isFirstPerson()) return;
        CameraRenderState cam = gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (cam.isPanoramicMode) return;
        long now = stack.now();
        HologramLayout layout = prepare(mc, active, now);
        if (layout == null) return;
        Camera camera = gameRenderer.mainCamera();
        Font font = mc.font;
        lastPlacement = "first_person";

        int winW = mc.getWindow().getWidth(), winH = mc.getWindow().getHeight();
        double unitPx = mc.getWindow().getGuiScale() * UNIT_PER_GUI_PIXEL;
        unitPx = Math.min(unitPx, MAX_SCREEN_W * winW / layout.width);
        unitPx = Math.min(unitPx, MAX_SCREEN_H * winH / layout.height);
        double worldPerPx = 2 * HologramAnchor.DISTANCE * Math.tan(Math.toRadians(cam.hudFov / 2)) / Math.max(1, winH);
        float unit = (float) (unitPx * worldPerPx);

        float halfHeightDeg = degrees(active.displayedHeight * unit / 2);
        float restUp = Math.max(0, Math.min(halfHeightDeg + REST_GAP_DEG, MAX_TOP_DEG - halfHeightDeg));
        Vec3 camPos = cam.pos;
        ANCHOR.update(camPos, camera.yRot(), camera.xRot(), restUp, 0, 0, now);
        // Where the panel's lower edge lands on screen (other HUD elements keep clear of it).
        double bottomDeg = ANCHOR.pitch() - camera.xRot() + halfHeightDeg;
        firstPersonBottom = (float) (Math.tan(Math.toRadians(bottomDeg)) / Math.tan(Math.toRadians(cam.hudFov / 2)));
        firstPersonBottomNanos = System.nanoTime();

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(cam.viewRotationMatrix);
        try {
            PoseStack pose = new PoseStack();
            HologramPainter painter = new HologramPainter(STORAGE, pose, 0);
            HologramStack.Entry ghost = stack.suspendedTop();
            if (ghost != null) {
                HologramLayout ghostLayout = new HologramLayout(ghost.spec(), font);
                // Tucked partly behind the active panel's left edge, slightly lower: clearly "set aside".
                float yawOffset = degrees(layout.width * unit / 2) + degrees(ghostLayout.width * unit * GHOST_SCALE / 2) * 0.45f;
                paintGhost(painter, ghost, ghostLayout, font, ANCHOR.panelOffset(camPos, -yawOffset, 4),
                        ANCHOR.yaw() - yawOffset, ANCHOR.pitch() + 4, -14, unit * GHOST_SCALE, now);
            }
            paintActive(painter, active, layout, font, ANCHOR.panelOffset(camPos, 0, 0), ANCHOR.yaw(), ANCHOR.pitch(),
                    unit, now, true, mc.gui.screen() == null, camera.forwardVector(), false);
            gameRenderer.featureRenderDispatcher().renderAllFeatures(STORAGE);
        } finally {
            modelView.popMatrix();
        }
    }

    /**
     * Third-person pass, run right after the world is drawn (world projection and world depth still
     * current): the System projects a real window IN FRONT OF THE CHARACTER — anchored at the
     * character's eyes along the character's (dampened) view, sized as a physical object, tested against
     * the world's depth so the character (and terrain) genuinely stand in front of it. Its readable face
     * belongs to the character; a front-view camera sees the subdued rear face. Technique informed by the Solo Leveling System addon's
     * third-person windows (eye-anchored, depth-tested); implementation is Totality's own.
     */
    public static void renderWorldPass(GameRenderer gameRenderer) {
        Minecraft mc = Minecraft.getInstance();
        HologramStack stack = HologramManager.stack();
        HologramStack.Entry active = stack.active();
        if (active == null || mc.options.getCameraType().isFirstPerson()) return;
        CameraRenderState cam = gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (cam.isPanoramicMode) return;
        Entity character = mc.getCameraEntity();
        if (character == null) return;
        long now = stack.now();
        HologramLayout layout = prepare(mc, active, now);
        if (layout == null) return;
        Camera camera = gameRenderer.mainCamera();
        Font font = mc.font;
        lastPlacement = "third_person";
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        Vec3 camPos = cam.pos;
        Vec3 eye = character.getEyePosition(partial);
        float lookYaw = character.getViewYRot(partial);
        float lookPitch = Mth.clamp(character.getViewXRot(partial) * WORLD_PITCH_SHARE, WORLD_PITCH_MIN, WORLD_PITCH_MAX);
        CHARACTER_ANCHOR.update(eye, lookYaw, lookPitch, 0, 0, 0, now);

        // Physical size: a share of the view at the window's distance, within fixed limits, smoothed so a
        // camera pushed in by a wall does not make the window jump.
        double distance = CHARACTER_ANCHOR.panelOffsetInView(camPos, 0, 0).length();
        double tanHalf = Math.tan(Math.toRadians(camera.getFov() / 2));
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double width = Mth.clamp(WORLD_VIEW_SHARE * 2 * distance * tanHalf * aspect, WORLD_MIN_WIDTH, WORLD_MAX_WIDTH);
        smoothedWidth = smoothedWidth < 0 ? width : smoothedWidth + (width - smoothedWidth) * 0.15;
        float unit = (float) (smoothedWidth / layout.width);
        unit = (float) Math.min(unit, 0.55 * 2 * distance * tanHalf / layout.height);
        double lift = active.displayedHeight * unit / 2 + WORLD_BOTTOM_ABOVE_EYES;
        Vec3 centre = CHARACTER_ANCHOR.panelOffsetInView(camPos, 0, lift);

        // The projection is for the CHARACTER: its readable face always looks back along the character's
        // view. A camera on the other side (front view) sees its rear surface, never a flipped copy.
        float yaw = CHARACTER_ANCHOR.yaw(), pitch = CHARACTER_ANCHOR.pitch();
        Vec3 readableFaceNormal = HologramAnchor.direction(yaw, pitch).scale(-1);
        boolean rear = readableFaceNormal.dot(centre.scale(-1)) < 0;
        lastPlacement = rear ? "third_person_rear_face" : "third_person";

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(cam.viewRotationMatrix);
        try {
            PoseStack pose = new PoseStack();
            HologramPainter painter = new HologramPainter(STORAGE, pose, 0);
            HologramStack.Entry ghost = stack.suspendedTop();
            HologramLayout ghostLayout = ghost == null ? null : new HologramLayout(ghost.spec(), font);
            // WORLD depth only (no x-ray pass): whatever is physically nearer the camera — the character,
            // terrain — occludes the window, and the window, when nearer, composites translucently over
            // what is behind it. An x-ray silhouette would paint over a character standing in front.
            HologramRenderTypes.mode = HologramRenderTypes.Mode.WORLD;
            if (ghost != null && !rear) {
                Vec3 ghostCentre = CHARACTER_ANCHOR.panelOffsetInView(camPos,
                        -(layout.width / 2 + ghostLayout.width * GHOST_SCALE * 0.45f) * unit, lift - 4 * unit);
                paintGhost(painter, ghost, ghostLayout, font, ghostCentre, yaw, pitch, 0, unit * GHOST_SCALE, now);
            }
            paintActive(painter, active, layout, font, centre, yaw, pitch, unit, now, true, mc.gui.screen() == null,
                    camera.forwardVector(), rear);
            gameRenderer.featureRenderDispatcher().renderAllFeatures(STORAGE);
        } finally {
            HologramRenderTypes.mode = HologramRenderTypes.Mode.OVERLAY;
            modelView.popMatrix();
        }
    }

    // ── Active panel ──────────────────────────────────────────────────────────

    /**
     * @param offset panel centre relative to the camera; {@code yaw}/{@code pitch} the direction it faces away from
     * @param aim    whether this call owns the crosshair hit test (once per frame)
     * @param rear   the viewer is behind the projection: draw its rear surface, nothing to read or touch
     */
    private static void paintActive(HologramPainter p, HologramStack.Entry e, HologramLayout layout, Font font,
                                    Vec3 offset, float yaw, float pitch, float unit, long now, boolean aim,
                                    boolean inputAllowed, Vector3fc forward, boolean rear) {
        float ms = (now - e.phaseStartNanos) / 1e6f;
        float reveal = 1, content = 1, tilt = 0, flash = 0, flashWidth = 1, sweep = -1;
        switch (e.phase) {
            case OPENING -> {
                flashWidth = easeOut(clamp01(ms / 90));
                flash = 1 - clamp01((ms - 200) / 140);
                reveal = Math.max(0.03f, easeOut(clamp01((ms - 60) / 220)));
                content = clamp01((ms - 170) / 200);
                tilt = 10 * (1 - easeOut(clamp01(ms / 380)));
                sweep = clamp01((ms - 120) / 420);
            }
            case SHOWN -> {
                long sinceRefresh = now - e.refreshNanos;
                if (e.refreshNanos != Long.MIN_VALUE && sinceRefresh < 260_000_000L) {
                    float r = sinceRefresh / 260e6f;
                    content = 0.2f + 0.8f * easeOut(r);
                    flash = 0.6f * (1 - r);
                    flashWidth = 1;
                }
            }
            case CLOSING -> {
                content = 1 - clamp01(ms / 90);
                reveal = Math.max(0.03f, 1 - easeIn(clamp01((ms - 40) / 150)));
                flash = clamp01((ms - 110) / 50) * (1 - clamp01((ms - 200) / 60));
                flashWidth = 1 - easeIn(clamp01((ms - 190) / 70));
            }
        }
        float w = layout.width, h = e.displayedHeight;
        float base = p.alpha;
        PoseStack pose = p.pose;
        pose.pushPose();
        orient(pose, offset, yaw, pitch, unit, tilt);
        HologramStyle style = e.spec().style();

        pose.pushPose();
        pose.scale(1, reveal, 1);
        pose.translate(-w / 2, -h / 2, 0);
        if (aim) {
            if (e.phase == HologramStack.Phase.SHOWN && !rear) updateAim(e, layout, h, p.matrix(), forward, inputAllowed, now);
            else clearAim(e);
        }
        if (rear) paintRear(p, e, style, w, h, content, now);
        else paintPanel(p, e, layout, font, style, w, h, content, sweep, now, false);
        pose.popPose();

        if (flash > 0.01f) {
            float half = w / 2 * flashWidth + 8;
            p.alpha = base * flash;
            p.quad(HologramRenderTypes.LINE.additive(), -half, -4, half, 4, 0, 0, 1, 1,
                    HologramPainter.argb(1, HologramPainter.mixRgb(style.accent, WHITE, 0.55f)),
                    HologramPainter.argb(1, HologramPainter.mixRgb(style.accent, WHITE, 0.55f)));
            p.alpha = base;
        }
        pose.popPose();
    }

    private static void paintPanel(HologramPainter p, HologramStack.Entry e, HologramLayout layout, Font font,
                                   HologramStyle style, float w, float h, float content, float sweep, long now,
                                   boolean ghost) {
        float t = now / 1e9f;
        float pulse = 0.82f + 0.18f * Mth.sin(t * Mth.TWO_PI / 2.6f);
        int accent = style.accent;
        float base = p.alpha;

        // Light and surface.
        p.nineSlice(HologramRenderTypes.GLOW, true, -10, -10, w + 10, h + 10, 80, 20,
                HologramPainter.argb(0.5f * pulse, accent), HologramPainter.argb(0.5f * pulse, accent));
        p.nineSlice(HologramRenderTypes.SURFACE, false, 0, 0, w, h, 24, 6,
                HologramPainter.argb(0.8f, HologramPainter.mixRgb(style.surface, accent, 0.14f)),
                HologramPainter.argb(0.74f, style.surface));
        float drift = (t * 0.05f) % 1;
        p.quad(HologramRenderTypes.GRID.additive(), 3, 3, w - 3, h - 3, 0, drift, (w - 6) / 16, (h - 6) / 16 + drift,
                HologramPainter.argb(0.085f, accent), HologramPainter.argb(0.05f, accent));
        p.quad(HologramRenderTypes.WHITE.additive(), 3, 3, w - 3, Math.min(h - 3, 3 + h * 0.4f), 0.25f, 0.25f, 0.75f, 0.75f,
                HologramPainter.argb(0.09f, accent), HologramPainter.argb(0, accent));
        if (!ghost) {
            float period = 4.5f;
            float idle = ((t % period) / period) * (h + 60) - 30;
            scanline(p, w, h, idle, 0.09f, accent);
            if (sweep >= 0 && sweep < 1) scanline(p, w, h, sweep * (h + 30) - 15, 0.4f * (1 - sweep), accent);
        }

        // Frame: crisp line, then an additive copy that pulses with the glow.
        p.nineSlice(HologramRenderTypes.FRAME, false, -2, -2, w + 2, h + 2, 32, 8,
                HologramPainter.argb(0.95f, HologramPainter.mixRgb(accent, WHITE, 0.18f)),
                HologramPainter.argb(0.9f, accent));
        p.nineSlice(HologramRenderTypes.FRAME, true, -2, -2, w + 2, h + 2, 32, 8,
                HologramPainter.argb(0.35f * pulse, accent), HologramPainter.argb(0.35f * pulse, accent));

        p.alpha = base * content;

        // Header tab: icon + letter-spaced header.
        float tabMidY = (layout.tabY0 + layout.tabY1) / 2;
        p.nineSlice(HologramRenderTypes.BUTTON_FILL, false, layout.tabX0, layout.tabY0, layout.tabX1, layout.tabY1, 8, 2,
                HologramPainter.argb(0.26f, accent), HologramPainter.argb(0.1f, accent));
        p.nineSlice(HologramRenderTypes.BUTTON_FRAME, false, layout.tabX0, layout.tabY0, layout.tabX1, layout.tabY1, 8, 2,
                HologramPainter.argb(0.85f, accent), HologramPainter.argb(0.85f, accent));
        float iconX = layout.tabX0 + 5 + HologramLayout.TAB_ICON / 2;
        p.sprite(HologramRenderTypes.ICON_HALO.additive(), iconX - 11, tabMidY - 11, iconX + 11, tabMidY + 11,
                HologramPainter.argb(0.4f, accent));
        HologramIcon icon = e.spec().icon();
        float spin = icon == HologramIcon.SPINNER ? (t * Mth.TWO_PI / 1.1f) % Mth.TWO_PI : 0;
        p.icon(icon, iconX, tabMidY, HologramLayout.TAB_ICON, HologramPainter.argb(1, style.highlight), spin);
        p.spacedText(font, layout.headerText, layout.tabX0 + 5 + HologramLayout.TAB_ICON + 5,
                tabMidY - 8 * HologramLayout.HEADER_SCALE / 2 + 0.5f, HologramLayout.HEADER_SCALE,
                HologramLayout.HEADER_SPACING, HologramPainter.argb(1, style.highlight));

        // Divider with a bright core.
        p.quad(HologramRenderTypes.LINE.additive(), HologramLayout.PAD - 8, layout.dividerY - 3, w - HologramLayout.PAD + 8,
                layout.dividerY + 3, 0, 0, 1, 1, HologramPainter.argb(0.7f, accent), HologramPainter.argb(0.7f, accent));

        // Title with a soft halo.
        if (!layout.title.isEmpty()) {
            HologramLayout.Line first = layout.title.getFirst();
            HologramLayout.Line last = layout.title.getLast();
            p.quad(HologramRenderTypes.LINE.additive(), w / 2 - layout.titleWidth / 2 - 30, first.y() - 5,
                    w / 2 + layout.titleWidth / 2 + 30, last.y() + 9 * last.scale() + 3, 0, 0, 1, 1,
                    HologramPainter.argb(0.2f, accent), HologramPainter.argb(0.2f, accent));
        }
        for (HologramLayout.Line line : layout.title) p.text(line.text(), line.x(), line.y(), line.scale(), HologramPainter.argb(1, style.title));
        if (ghost) {
            p.alpha = base;
            return;
        }
        for (HologramLayout.Line line : layout.body) p.text(line.text(), line.x(), line.y(), 1, HologramPainter.argb(1, style.body));
        for (HologramLayout.Line line : layout.footnote) p.text(line.text(), line.x(), line.y(), line.scale(), HologramPainter.argb(0.62f, style.body));

        if (layout.progressY >= 0) indeterminateBar(p, w, layout.progressY, t, accent);
        for (HologramLayout.Button b : layout.buttons) button(p, e, b, font, style, now);
        if (layout.voiceY >= 0) voiceLine(p, e, layout, font, style, now);

        // Remaining lifetime: a thin line along the bottom edge shrinking toward the centre.
        int lifetime = e.spec().lifetimeTicks();
        if (lifetime > 0) {
            float frac = clamp01((float) e.ticksRemaining() / lifetime);
            float half = (w / 2 - 12) * frac;
            p.fill(w / 2 - half, h - 5.2f, w / 2 + half, h - 4.5f, HologramPainter.argb(e.aimedAt ? 0.85f : 0.5f, accent));
        }

        if (e.aimedAt) {
            p.sprite(HologramRenderTypes.HIT_RING.additive(), e.aimX - 4, e.aimY - 4, e.aimX + 4, e.aimY + 4,
                    HologramPainter.argb(e.hoveredAction != null ? 1 : 0.55f, HologramPainter.mixRgb(accent, WHITE, 0.3f)));
        }
        p.alpha = base;
    }

    /**
     * The reverse side of a projection meant for someone else: the same silhouette, frame and light,
     * subdued, with faint circuitry and the System emblem — no text, no buttons, nothing to press.
     */
    private static void paintRear(HologramPainter p, HologramStack.Entry e, HologramStyle style, float w, float h,
                                  float content, long now) {
        float t = now / 1e9f;
        float pulse = 0.82f + 0.18f * Mth.sin(t * Mth.TWO_PI / 2.6f);
        int accent = style.accent;
        float base = p.alpha;
        p.nineSlice(HologramRenderTypes.GLOW, true, -10, -10, w + 10, h + 10, 80, 20,
                HologramPainter.argb(0.3f * pulse, accent), HologramPainter.argb(0.3f * pulse, accent));
        p.nineSlice(HologramRenderTypes.SURFACE, false, 0, 0, w, h, 24, 6,
                HologramPainter.argb(0.55f, style.surface), HologramPainter.argb(0.5f, style.surface));
        float drift = (t * 0.05f) % 1;
        p.quad(HologramRenderTypes.GRID.additive(), 3, 3, w - 3, h - 3, 0, drift, (w - 6) / 16, (h - 6) / 16 + drift,
                HologramPainter.argb(0.06f, accent), HologramPainter.argb(0.035f, accent));
        p.nineSlice(HologramRenderTypes.FRAME, false, -2, -2, w + 2, h + 2, 32, 8,
                HologramPainter.argb(0.5f, accent), HologramPainter.argb(0.45f, accent));
        p.alpha = base * content;
        p.quad(HologramRenderTypes.LINE.additive(), 20, h / 2 - 3, w - 20, h / 2 + 3, 0, 0, 1, 1,
                HologramPainter.argb(0.25f, accent), HologramPainter.argb(0.25f, accent));
        p.sprite(HologramRenderTypes.ICON_HALO.additive(), w / 2 - 26, h / 2 - 26, w / 2 + 26, h / 2 + 26,
                HologramPainter.argb(0.18f, accent));
        p.icon(e.spec().icon() == HologramIcon.SPINNER ? HologramIcon.SYSTEM : e.spec().icon(), w / 2, h / 2, 26,
                HologramPainter.argb(0.22f, style.highlight), 0);
        p.alpha = base;
    }

    private static void scanline(HologramPainter p, float w, float h, float centre, float alpha, int accent) {
        float half = 11;
        float y0 = Math.max(2, centre - half), y1 = Math.min(h - 2, centre + half);
        if (y1 <= y0) return;
        float v0 = (y0 - (centre - half)) / (2 * half), v1 = (y1 - (centre - half)) / (2 * half);
        p.quad(HologramRenderTypes.SCANLINE.additive(), 2, y0, w - 2, y1, 0, v0, 1, v1,
                HologramPainter.argb(alpha, accent), HologramPainter.argb(alpha, accent));
    }

    private static void indeterminateBar(HologramPainter p, float w, float y, float t, int accent) {
        float x0 = HologramLayout.PAD + 14, x1 = w - HologramLayout.PAD - 14;
        p.fill(x0, y, x1, y + 1.5f, HologramPainter.argb(0.25f, accent));
        float phase = (t % 1.6f) / 1.6f;
        float pos = 0.5f - 0.5f * Mth.cos(phase * Mth.TWO_PI);
        float seg = (x1 - x0) * 0.28f;
        float sx = x0 + pos * (x1 - x0 - seg);
        p.quad(HologramRenderTypes.LINE.additive(), sx - 6, y - 5, sx + seg + 6, y + 6.5f, 0, 0, 1, 1,
                HologramPainter.argb(0.95f, accent), HologramPainter.argb(0.95f, accent));
        p.fill(sx, y, sx + seg, y + 1.5f, HologramPainter.argb(0.95f, HologramPainter.mixRgb(accent, WHITE, 0.5f)));
    }

    private static void button(HologramPainter p, HologramStack.Entry e, HologramLayout.Button b, Font font,
                               HologramStyle style, long now) {
        int accent = style.accent;
        int bright = HologramPainter.mixRgb(accent, WHITE, 0.4f);
        boolean hovered = b.action().id().equals(e.hoveredAction);
        boolean pressed = b.action().id().equals(e.pressedAction) && now - e.pressedNanos < 220_000_000L;
        boolean primary = b.action().primary();
        float arm = hovered ? HologramManager.armProgress(e, now) : 0;
        float fillTop = pressed ? 0.95f : primary ? 0.5f + 0.3f * arm : 0.1f + 0.3f * arm;
        p.nineSlice(HologramRenderTypes.BUTTON_FILL, false, b.x0(), b.y0(), b.x1(), b.y1(), 8, 2,
                HologramPainter.argb(fillTop, primary ? HologramPainter.mixRgb(accent, WHITE, 0.08f) : accent),
                HologramPainter.argb(fillTop * 0.62f, accent));
        if (hovered || pressed) {
            // Arming: the aimed button fills left to right; Use activates it once full.
            float ax1 = b.x0() + (b.x1() - b.x0()) * (pressed ? 1 : arm);
            p.quad(HologramRenderTypes.WHITE.additive(), b.x0() + 1, b.y1() - 2.2f, ax1 - 1, b.y1() - 1.2f,
                    0.25f, 0.25f, 0.75f, 0.75f, HologramPainter.argb(0.95f, bright), HologramPainter.argb(0.95f, bright));
            float glow = arm >= 1 || pressed ? 0.8f : 0.35f;
            p.nineSlice(HologramRenderTypes.BUTTON_FILL, true, b.x0(), b.y0(), b.x1(), b.y1(), 8, 2,
                    HologramPainter.argb(0.35f * glow, accent), HologramPainter.argb(0.2f * glow, accent));
            p.quad(HologramRenderTypes.LINE.additive(), b.x0() - 8, b.y1() - 2.5f, b.x1() + 8, b.y1() + 3.5f, 0, 0, 1, 1,
                    HologramPainter.argb(glow, accent), HologramPainter.argb(glow, accent));
        } else if (primary) {
            p.quad(HologramRenderTypes.LINE.additive(), b.x0() - 4, b.y1() - 2.5f, b.x1() + 4, b.y1() + 3.5f, 0, 0, 1, 1,
                    HologramPainter.argb(0.35f, accent), HologramPainter.argb(0.35f, accent));
        }
        int frame = hovered ? bright : primary ? HologramPainter.mixRgb(accent, WHITE, 0.2f) : accent;
        p.nineSlice(HologramRenderTypes.BUTTON_FRAME, false, b.x0(), b.y0(), b.x1(), b.y1(), 8, 2,
                HologramPainter.argb(hovered || primary ? 1 : 0.8f, frame), HologramPainter.argb(hovered || primary ? 1 : 0.8f, frame));
        float contentW = b.labelWidth();
        float lx = b.x0() + (b.x1() - b.x0() - contentW) / 2;
        float midY = (b.y0() + b.y1()) / 2;
        int labelColor = hovered || pressed || primary ? 0xFFFFFF : style.body;
        p.spacedText(font, b.label(), lx, midY - 8 * HologramLayout.BUTTON_LABEL_SCALE / 2 + 0.5f,
                HologramLayout.BUTTON_LABEL_SCALE, HologramLayout.BUTTON_SPACING, HologramPainter.argb(1, labelColor));
    }

    // ── Suspended ("paused") panel ────────────────────────────────────────────

    private static void paintGhost(HologramPainter p, HologramStack.Entry ghost, HologramLayout layout, Font font,
                                   Vec3 position, float yaw, float pitch, float turnDeg, float unit, long now) {
        float fadeIn = easeOut(clamp01((now - ghost.suspendedNanos) / 300e6f));
        float w = layout.width, h = layout.height;
        PoseStack pose = p.pose;
        pose.pushPose();
        orient(pose, position, yaw, pitch, unit, 0);
        if (turnDeg != 0) pose.mulPose(Axis.YP.rotationDegrees(turnDeg));
        pose.translate(-w / 2, -h / 2, 0);
        float base = p.alpha;
        p.alpha = base * GHOST_ALPHA * fadeIn;
        paintPanel(p, ghost, layout, font, ghost.spec().style(), w, h, 1, -1, now, true);
        // "PAUSED" chip under the panel.
        String label = "PAUSED";
        float lw = HologramLayout.spacedWidth(font, label, 0.8f, 1.2f);
        float cx = w / 2, y0 = h + 6;
        int accent = ghost.spec().style().accent;
        p.nineSlice(HologramRenderTypes.BUTTON_FILL, false, cx - lw / 2 - 12, y0, cx + lw / 2 + 12, y0 + 15, 8, 2,
                HologramPainter.argb(0.3f, accent), HologramPainter.argb(0.18f, accent));
        p.nineSlice(HologramRenderTypes.BUTTON_FRAME, false, cx - lw / 2 - 12, y0, cx + lw / 2 + 12, y0 + 15, 8, 2,
                HologramPainter.argb(0.9f, accent), HologramPainter.argb(0.9f, accent));
        p.spacedText(font, label, cx - lw / 2, y0 + 4.3f, 0.8f, 1.2f, HologramPainter.argb(1, ghost.spec().style().body));
        p.alpha = base;
        pose.popPose();
    }

    // ── Transform & aim ───────────────────────────────────────────────────────

    /**
     * Places a panel centred at {@code offset} (camera-relative world) facing back along the view
     * direction {@code yaw}/{@code pitch}; afterwards 1 unit = {@code unit} blocks, +x right, +y down.
     */
    private static void orient(PoseStack pose, Vec3 offset, float yaw, float pitch, float unit, float tiltDeg) {
        pose.translate(offset.x, offset.y, offset.z);
        pose.mulPose(new Quaternionf().rotationYXZ(Mth.PI - yaw * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD, 0));
        if (tiltDeg != 0) pose.mulPose(Axis.XP.rotationDegrees(tiltDeg));
        pose.scale(unit, -unit, unit);
    }

    /** Intersects the crosshair ray with the panel plane, in panel units. */
    private static void updateAim(HologramStack.Entry e, HologramLayout layout, float h, Matrix4f panelToWorld,
                                  Vector3fc forward, boolean allowed, long now) {
        String previous = e.hoveredAction;
        clearAim(e);
        if (!allowed) return;
        Matrix4f toPanel = new Matrix4f(panelToWorld).invert();
        Vector4f origin = toPanel.transform(new Vector4f(0, 0, 0, 1));
        Vector4f dir = toPanel.transform(new Vector4f(forward.x(), forward.y(), forward.z(), 0));
        if (Math.abs(dir.z) < 1e-6f) return;
        float t = -origin.z / dir.z;
        if (t <= 0) return;
        float x = origin.x + dir.x * t, y = origin.y + dir.y * t;
        e.aimX = x;
        e.aimY = y;
        if (x < 0 || x > layout.width || y < 0 || y > h) return;
        e.aimedAt = true;
        String aimed = null;
        for (HologramLayout.Button b : layout.buttons) {
            if (x >= b.x0() - AIM_TOLERANCE && x <= b.x1() + AIM_TOLERANCE
                    && y >= b.y0() - AIM_TOLERANCE && y <= b.y1() + AIM_TOLERANCE) {
                aimed = b.action().id();
                break;
            }
        }
        // A button only counts as aimed at once the player has deliberately moved the crosshair onto
        // it: not when the panel appeared (or changed) under a resting crosshair, and not while the
        // panel itself is travelling to follow the view.
        if (ANCHOR.following()) e.hoverSuppressed = true;
        else if (aimed == null) e.hoverSuppressed = false;
        e.hoveredAction = e.hoverSuppressed ? null : aimed;
        if (e.hoveredAction == null || !e.hoveredAction.equals(previous)) e.hoverStartNanos = now;
    }

    private static void clearAim(HologramStack.Entry e) {
        e.aimedAt = false;
        e.hoveredAction = null;
        e.aimX = e.aimY = Float.NaN;
    }

    /** Centre of a button on the displayed hologram's last drawn layout, in panel units. */
    static float @Nullable [] buttonCentre(String actionId) {
        HologramLayout layout = lastLayout;
        if (layout == null) return null;
        for (HologramLayout.Button b : layout.buttons) {
            if (b.action().id().equals(actionId)) return new float[] {(b.x0() + b.x1()) / 2, (b.y0() + b.y1()) / 2};
        }
        return null;
    }

    /** Voice hint under the buttons, replaced for a moment by listening / heard / rejected feedback. */
    private static void voiceLine(HologramPainter p, HologramStack.Entry e, HologramLayout layout, Font font,
                                  HologramStyle style, long now) {
        String text;
        int color;
        float alpha = 0.62f;
        long age = now - e.voiceNanos;
        HologramVoice.Status status = e.voiceStatus;
        if (status == HologramVoice.Status.LISTENING) {
            text = e.voiceText;
            color = style.highlight;
            alpha = 0.7f + 0.3f * Mth.sin(now / 1e9f * Mth.TWO_PI * 1.5f);
        } else if (status != null && age < HologramVoice.FEEDBACK_NANOS) {
            text = e.voiceText;
            color = status == HologramVoice.Status.ACCEPTED ? 0xFFFFFF : HologramStyle.ERROR.highlight;
            alpha = 1 - 0.6f * Math.max(0, (age - 1.4e9f) / 0.8e9f);
        } else {
            StringBuilder words = new StringBuilder();
            for (String phrase : HologramVoice.commands(e.spec()).keySet()) {
                if (!words.isEmpty()) words.append(" / ");
                words.append('“').append(HologramVoice.HINT.getOrDefault(phrase, phrase)).append('”');
            }
            text = "Hold " + ModKeybinds.VOICE_PUSH_TO_TALK.getTranslatedKeyMessage().getString() + " · say " + words;
            color = style.body;
        }
        float scale = HologramLayout.VOICE_SCALE;
        float iconSize = 7;
        float w = font.width(text) * scale + iconSize + 3;
        float x = (layout.width - w) / 2;
        float midY = layout.voiceY + 8 * scale / 2;
        p.icon(HologramIcon.MICROPHONE, x + iconSize / 2, midY, iconSize, HologramPainter.argb(alpha, color), 0);
        p.text(FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY), x + iconSize + 3,
                layout.voiceY, scale, HologramPainter.argb(alpha, color));
    }

    // ── Math ──────────────────────────────────────────────────────────────────

    private static float degrees(double blocksAtDistance) {
        return (float) Math.toDegrees(Math.atan(blocksAtDistance / HologramAnchor.DISTANCE));
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static float easeOut(float t) {
        float u = 1 - t;
        return 1 - u * u * u;
    }

    private static float easeIn(float t) {
        return t * t * t;
    }
}
