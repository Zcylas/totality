package zcylas.totality.client.hologram;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;
import zcylas.totality.entity.npc.TotalityNpcEntity;

/**
 * Mob HUD V1 prototype: ONE holographic nameplate, above the mob the player is looking at, showing
 * only its name. It is anchored to the entity (moves with it), billboarded toward the observer (readable
 * from the front, the flanks or behind), rendered in the world pass with world depth plus a faint x-ray
 * silhouette, and never shown for more than one mob: on a target change the old label finishes fading
 * out before the new one fades in. Looking away keeps it briefly, then it fades. Client-only
 * presentation; no gameplay state.
 *
 * <p>While {@link #PROTOTYPE_ENABLED}, {@code TotalityClient} registers this instead of the legacy
 * screen-space {@code MobHealthBarHud} (which is left intact for the future Mob API redesign).
 */
public final class TargetNameplate {

    /** The prototype replaces the legacy screen-space Mob HUD; set false to restore the legacy HUD. */
    public static final boolean PROTOTYPE_ENABLED = true;

    private static final double MAX_DISTANCE = 24;
    private static final long FADE_IN_NANOS = 160_000_000L;
    private static final long FADE_OUT_NANOS = 240_000_000L;
    private static final long SWITCH_OUT_NANOS = 110_000_000L;
    private static final long LINGER_NANOS = 700_000_000L;
    /** Blocks per plate unit at close range; the plate is 16 units tall (~0.35 blocks, ~1.7 wide for "Zombie"). */
    private static final float BASE_UNIT = 0.022f;
    private static final float PLATE_H = 16;
    private static final float MIN_PLATE_W = 64;
    private static final float ABOVE_HEAD = 0.55f;
    private static final float XRAY_ALPHA = 0.35f;
    private static final SubmitNodeStorage STORAGE = new SubmitNodeStorage();

    private static boolean enabled;
    private static @Nullable LivingEntity shown;
    private static @Nullable LivingEntity pending;
    private static float alpha;
    private static long lastSeenNanos;
    private static long lastFrameNanos;

    private TargetNameplate() {}

    public static void register() {
        enabled = true;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
                .register((handler, client) -> reset());
    }

    static void reset() {
        shown = null;
        pending = null;
        alpha = 0;
    }

