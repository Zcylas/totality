package zcylas.totality.client.vfx.explosion.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.particle.fireball.FireballDetonationParticle;
import zcylas.totality.client.particle.fireball.FireballV2;
import zcylas.totality.client.vfx.explosion.FireExplosion;
import zcylas.totality.client.vfx.explosion.FireExplosionRenderer;
import zcylas.totality.client.vfx.glow.EmissiveGlow;
import zcylas.totality.client.vfx.projectile.FireballProjectileVfx;
import zcylas.totality.entity.magic.FireballParticleBudget;
import zcylas.totality.entity.magic.FireballProjectileEntity;
import zcylas.totality.entity.magic.FireballVfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Development-only Fireball V2 explosion tooling (registered only in a Fabric development environment):
 * {@code /totalityvfx fireball} status; {@code legacy on|off} (the V1 projectile and detonation for A/B); {@code emissive on|off};
 * {@code test <n>} client-only explosions on the ground ahead (no damage, nothing sent to the server);
 * {@code loop <n>} keeps n client-only explosions restarting (measurement; 0 stops); {@code timing on|off};
 * {@code marker on|off} outlines the 6-block gameplay radius around every live explosion (phase B2.1 scale check).
 * Also registers capture scene 69 ({@link FireballV2Capture}).
 */
public final class FireballV2Dev {

    private static final List<Vec3> LOOP = new ArrayList<>();
    private static final List<FireExplosion> LOOP_LIVE = new ArrayList<>();
    private static final int MARKER_COLOUR = 0xFF40E0FF;
    private static final int MARKER_EQUATOR_COLOUR = 0xFF60FF60;
    private static boolean marker;

    private FireballV2Dev() {}

