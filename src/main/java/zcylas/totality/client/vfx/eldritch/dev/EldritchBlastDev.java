package zcylas.totality.client.vfx.eldritch.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.magic.spell.SpellRegistry;
import zcylas.totality.api.magic.spell.destruction.EldritchBlast;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.client.vfx.eldritch.EldritchBlastVfx;
import zcylas.totality.client.vfx.eldritch.EldritchBlastVfx.Palette;
import zcylas.totality.client.vfx.eldritch.EldritchBlastVfx.SoundVariant;
import zcylas.totality.client.vfx.glow.EmissiveGlow;
import zcylas.totality.client.vfx.screen.ScreenFx;

import java.util.List;
import java.util.Locale;

/**
 * Development-only Eldritch Blast V2 tooling (registered only in a Fabric development environment):
 * {@code /totalityvfx eldritch} status; {@code palette violet|teal}; {@code sound custom|reference} (A/B of the original
 * and the private reference sounds); {@code beams <0-4>} beams per cast in single player (preview of the future
 * Warlock-level scaling; 0 = normal); {@code emissive on|off}; {@code timing on|off}. Also capture scene 70.
 */
public final class EldritchBlastDev {

    private EldritchBlastDev() {}

    public static void registerIfDevelopmentEnvironment() {
        if (!VerificationReporter.isDevEnvironment()) return;
        if (HologramCapture.requested()) HologramCapture.addScene(70, EldritchBlastCapture.scenes());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("totalityvfx").then(ClientCommands.literal("eldritch").executes(EldritchBlastDev::status)
                        .then(ClientCommands.literal("palette")
                                .then(ClientCommands.literal("violet").executes(ctx -> palette(ctx, Palette.VIOLET)))
                                .then(ClientCommands.literal("teal").executes(ctx -> palette(ctx, Palette.TEAL))))
                        .then(ClientCommands.literal("sound")
                                .then(ClientCommands.literal("custom").executes(ctx -> sound(ctx, SoundVariant.CUSTOM)))
                                .then(ClientCommands.literal("reference").executes(ctx -> sound(ctx, SoundVariant.REFERENCE))))
                        .then(ClientCommands.literal("beams").then(ClientCommands.argument("count", IntegerArgumentType.integer(0, 4))
                                .executes(ctx -> {
                                    // Single player only: the integrated server runs in this JVM.
                                    EldritchBlast.setDevBeamOverride(IntegerArgumentType.getInteger(ctx, "count"));
                                    return status(ctx);
                                })))
                        .then(ClientCommands.literal("emissive")
                                .then(ClientCommands.literal("on").executes(ctx -> {
                                    EldritchBlastVfx.setEmissiveEnabled(true);
                                    return status(ctx);
                                }))
                                .then(ClientCommands.literal("off").executes(ctx -> {
                                    EldritchBlastVfx.setEmissiveEnabled(false);
                                    return status(ctx);
                                })))
                        .then(ClientCommands.literal("timing")
                                .then(ClientCommands.literal("on").executes(ctx -> {
                                    EldritchBlastVfx.setTiming(true);
                                    return status(ctx);
                                }))
                                .then(ClientCommands.literal("off").executes(ctx -> {
                                    EldritchBlastVfx.setTiming(false);
                                    return status(ctx);
                                }))))));
    }

    private static int palette(CommandContext<FabricClientCommandSource> ctx, Palette p) {
        EldritchBlastVfx.setPalette(p);
        return status(ctx);
    }

    private static int sound(CommandContext<FabricClientCommandSource> ctx, SoundVariant v) {
        EldritchBlastVfx.setSoundVariant(v);
        return status(ctx);
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Component.literal(stats()));
        return 1;
    }

    static String stats() {
        return String.format(Locale.ROOT, "Eldritch Blast V2: palette=%s sound=%s beams/cast=%d tracked=%d impacts=%d quads=%d (peak %d) "
                        + "gpu=%.3f ms cpu=%.3f ms (%d samples)",
                EldritchBlastVfx.palette(), EldritchBlastVfx.soundVariant(), EldritchBlast.beamCount(), EldritchBlastVfx.trackedBeams(),
                EldritchBlastVfx.lastImpacts(), EldritchBlastVfx.lastQuads(), EldritchBlastVfx.peakQuads(),
                EldritchBlastVfx.averageGpuMillis(), EldritchBlastVfx.averageCpuMillis(), EldritchBlastVfx.gpuSamples());
    }

    static void captureStarted() {
        EldritchBlastVfx.setTiming(true);
        ScreenFx.settings().setShake(1.0f);
    }

    /** Per-frame capture log: what is drawn, the glow budget, Screen FX, sounds and the cast/impact events. */
    static String frameState() {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "beams=%d impacts=%d quads=%d tracked=%d glowDemand=%.2f glowScale=%.2f shake=%.3f castSounds=%d impactSounds=%d",
                EldritchBlastVfx.lastBeams(), EldritchBlastVfx.lastImpacts(), EldritchBlastVfx.lastQuads(), EldritchBlastVfx.trackedBeams(),
                EmissiveGlow.budget().groupDemand(EldritchBlastVfx.GLOW_GROUP), EmissiveGlow.budget().groupScale(EldritchBlastVfx.GLOW_GROUP),
                ScreenFx.lastFrame().shake(), EldritchBlastVfx.castSounds(), EldritchBlastVfx.impactSounds()));
        for (String e : EldritchBlastVfx.drainEvents()) sb.append(" | ").append(e);
        return sb.toString();
    }

    private static Step beams(int n) {
        return HologramCapture.run("beams per cast " + n, () -> EldritchBlast.setDevBeamOverride(n));
    }

    private static Step palette(Palette p) {
        return HologramCapture.run("palette " + p, () -> EldritchBlastVfx.setPalette(p));
    }

    /** The V2-only sequences of scene 70 (multi-beam, palettes, emissive off, sound A/B events, stress, cleanup). */
    static void v2Scenes(List<Step> s, double[] stage, double[] chest) {
        if ("v1".equals(System.getProperty("totality.eldritch.label"))) return;
        int[] sounds = new int[2];
        // One cast = one cast sound and one impact sound, and everything is gone afterwards.
        s.add(HologramCapture.run("count sounds", () -> {
            sounds[0] = EldritchBlastVfx.castSounds();
            sounds[1] = EldritchBlastVfx.impactSounds();
        }));
        EldritchBlastCapture.spell(s, "count_single", EldritchBlastCapture.cam(stage, -8.5, 2.3, 7.0, 0, 1.4, 7.0),
                EldritchBlastCapture.warlockCasts(stage, chest), 6);
        s.add(HologramCapture.waitTicks(40));
        s.add(HologramCapture.check("one cast played exactly one cast sound and one impact sound",
                () -> EldritchBlastVfx.castSounds() - sounds[0] == 1 && EldritchBlastVfx.impactSounds() - sounds[1] == 1));
        s.add(HologramCapture.check("the beam, its impact and its residue are cleaned up",
                () -> EldritchBlastVfx.trackedBeams() == 0 && EldritchBlastVfx.lastImpacts() == 0));

        // Multi-beam preview: 2, 3 and 4 beams (dev override), side and behind.
        for (int n = 2; n <= 4; n++) {
            int beams = n;
            s.add(beams(n));
            s.add(HologramCapture.run("count sounds", () -> sounds[0] = EldritchBlastVfx.castSounds()));
            EldritchBlastCapture.spell(s, "multi" + n + "_side", EldritchBlastCapture.cam(stage, -8.5, 2.3, 7.0, 0, 1.4, 7.0),
                    EldritchBlastCapture.warlockCasts(stage, chest), 18 + 4 * n);
            s.add(HologramCapture.check(beams + " beams per cast fired " + beams + " beams",
                    () -> EldritchBlastVfx.castSounds() - sounds[0] == beams));
        }
        EldritchBlastCapture.spell(s, "multi4_behind", EldritchBlastCapture.cam(stage, 1.7, 2.7, -4.0, 0, 1.4, 10.0),
                EldritchBlastCapture.warlockCasts(stage, chest), 34);
        EldritchBlastCapture.spell(s, "multi4_target_close", EldritchBlastCapture.cam(stage, -3.6, 1.9, 10.8, 0, 1.3, 14.0),
                EldritchBlastCapture.warlockCasts(stage, chest), 34);
        s.add(beams(0));

        // The optional violet palette (development) from the same cameras.
        s.add(palette(Palette.VIOLET));
        EldritchBlastCapture.spell(s, "violet_side", EldritchBlastCapture.cam(stage, -8.5, 2.3, 7.0, 0, 1.4, 7.0),
                EldritchBlastCapture.warlockCasts(stage, chest), 24);
        EldritchBlastCapture.spell(s, "violet_target_close", EldritchBlastCapture.cam(stage, -3.6, 1.9, 10.8, 0, 1.3, 14.0),
                EldritchBlastCapture.warlockCasts(stage, chest), 24);
        s.add(palette(Palette.TEAL));

        // Emissive Rendering Layer off (same shot as "side").
        s.add(HologramCapture.run("emissive off", () -> EldritchBlastVfx.setEmissiveEnabled(false)));
        EldritchBlastCapture.spell(s, "side_no_emissive", EldritchBlastCapture.cam(stage, -8.5, 2.3, 7.0, 0, 1.4, 7.0),
                EldritchBlastCapture.warlockCasts(stage, chest), 24);
        s.add(HologramCapture.run("emissive on", () -> EldritchBlastVfx.setEmissiveEnabled(true)));

        // Reference sound variant: same events, other sound events (audible only in a "sound" run).
        s.add(HologramCapture.run("sound reference", () -> EldritchBlastVfx.setSoundVariant(SoundVariant.REFERENCE)));
        EldritchBlastCapture.spell(s, "sound_reference", EldritchBlastCapture.cam(stage, -8.5, 2.3, 7.0, 0, 1.4, 7.0),
                EldritchBlastCapture.warlockCasts(stage, chest), 12);
        s.add(HologramCapture.run("sound custom", () -> EldritchBlastVfx.setSoundVariant(SoundVariant.CUSTOM)));

        // Measurements: one beam, then 20 concurrent beams (fanned across the range), GPU/CPU of the draw.
        measure(s, stage, "single", 1);
        measure(s, stage, "twenty", 20);
        s.add(HologramCapture.waitTicks(60));
        s.add(HologramCapture.check("after the stress test every beam and impact is cleaned up",
                () -> EldritchBlastVfx.trackedBeams() == 0 && EldritchBlastVfx.lastImpacts() == 0));
    }

    /**
     * Real time (tick rate 20) for audio recording: three single casts with the original sounds, three with the private
     * reference sounds, then a four-beam cast; every event is logged with its wall-clock time to align with the recording.
     */
    static void audioScenes(List<Step> s, double[] stage, double[] chest) {
        s.add(EldritchBlastCapture.cam(stage, -8.5, 2.3, 7.0, 0, 1.4, 7.0));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.run("audio start", () -> HologramCapture.log("audio start wall=" + System.currentTimeMillis())));
        for (SoundVariant v : SoundVariant.values()) {
            s.add(HologramCapture.run("sound " + v, () -> EldritchBlastVfx.setSoundVariant(v)));
            for (int i = 0; i < 3; i++) {
                s.add(EldritchBlastCapture.warlockCasts(stage, chest));
                for (int k = 0; k < 40; k++) s.add(mc -> {
                    HologramCapture.log("audio frame " + frameState());
                    return true;
                });
            }
        }
        s.add(HologramCapture.run("sound CUSTOM, 4 beams", () -> {
            EldritchBlastVfx.setSoundVariant(SoundVariant.CUSTOM);
            EldritchBlast.setDevBeamOverride(4);
        }));
        s.add(EldritchBlastCapture.warlockCasts(stage, chest));
        for (int k = 0; k < 50; k++) s.add(mc -> {
            HologramCapture.log("audio frame " + frameState());
            return true;
        });
        s.add(beams(0));
        s.add(HologramCapture.run("audio end", () -> HologramCapture.log("audio end wall=" + System.currentTimeMillis())));
    }

    private static void measure(List<Step> s, double[] stage, String name, int count) {
        s.add(EldritchBlastCapture.cam(stage, -9.0, 4.0, 2.0, 0, 1.2, 12.0));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.run("reset measurements", EldritchBlastVfx::resetMeasurements));
        // All casts in the same server tick, so the beams are alive together.
        s.add(EldritchBlastCapture.onServer(server -> {
            for (int i = 0; i < count; i++) {
                double x = count == 1 ? 0 : -9.0 + 18.0 * i / (count - 1);
                EldritchBlastCapture.aimWarlock(stage, new double[]{x, 1.0 + (i % 3) * 0.6, 22});
                SpellRegistry.ELDRITCH_BLAST.onActivate(EldritchBlastCapture.warlock(), null);
            }
        }));
        s.add(HologramCapture.waitTicks(16));
        s.add(HologramCapture.run("perf " + name, () -> HologramCapture.log("perf " + name + ": " + stats())));
        frames(s, "perf_" + name, 2);
    }

    private static void frames(List<Step> s, String name, int n) {
        EldritchBlastCapture.frames(s, name, n);
    }
}
