package zcylas.totality.client.vfx.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import zcylas.totality.Totality;
import zcylas.totality.client.camera.CameraSession;
import zcylas.totality.client.vfx.screen.dev.ScreenFxDev;

import java.io.IOException;

/**
 * Shared Screen FX Service V1 (VFX Experiment 3, phase B1): reusable, client-only camera shake and screen flash for
 * any effect. Effects {@link #request} contributions; once per frame they are merged by {@link ScreenFxMixer}
 * (diminishing merge, priorities, hard caps, anti-strobe flash budget) and scaled by the accessibility settings.
 *
 * <ul>
 *   <li>Clock: client game time plus the partial tick, in seconds (time-based, follows {@code /tick rate}, frozen
 *       while paused); nothing is shown while the game is paused.</li>
 *   <li>Shake: a small rotation of the rendered view, added at the end of {@code GameRenderer.bobHurt} (world and
 *       hand projection only; the player's aim, the server and the crosshair target are untouched). At most
 *       {@link #MAX_SHAKE_DEGREES}.</li>
 *   <li>Flash: a full-screen tint drawn first in the HUD (under the hotbar and chat), at most {@link #MAX_FLASH_ALPHA}.</li>
 *   <li>Suppressed entirely while Totality's Camera is open, so the viewfinder and photographs never shake or flash.</li>
 *   <li>Cosmetic only: it never touches world time, weather or any gameplay state.</li>
 * </ul>
 */
public final class ScreenFx {

    public static final float MAX_SHAKE_DEGREES = 0.8f;
    public static final float MAX_FLASH_ALPHA = 0.35f;
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "screen_fx_flash");

    private static final ScreenFxMixer MIXER = new ScreenFxMixer();
    private static ScreenFxSettings settings = new ScreenFxSettings(
            FabricLoader.getInstance().getConfigDir().resolve("totality-screenfx.properties"));
    private static long rejected;

    private ScreenFx() {}

    public static void register() {
        settings = ScreenFxSettings.load(FabricLoader.getInstance().getConfigDir().resolve("totality-screenfx.properties"));
        HudElementRegistry.addFirst(HUD_ID, (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gui.hud.isHidden()) return;
            ScreenFxFrame f = frame();
            if (f.flash() <= 0.002) return;
            int alpha = Math.round((float) f.flash() * MAX_FLASH_ALPHA * 255.0f);
            graphics.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                    (alpha << 24) | (f.flashColour() & 0xFFFFFF));
        });
        // Leaving a world drops every request (requests are tied to the level clock).
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null && MIXER.liveRequests() > 0) MIXER.clear();
        });
        ScreenFxDev.registerIfDevelopmentEnvironment();
    }

    /** Adds a request (client/render thread); returns its handle, or 0 when it was rejected (no world, or disabled). */
    public static long request(ScreenFxRequest request) {
        double now = now();
        if (Double.isNaN(now) || request.channel() == ScreenFxChannel.IMPACT_FRAME && !settings.impactFrames()) {
            rejected++;
            return 0;
        }
        return MIXER.add(request, now);
    }

    public static boolean cancel(long handle) {
        return MIXER.cancel(handle);
    }

    public static int cancelOwner(Object owner) {
        return MIXER.cancelOwner(owner);
    }

    public static void clear() {
        MIXER.clear();
    }

    public static ScreenFxSettings settings() {
        return settings;
    }

    public static void saveSettings() {
        try {
            settings.save();
        } catch (IOException e) {
            Totality.LOGGER.warn("[Totality VFX] could not save screen FX settings: {}", e.toString());
        }
    }

    /** Seconds on the client game clock, or NaN without a world. */
    static double now() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Double.NaN;
        return (mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20.0;
    }

    /** True while screen effects must not show (paused, no world, Totality's Camera open). */
    static boolean suppressed() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mc.player == null || mc.isPaused() || CameraSession.isActive();
    }

    /** This frame's resolved output (resolved once per frame time; NONE while suppressed). */
    public static ScreenFxFrame frame() {
        if (suppressed()) return ScreenFxFrame.NONE;
        Minecraft mc = Minecraft.getInstance();
        CameraRenderState camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        Vector3f l = camera.orientation.transform(new Vector3f(0.0f, 0.0f, -1.0f));
        double shakeScale = settings.shakeScale(mc.options.screenEffectScale().get());
        double flashScale = settings.flashScale(mc.options.hideLightningFlash().get());
        return MIXER.resolve(now(), camera.pos, new Vec3(l.x(), l.y(), l.z()), mc.player.getUUID(), shakeScale, flashScale);
    }

    /** Called from the {@code GameRenderer.bobHurt} mixin: adds this frame's shake rotation to the view bob pose. */
    public static void applyShake(PoseStack poseStack) {
        ScreenFxFrame f = frame();
        if (f.shake() <= 1.0e-4) return;
        double t = now();
        float amp = (float) f.shake() * MAX_SHAKE_DEGREES;
        // Smooth, deterministic multi-frequency signal (13-17 Hz), different per axis.
        float pitch = amp * (float) (0.6 * Math.sin(t * 82.3) + 0.4 * Math.sin(t * 107.9 + 1.7));
        float yaw = amp * (float) (0.6 * Math.sin(t * 95.1 + 0.6) + 0.4 * Math.sin(t * 69.7 + 2.9));
        float roll = 0.5f * amp * (float) Math.sin(t * 76.4 + 4.1);
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
    }

    // ── Diagnostics (dev command, capture) ─────────────────────────────────────

    public static ScreenFxFrame lastFrame() {
        return MIXER.last();
    }

    public static int liveRequests() {
        return MIXER.liveRequests();
    }

    public static long requestedTotal() {
        return MIXER.requestedTotal();
    }

    public static long rejectedTotal() {
        return rejected;
    }

    public static int flashOnsets() {
        return MIXER.flashOnsets();
    }

    public static double flashTokens() {
        return MIXER.flashTokens();
    }
}