    public static void registerIfDevelopmentEnvironment() {
        if (!VerificationReporter.isDevEnvironment()) return;
        if (HologramCapture.requested()) HologramCapture.addScene(69, FireballV2Capture.scenes());
        ClientTickEvents.END_CLIENT_TICK.register(client -> drawMarkers());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("totalityvfx").then(ClientCommands.literal("fireball").executes(FireballV2Dev::status)
                        .then(ClientCommands.literal("legacy")
                                .then(ClientCommands.literal("on").executes(ctx -> legacy(ctx, true)))
                                .then(ClientCommands.literal("off").executes(ctx -> legacy(ctx, false))))
                        .then(ClientCommands.literal("emissive")
                                .then(ClientCommands.literal("on").executes(ctx -> emissive(ctx, true)))
                                .then(ClientCommands.literal("off").executes(ctx -> emissive(ctx, false))))
                        .then(ClientCommands.literal("test").then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 32))
                                .executes(ctx -> {
                                    for (Vec3 p : gridAhead(IntegerArgumentType.getInteger(ctx, "count"))) spawn(p);
                                    return status(ctx);
                                })))
                        .then(ClientCommands.literal("loop").then(ClientCommands.argument("count", IntegerArgumentType.integer(0, 32))
                                .executes(ctx -> {
                                    loop(IntegerArgumentType.getInteger(ctx, "count"));
                                    return status(ctx);
                                })))
                        .then(ClientCommands.literal("marker")
                                .then(ClientCommands.literal("on").executes(ctx -> {
                                    setMarker(true);
                                    return status(ctx);
                                }))
                                .then(ClientCommands.literal("off").executes(ctx -> {
                                    setMarker(false);
                                    return status(ctx);
                                })))
                        .then(ClientCommands.literal("timing")
                                .then(ClientCommands.literal("on").executes(ctx -> {
                                    setTiming(true);
                                    return status(ctx);
                                }))
                                .then(ClientCommands.literal("off").executes(ctx -> {
                                    setTiming(false);
                                    return status(ctx);
                                }))))));
    }

    /** {@code count} ground points ahead of the player (rows of five, 4 blocks apart, from 14 blocks out). */
    public static List<Vec3> gridAhead(int count) {
        LocalPlayer p = Minecraft.getInstance().player;
        Vec3 look = p.getLookAngle();
        Vec3 fwd = new Vec3(look.x, 0, look.z).normalize();
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int row = i / 5, col = i % 5;
            Vec3 at = p.position().add(fwd.scale(14 + row * 5)).add(side.scale((col - 2) * 5.0));
            out.add(new Vec3(at.x, Math.floor(at.y) + 0.25, at.z));
        }
        return out;
    }

    /** A client-only explosion with Fireball V2's look (no damage, no packets, no screen effects). */
    public static FireExplosion spawn(Vec3 at) {
        return FireExplosionRenderer.spawn(Minecraft.getInstance().level, at, new Vec3(0, 1, 0),
                FireballProjectileEntity.BLAST_RADIUS, FireballV2.STYLE);
    }

    /** Keeps {@code count} client-only explosions alive, each restarting when it ends (0 stops the loop). */
    public static void loop(int count) {
        LOOP.clear();
        LOOP_LIVE.clear();
        if (count > 0) LOOP.addAll(gridAhead(count));
    }

    /** Called by the renderer each frame: restarts finished loop explosions. */
    public static void tickLoop(double now) {
        if (LOOP.isEmpty() || Double.isNaN(now)) return;
        for (int i = 0; i < LOOP.size(); i++) {
            if (i >= LOOP_LIVE.size()) {
                LOOP_LIVE.add(spawn(LOOP.get(i)));
            } else if (LOOP_LIVE.get(i).finished(now)) {
                LOOP_LIVE.set(i, spawn(LOOP.get(i)));
            }
        }
    }

    /** Development only (the dev command and capture): shows the gameplay-radius outline around live explosions. */
    public static void setMarker(boolean on) {
        marker = on;
    }

    /**
     * Per-tick gizmos around every live explosion: the outline of its damage sphere as seen from the camera (the exact
     * silhouette circle, not a great circle) and its horizontal equator. Never drawn unless enabled
     * by the development command or capture; this class is only registered in a development environment.
     */
    private static void drawMarkers() {
        Minecraft mc = Minecraft.getInstance();
        if (!marker || mc.level == null) return;
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        for (FireExplosion e : FireExplosionRenderer.active()) {
            Vec3 c = e.centre();
            double r = e.radius();
            circle(c, new Vec3(1, 0, 0), new Vec3(0, 0, 1), r, MARKER_EQUATOR_COLOUR);
            Vec3 toCam = cam.subtract(c);
            double d = toCam.length();
            if (d > r) {
                Vec3 n = toCam.scale(1.0 / d);
                Vec3 u = n.cross(Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
                circle(c.add(n.scale(r * r / d)), u, n.cross(u), r * Math.sqrt(1.0 - r * r / (d * d)), MARKER_COLOUR);
            }
        }
    }

    private static void circle(Vec3 centre, Vec3 u, Vec3 v, double radius, int colour) {
        int n = 72;
        Vec3 prev = centre.add(u.scale(radius));
        for (int i = 1; i <= n; i++) {
            double a = i * Math.PI * 2 / n;
            Vec3 p = centre.add(u.scale(radius * Math.cos(a))).add(v.scale(radius * Math.sin(a)));
            Gizmos.line(prev, p, colour, 2.5f);
            prev = p;
        }
    }

    private static int legacy(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        setLegacy(on);
        return status(ctx);
    }

    /** Development only: the whole V1 Fireball look (projectile renderer, cast burst, trail and detonation) for A/B. */
    public static void setLegacy(boolean on) {
        FireballDetonationParticle.setLegacyExplosion(on);
        FireballVfx.setLegacyProjectile(on);
    }

    /** GPU timers of the explosion, the projectile and the shared emissive layer. */
    public static void setTiming(boolean on) {
        FireExplosionRenderer.setTiming(on);
        FireballProjectileVfx.setTiming(on);
        EmissiveGlow.setTiming(on);
    }

    public static void resetMeasurements() {
        FireExplosionRenderer.resetMeasurements();
        FireballProjectileVfx.resetMeasurements();
    }

    private static int emissive(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        FireExplosionRenderer.setEmissiveEnabled(on);
        return status(ctx);
    }

    static String stats() {
        return String.format(Locale.ROOT,
                "%d explosion(s), %d element(s) (peak %d), %d draw(s), vertex buffers %d KiB, GPU %s, CPU %s, particles %s; "
                        + "projectiles %d (peak %d), %d quads, %d draw(s), projectile GPU %s, CPU %s, vb %d KiB; "
                        + "fireball particles alive %d; glow demand %.2f, group scale %.2f, glow quads %d",
                FireExplosionRenderer.lastExplosions(), FireExplosionRenderer.lastElements(), FireExplosionRenderer.peakElements(),
                FireExplosionRenderer.lastDraws(), FireExplosionRenderer.vertexBufferBytes() / 1024,
                ms(FireExplosionRenderer.averageGpuMillis()), ms(FireExplosionRenderer.averageCpuMillis()),
                Minecraft.getInstance().particleEngine.countParticles(),
                FireballProjectileVfx.lastProjectiles(), FireballProjectileVfx.peakProjectiles(), FireballProjectileVfx.lastQuads(),
                FireballProjectileVfx.lastDraws(), ms(FireballProjectileVfx.averageGpuMillis()), ms(FireballProjectileVfx.averageCpuMillis()),
                FireballProjectileVfx.vertexBufferBytes() / 1024, FireballParticleBudget.alive(),
                EmissiveGlow.budget().groupDemand(FireballV2.GLOW_GROUP), EmissiveGlow.budget().groupScale(FireballV2.GLOW_GROUP),
                EmissiveGlow.lastQuadCount());
    }

    private static String ms(double v) {
        return v < 0 ? "n/a" : String.format(Locale.ROOT, "%.3f ms", v);
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Component.literal("[Fireball V2] " + (FireballDetonationParticle.legacyExplosion() ? "V1 (legacy)" : "V2")
                + " detonation, emissive " + (FireExplosionRenderer.emissiveEnabled() ? "on" : "off") + ", radius marker "
                + (marker ? "on" : "off") + "; " + stats()));
        return 1;
    }
}
