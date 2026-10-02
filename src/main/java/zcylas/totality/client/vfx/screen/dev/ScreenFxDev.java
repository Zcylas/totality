package zcylas.totality.client.vfx.screen.dev;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.client.vfx.screen.ScreenFx;
import zcylas.totality.client.vfx.screen.ScreenFxChannel;
import zcylas.totality.client.vfx.screen.ScreenFxEnvelope;
import zcylas.totality.client.vfx.screen.ScreenFxFalloff;
import zcylas.totality.client.vfx.screen.ScreenFxFrame;
import zcylas.totality.client.vfx.screen.ScreenFxMixer;
import zcylas.totality.client.vfx.screen.ScreenFxPriority;
import zcylas.totality.client.vfx.screen.ScreenFxRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Development-only Shared Screen FX tooling (registered only in a Fabric development environment):
 * {@code /totalityvfx screenfx} status, {@code test single|twenty|priorities|strobe|clear}, {@code shake|flash <0-1>};
 * and capture scene 68, which drives the real service through every B1 case and logs the resolved output each tick.
 */
public final class ScreenFxDev {

    /** A Fireball-sized test shake and flash (the values Fireball V2 uses). */
    static final ScreenFxEnvelope SHAKE_ENV = new ScreenFxEnvelope(0.02, 0.06, 0.40);
    static final ScreenFxFalloff SHAKE_FALLOFF = new ScreenFxFalloff(6.0, 40.0);
    static final ScreenFxEnvelope FLASH_ENV = new ScreenFxEnvelope(0.0, 0.03, 0.15);
    static final ScreenFxFalloff FLASH_FALLOFF = new ScreenFxFalloff(8.0, 48.0);
    private static final int WARM = 0xFFD9A0;

    private ScreenFxDev() {}