    /**
     * The mob the player is looking at, up to {@link #MAX_DISTANCE} blocks, or null. Its own ray (the
     * vanilla crosshair pick only reaches the short entity-interaction range); terrain in between blocks it.
     */
    static @Nullable LivingEntity lookedAt(Minecraft mc, float partial) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return null;
        Vec3 eye = player.getEyePosition(partial);
        Vec3 look = player.getViewVector(partial);
        Vec3 end = eye.add(look.scale(MAX_DISTANCE));
        BlockHitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = block.getType() == HitResult.Type.MISS ? MAX_DISTANCE : eye.distanceTo(block.getLocation());
        AABB area = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(reach)), area,
                e -> e instanceof LivingEntity && !e.isSpectator() && e.isPickable(), reach * reach);
        Entity e = hit == null ? null : hit.getEntity();
        if (!(e instanceof LivingEntity living) || e instanceof Player || e instanceof ArmorStand) return null;
        if (!living.isAlive() || e == mc.getCameraEntity() || living.isInvisibleTo(player)) return null;
        return living;
    }

    /** Diagnostics (capture checks): the mob currently labelled, or null. */
    public static @Nullable LivingEntity shownTarget() {
        return alpha > 0.01f ? shown : null;
    }

    /**
     * True when Totality's plate replaces this entity's vanilla nametag: always for Totality NPCs (the plate is their
     * nameplate — shown when looked at, and no vanilla tag when not), and for any other entity while it carries the
     * plate (shown, fading, or next in line). Everything else — including all players, which never get the plate —
     * keeps the ordinary vanilla nametag. Rendering only: names, teams and scoreboards are untouched.
     * (Long-term direction: replace vanilla nameplates entirely, players included, once each category has a plate.)
     */
    public static boolean replacesVanillaName(Entity entity) {
        return enabled && entity != null && (entity instanceof TotalityNpcEntity || entity == shown || entity == pending);
    }

    /** Diagnostics: how many labels are drawn this frame (0 or 1 by construction). */
    public static int visibleCount() {
        return shownTarget() == null ? 0 : 1;
    }

    /** Per-frame target and fade update; sequential so two labels never share the screen. */
    static void update(Minecraft mc, long now, float partial) {
        float dt = lastFrameNanos == 0 ? 0 : Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        LivingEntity looked = lookedAt(mc, partial);
        if (looked != null) lastSeenNanos = now;
        if (shown != null && (!shown.isAlive() || shown.isRemoved())) pending = looked;
        if (looked != null) {
            if (shown == null) {
                shown = looked;
                alpha = 0;
            } else if (looked != shown) {
                pending = looked;
            } else {
                pending = null;
            }
        }
        float target;
        long rate;
        if (pending != null && shown != null) {
            target = 0;
            rate = SWITCH_OUT_NANOS;
        } else if (shown != null && now - lastSeenNanos < LINGER_NANOS) {
            target = 1;
            rate = FADE_IN_NANOS;
        } else {
            target = 0;
            rate = FADE_OUT_NANOS;
        }
        float step = dt * 1e9f / rate;
        alpha = target > alpha ? Math.min(target, alpha + step) : Math.max(target, alpha - step);
        if (alpha <= 0) {
            if (pending != null) {
                shown = pending;
                pending = null;
            } else if (target == 0) {
                shown = null;
            }
        }
    }

    /** Runs in the after-world pass (world projection and depth current). Render thread. */
    public static void render(GameRenderer gameRenderer) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
            reset();
            return;
        }
        CameraRenderState cam = gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (cam.isPanoramicMode) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        update(mc, System.nanoTime(), partial);
        LivingEntity target = shown;
        if (target == null || alpha <= 0.01f) return;

        Camera camera = gameRenderer.mainCamera();
        Font font = mc.font;
        Vec3 camPos = cam.pos;
        // no extra lift for a visible custom name: the vanilla nametag is suppressed while the plate is up
        Vec3 anchor = target.getPosition(partial).add(0, target.getBbHeight() + ABOVE_HEAD, 0);
        Vec3 offset = anchor.subtract(camPos);
        double distance = offset.length();
        // Physical size grows gently with distance so a far target stays legible (and shrinks less).
        float unit = BASE_UNIT * (float) Mth.clamp(1 + (distance - 8) * 0.04, 1, 1.8);

        String name = target.getName().getString();
        float textW = font.width(name);
        float w = Math.max(MIN_PLATE_W, textW + 34), h = PLATE_H;

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(cam.viewRotationMatrix);
        try {
            PoseStack pose = new PoseStack();
            HologramPainter p = new HologramPainter(STORAGE, pose, 0);
            pose.translate(offset.x, offset.y, offset.z);
            pose.mulPose(camera.rotation());                  // billboard: always faces the observer
            pose.scale(unit, -unit, unit);
            pose.translate(-w / 2, -h, 0);                    // bottom-centre sits on the anchor
            for (HologramRenderTypes.Mode mode : new HologramRenderTypes.Mode[] {HologramRenderTypes.Mode.WORLD,
                    HologramRenderTypes.Mode.XRAY}) {
                HologramRenderTypes.mode = mode;
                p.alpha = alpha * (mode == HologramRenderTypes.Mode.XRAY ? XRAY_ALPHA : 1);
                paint(p, font, name, textW, w, h, System.nanoTime());
            }
            gameRenderer.featureRenderDispatcher().renderAllFeatures(STORAGE);
        } finally {
            HologramRenderTypes.mode = HologramRenderTypes.Mode.OVERLAY;
            modelView.popMatrix();
        }
    }

    private static void paint(HologramPainter p, Font font, String name, float textW, float w, float h, long now) {
        HologramStyle style = HologramStyle.SYSTEM;
        int accent = style.accent;
        float pulse = 0.85f + 0.15f * Mth.sin(now / 1e9f * Mth.TWO_PI / 2.8f);
        p.threeSlice(HologramRenderTypes.NAMEPLATE_GLOW, true, -8, -8, w + 8, h + 8, 60, 15,
                HologramPainter.argb(0.55f * pulse, accent), HologramPainter.argb(0.55f * pulse, accent));
        p.threeSlice(HologramRenderTypes.NAMEPLATE_FILL, false, 0, 0, w, h, 28, 7,
                HologramPainter.argb(0.86f, HologramPainter.mixRgb(style.surface, accent, 0.14f)),
                HologramPainter.argb(0.8f, style.surface));
        p.threeSlice(HologramRenderTypes.NAMEPLATE_FRAME, false, 0, 0, w, h, 28, 7,
                HologramPainter.argb(1, HologramPainter.mixRgb(accent, 0xFFFFFF, 0.2f)), HologramPainter.argb(0.95f, accent));
        p.threeSlice(HologramRenderTypes.NAMEPLATE_FRAME, true, 0, 0, w, h, 28, 7,
                HologramPainter.argb(0.3f * pulse, accent), HologramPainter.argb(0.3f * pulse, accent));
        // Emblem on the top edge and the pointer down to the mob.
        p.sprite(HologramRenderTypes.ICON_HALO.additive(), w / 2 - 7, -7, w / 2 + 7, 7, HologramPainter.argb(0.5f, accent));
        p.icon(HologramIcon.DIAMOND, w / 2, 0, 7, HologramPainter.argb(1, style.highlight), 0);
        p.sprite(HologramRenderTypes.CHEVRON.translucent(), w / 2 - 4, h + 1.5f, w / 2 + 4, h + 6.5f,
                HologramPainter.argb(0.95f, style.highlight));
        p.text(FormattedCharSequence.forward(name, net.minecraft.network.chat.Style.EMPTY), (w - textW) / 2,
                (h - 8) / 2 + 0.5f, 1, HologramPainter.argb(1, style.title));
    }
}