    public static void registerIfDevelopmentEnvironment() {
        if (!VerificationReporter.isDevEnvironment()) return;
        if (HologramCapture.requested()) HologramCapture.addScene(68, scenes());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("totalityvfx").then(ClientCommands.literal("screenfx").executes(ScreenFxDev::status)
                        .then(ClientCommands.literal("test")
                                .then(ClientCommands.literal("single").executes(ctx -> test(ctx, 1)))
                                .then(ClientCommands.literal("twenty").executes(ctx -> test(ctx, 20)))
                                .then(ClientCommands.literal("priorities").executes(ctx -> {
                                    priorities(new Object());
                                    return status(ctx);
                                }))
                                .then(ClientCommands.literal("strobe").executes(ctx -> {
                                    flash(ahead(4.0), 0.8f);
                                    return status(ctx);
                                }))
                                .then(ClientCommands.literal("clear").executes(ctx -> {
                                    ScreenFx.clear();
                                    return status(ctx);
                                })))
                        .then(ClientCommands.literal("shake").then(ClientCommands.argument("value", FloatArgumentType.floatArg(0, 1))
                                .executes(ctx -> {
                                    ScreenFx.settings().setShake(FloatArgumentType.getFloat(ctx, "value"));
                                    ScreenFx.saveSettings();
                                    return status(ctx);
                                })))
                        .then(ClientCommands.literal("flash").then(ClientCommands.argument("value", FloatArgumentType.floatArg(0, 1))
                                .executes(ctx -> {
                                    ScreenFx.settings().setFlash(FloatArgumentType.getFloat(ctx, "value"));
                                    ScreenFx.saveSettings();
                                    return status(ctx);
                                }))))));
    }

    private static int test(CommandContext<FabricClientCommandSource> ctx, int count) {
        for (int i = 0; i < count; i++) {
            Vec3 at = ahead(4.0 + i * 0.5);
            shake(at, 0.45f);
            flash(at, 0.5f);
        }
        return status(ctx);
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        ScreenFxFrame f = ScreenFx.lastFrame();
        ctx.getSource().sendFeedback(Component.literal(String.format(Locale.ROOT,
                "[Screen FX] %d live, shake %.3f (strongest %.3f), flash %.3f (demand %.3f), settings shake %.2f flash %.2f",
                ScreenFx.liveRequests(), f.shake(), f.strongestShake(), f.flash(), f.flashDemand(),
                ScreenFx.settings().shake(), ScreenFx.settings().flash())));
        return 1;
    }

    static Vec3 ahead(double blocks) {
        var p = Minecraft.getInstance().player;
        return p.getEyePosition().add(p.getLookAngle().scale(blocks));
    }

    static long shake(Vec3 at, float intensity) {
        return ScreenFx.request(ScreenFxRequest.at(ScreenFxChannel.SHAKE, at, intensity, SHAKE_ENV, SHAKE_FALLOFF, 0, null));
    }

    static long flash(Vec3 at, float intensity) {
        return ScreenFx.request(ScreenFxRequest.at(ScreenFxChannel.FLASH, at, intensity, FLASH_ENV, FLASH_FALLOFF, WARM, null));
    }

    /** A long NORMAL shake 0.5 plus a CINEMATIC shake 0.2 owned by {@code owner}. */
    static void priorities(Object owner) {
        Vec3 at = ahead(3.0);
        ScreenFx.request(ScreenFxRequest.at(ScreenFxChannel.SHAKE, at, 0.5f, new ScreenFxEnvelope(0, 2.0, 0.2), SHAKE_FALLOFF, 0, null));
        ScreenFx.request(ScreenFxRequest.at(ScreenFxChannel.SHAKE, at, 0.2f, new ScreenFxEnvelope(0, 1.0, 0.1), SHAKE_FALLOFF, 0, owner)
                .withPriority(ScreenFxPriority.CINEMATIC));
    }

    // ── Capture scene 68 ──────────────────────────────────────────────────────

    /** Peak tracker for one capture case. */
    private static final class Peak {
        double shake, flash, strongestShake, strongestFlash, energy;
        int samples;

        void reset() {
            shake = flash = strongestShake = strongestFlash = energy = 0;
            samples = 0;
        }
    }

    private static Step watch(String label, Peak peak, int ticks, List<Step> s) {
        for (int i = 0; i < ticks; i++) {
            int tick = i;
            s.add(mc -> {
                ScreenFxFrame f = ScreenFx.lastFrame();
                peak.shake = Math.max(peak.shake, f.shake());
                peak.flash = Math.max(peak.flash, f.flash());
                peak.strongestShake = Math.max(peak.strongestShake, f.strongestShake());
                peak.strongestFlash = Math.max(peak.strongestFlash, f.strongestFlash());
                peak.energy += f.flash() * 0.05;
                peak.samples++;
                HologramCapture.log(String.format(Locale.ROOT,
                        "screenfx: %s t=%02d live=%d shake=%.3f strongestShake=%.3f flash=%.3f demand=%.3f strongestFlash=%.3f tokens=%.3f",
                        label, tick, ScreenFx.liveRequests(), f.shake(), f.strongestShake(), f.flash(), f.flashDemand(),
                        f.strongestFlash(), ScreenFx.flashTokens()));
                return true;
            });
        }
        return HologramCapture.run("summary " + label, () -> HologramCapture.log(String.format(Locale.ROOT,
                "screenfx-summary: %s peak shake %.3f (strongest single %.3f, ratio %.3f), peak flash %.3f (strongest single %.3f), flash energy %.3f s-at-cap",
                label, peak.shake, peak.strongestShake, peak.strongestShake > 0 ? peak.shake / peak.strongestShake : 0,
                peak.flash, peak.strongestFlash, peak.energy)));
    }

    private static Step start(String label, Peak peak, Runnable spawn) {
        return HologramCapture.run(label, () -> {
            ScreenFx.clear();
            peak.reset();
            spawn.run();
        });
    }

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        Peak p = new Peak();
        double[] base = new double[1];
        int[] onsets = new int[1];
        Object owner = new Object();
        s.add(HologramCapture.command("gamerule send_command_feedback false"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(HologramCapture.run("settings: shake 1, flash 1", () -> {
            ScreenFx.settings().setShake(1.0f);
            ScreenFx.settings().setFlash(1.0f);
        }));
        s.add(HologramCapture.waitTicks(10));

        // 1. One request (a Fireball-sized shake and flash 4 blocks ahead).
        s.add(start("SFX single", p, () -> {
            shake(ahead(4), 0.45f);
            flash(ahead(4), 0.5f);
        }));
        s.add(HologramCapture.screenshot("SFX_single_flash_t0"));
        s.add(watch("single", p, 14, s));
        s.add(HologramCapture.check("single: peak shake = its intensity at full falloff (0.45)", () -> Math.abs(p.shake - 0.45) < 0.02));
        s.add(HologramCapture.check("single: requests expire (0 live after the envelope)", () -> ScreenFx.liveRequests() == 0));
        s.add(HologramCapture.run("remember single", () -> base[0] = p.shake));

        // 2. Twenty simultaneous identical requests, then twenty spread over 4..40 blocks.
        s.add(start("SFX twenty", p, () -> {
            for (int i = 0; i < 20; i++) {
                shake(ahead(4), 0.45f);
                flash(ahead(4), 0.5f);
            }
        }));
        s.add(HologramCapture.screenshot("SFX_twenty_flash_t0"));
        s.add(watch("twenty_same_point", p, 14, s));
        s.add(HologramCapture.check("twenty: shake <= 1.35 x a single request", () -> p.shake <= ScreenFxMixer.MAX_MERGE_FACTOR * base[0] + 1e-3));
        s.add(HologramCapture.check("twenty: shake > a single request (diminishing, not max-only)", () -> p.shake > base[0] + 0.05));
        s.add(start("SFX twenty spread", p, () -> {
            for (int i = 0; i < 20; i++) {
                shake(ahead(4 + i * 1.9), 0.45f);
                flash(ahead(4 + i * 1.9), 0.5f);
            }
        }));
        s.add(watch("twenty_spread_4_to_40", p, 14, s));
        s.add(HologramCapture.check("twenty spread: shake <= 1.35 x the strongest", () -> p.shake <= ScreenFxMixer.MAX_MERGE_FACTOR * p.strongestShake + 1e-3));

        // 3. Priorities: a CINEMATIC 0.2 replaces a NORMAL 0.5 until it is cancelled by owner.
        s.add(start("SFX priorities", p, () -> priorities(owner)));
        s.add(watch("priorities_cinematic_active", p, 8, s));
        s.add(HologramCapture.check("priorities: the CINEMATIC 0.2 replaces the NORMAL 0.5", () -> Math.abs(p.shake - 0.2) < 0.02));
        s.add(HologramCapture.run("cancel the cinematic owner", () -> {
            HologramCapture.log("info: cancelOwner removed " + ScreenFx.cancelOwner(owner));
            p.reset();
        }));
        s.add(watch("priorities_after_cancel", p, 8, s));
        s.add(HologramCapture.check("priorities: after cancel, the NORMAL 0.5 shows again", () -> Math.abs(p.shake - 0.5) < 0.02));

        // 4. Distances (one request each): 3, 10, 25, 50 blocks.
        for (double d : new double[]{3, 10, 25, 50}) {
            String label = String.format(Locale.ROOT, "distance_%02.0f", d);
            s.add(start("SFX " + label, p, () -> {
                shake(ahead(d), 0.45f);
                flash(ahead(d), 0.5f);
            }));
            s.add(watch(label, p, 10, s));
        }
        s.add(HologramCapture.check("distance: at 50 blocks (beyond the 40/48-block falloff) nothing", () -> p.shake == 0 && p.flash == 0));

        // 5. A flash behind the camera is reduced to 30 %.
        s.add(start("SFX behind", p, () -> flash(ahead(-5), 0.5f)));
        s.add(watch("flash_behind_camera", p, 8, s));
        s.add(HologramCapture.check("behind: flash scaled by 0.3", () -> Math.abs(p.strongestFlash - 0.15) < 0.01));

        // 6. Strobe: a 0.8 flash every 2 ticks for 3 s.
        s.add(start("SFX strobe", p, () -> onsets[0] = ScreenFx.flashOnsets()));
        for (int i = 0; i < 60; i++) {
            int k = i;
            s.add(mc -> {
                if (k % 2 == 0) flash(ahead(4), 0.8f);
                ScreenFxFrame f = ScreenFx.lastFrame();
                p.flash = Math.max(p.flash, f.flash());
                p.energy += f.flash() * 0.05;
                HologramCapture.log(String.format(Locale.ROOT, "screenfx: strobe t=%02d flash=%.3f demand=%.3f tokens=%.3f onsets=%d",
                        k, f.flash(), f.flashDemand(), ScreenFx.flashTokens(), ScreenFx.flashOnsets() - onsets[0]));
                return true;
            });
        }
        s.add(HologramCapture.run("strobe summary", () -> HologramCapture.log(String.format(Locale.ROOT,
                "screenfx-summary: strobe 30 requests in 3 s -> %d onsets, flash energy %.3f s-at-cap (unlimited demand would be ~%.2f)",
                ScreenFx.flashOnsets() - onsets[0], p.energy, 3.0 * 0.8))));
        s.add(HologramCapture.check("strobe: at most 3 onsets per second (<= 10 in 3 s)", () -> ScreenFx.flashOnsets() - onsets[0] <= 10));
        s.add(HologramCapture.check("strobe: flash energy within the budget (0.5 + 0.25/s x 3 s = 1.25)", () -> p.energy <= 1.25 + 0.06));

        // 7. Accessibility: Totality settings 0 and 0.5; Minecraft's Distortion Effects 0; Hide Lightning Flashes.
        s.add(HologramCapture.waitTicks(60));
        s.add(HologramCapture.run("settings: shake 0, flash 0", () -> {
            ScreenFx.settings().setShake(0.0f);
            ScreenFx.settings().setFlash(0.0f);
        }));
        s.add(start("SFX disabled", p, () -> {
            shake(ahead(4), 0.45f);
            flash(ahead(4), 0.5f);
        }));
        s.add(watch("settings_disabled", p, 10, s));
        s.add(HologramCapture.check("settings 0/0: no shake, no flash", () -> p.shake == 0 && p.flash == 0));
        s.add(HologramCapture.run("settings: shake 0.5, flash 0.5", () -> {
            ScreenFx.settings().setShake(0.5f);
            ScreenFx.settings().setFlash(0.5f);
        }));
        s.add(start("SFX reduced", p, () -> {
            shake(ahead(4), 0.45f);
            flash(ahead(4), 0.5f);
        }));
        s.add(watch("settings_half", p, 10, s));
        s.add(HologramCapture.check("settings 0.5: shake halved (0.225)", () -> Math.abs(p.shake - 0.225) < 0.015));
        double[] saved = new double[1];
        boolean[] savedHide = new boolean[1];
        s.add(HologramCapture.run("settings 1; Minecraft Distortion Effects 0, Hide Lightning Flashes on", () -> {
            ScreenFx.settings().setShake(1.0f);
            ScreenFx.settings().setFlash(1.0f);
            var o = Minecraft.getInstance().options;
            saved[0] = o.screenEffectScale().get();
            savedHide[0] = o.hideLightningFlash().get();
            o.screenEffectScale().set(0.0);
            o.hideLightningFlash().set(true);
        }));
        s.add(start("SFX vanilla accessibility", p, () -> {
            shake(ahead(4), 0.45f);
            flash(ahead(4), 0.5f);
        }));
        s.add(watch("vanilla_distortion0_hideflash", p, 10, s));
        s.add(HologramCapture.check("Distortion Effects 0 -> no shake; Hide Lightning Flashes -> no flash", () -> p.shake == 0 && p.flash == 0));
        s.add(HologramCapture.run("restore Minecraft options", () -> {
            var o = Minecraft.getInstance().options;
            o.screenEffectScale().set(saved[0]);
            o.hideLightningFlash().set(savedHide[0]);
        }));

        // 8. Cancellation by handle and clean end state.
        long[] handle = new long[1];
        s.add(HologramCapture.run("cancel by handle", () -> {
            ScreenFx.clear();
            handle[0] = ScreenFx.request(ScreenFxRequest.at(ScreenFxChannel.SHAKE, ahead(3), 0.5f, new ScreenFxEnvelope(0, 5, 0), SHAKE_FALLOFF, 0, null));
        }));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("cancel: handle removes the request", () -> ScreenFx.cancel(handle[0]) && ScreenFx.liveRequests() == 0));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("clean end: nothing live, output zero", () -> ScreenFx.liveRequests() == 0
                && ScreenFx.lastFrame().shake() == 0 && ScreenFx.lastFrame().flash() == 0));
        return s;
    }
}
