package zcylas.totality.client.vfx.glow.dev;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.vfx.glow.EmissiveGlow;
import zcylas.totality.client.vfx.glow.EmissiveGlowSettings;
import zcylas.totality.client.vfx.heatvision.dev.HeatVisionCapture;
import zcylas.totality.client.vfx.heatvision.dev.HeatVisionDev;

import java.util.Locale;

/**
 * Development-only {@code /totalityvfx} command for VFX Experiment 1 (client-side, never sent to the server, registered
 * only in a Fabric development environment):
 *
 * <ul>
 *   <li>{@code glow}: status; {@code glow on|off}; {@code glow intensity <0-4>}; {@code glow levels <2-6>} (saved to
 *       {@code config/totality-vfx.properties});</li>
 *   <li>{@code test <count>}: client-only test objects in front of the player; {@code test clear} removes them;</li>
 *   <li>{@code timing on|off}: GPU timing of the glow passes; {@code timing} prints the latest value;</li>
 *   <li>{@code heatvision ...}: Heat Vision V2 test beams, classic A/B renderer, timing ({@link HeatVisionDev}).</li>
 * </ul>
 */
public final class EmissiveGlowDevCommand {

    static final String ROOT = "totalityvfx";

    private EmissiveGlowDevCommand() {}

    public static void registerIfDevelopmentEnvironment() {
        if (!VerificationReporter.isDevEnvironment()) return;
        EmissiveTestScene.register();
        if (HologramCapture.requested()) {
            HologramCapture.addScene(66, EmissiveGlowCapture.scenes());
            HologramCapture.addScene(67, HeatVisionCapture.scenes());
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal(ROOT).executes(EmissiveGlowDevCommand::status);
            root.then(ClientCommands.literal("glow").executes(EmissiveGlowDevCommand::status)
                    .then(ClientCommands.literal("on").executes(ctx -> enabled(ctx, true)))
                    .then(ClientCommands.literal("off").executes(ctx -> enabled(ctx, false)))
                    .then(ClientCommands.literal("intensity")
                            .then(ClientCommands.argument("value", FloatArgumentType.floatArg(0.0f, EmissiveGlowSettings.MAX_INTENSITY))
                                    .executes(ctx -> {
                                        EmissiveGlow.settings().setIntensity(FloatArgumentType.getFloat(ctx, "value"));
                                        EmissiveGlow.saveSettings();
                                        return status(ctx);
                                    })))
                    .then(ClientCommands.literal("levels")
                            .then(ClientCommands.argument("value", IntegerArgumentType.integer(EmissiveGlowSettings.MIN_LEVELS,
                                            EmissiveGlowSettings.MAX_LEVELS))
                                    .executes(ctx -> {
                                        EmissiveGlow.settings().setLevels(IntegerArgumentType.getInteger(ctx, "value"));
                                        EmissiveGlow.saveSettings();
                                        return status(ctx);
                                    }))));
            root.then(ClientCommands.literal("test")
                    .then(ClientCommands.literal("clear").executes(ctx -> {
                        EmissiveTestScene.INSTANCE.clear();
                        ctx.getSource().sendFeedback(Component.literal("[VFX test] Test objects removed."));
                        return 1;
                    }))
                    .then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 400)).executes(ctx -> {
                        placeInFront(IntegerArgumentType.getInteger(ctx, "count"));
                        ctx.getSource().sendFeedback(Component.literal("[VFX test] " + EmissiveTestScene.INSTANCE.count()
                                + " test objects placed (client only)."));
                        return 1;
                    })));
            root.then(ClientCommands.literal("timing").executes(EmissiveGlowDevCommand::timing)
                    .then(ClientCommands.literal("on").executes(ctx -> {
                        EmissiveGlow.setTiming(true);
                        return timing(ctx);
                    }))
                    .then(ClientCommands.literal("off").executes(ctx -> {
                        EmissiveGlow.setTiming(false);
                        return timing(ctx);
                    })));
            root.then(HeatVisionDev.command());
            dispatcher.register(root);
        });
    }

    /** Places test objects in front of the local player (row of four plus a pillar, or a grid). */
    static void placeInFront(int count) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        EmissiveTestScene.INSTANCE.place(player.getEyePosition(), player.getLookAngle(), count, count <= 4);
    }

    private static int enabled(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        EmissiveGlow.settings().setEnabled(on);
        EmissiveGlow.saveSettings();
        return status(ctx);
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        EmissiveGlowSettings s = EmissiveGlow.settings();
        ctx.getSource().sendFeedback(Component.literal(String.format(Locale.ROOT,
                "[VFX glow] %s, intensity %.2f, levels %d, %d source(s), buffers %s",
                s.enabled() ? "ON" : "OFF", s.intensity(), s.levels(), EmissiveGlow.sourceCount(),
                EmissiveGlow.holdsBuffers() ? "allocated" : "released")));
        return 1;
    }

    private static int timing(CommandContext<FabricClientCommandSource> ctx) {
        long ns = EmissiveGlow.lastGpuNanos();
        ctx.getSource().sendFeedback(Component.literal(ns < 0 ? "[VFX glow] GPU time: not measured yet"
                : String.format(Locale.ROOT, "[VFX glow] GPU time %.3f ms for %d emissive quads", ns / 1.0e6, EmissiveGlow.lastQuadCount())));
        return 1;
    }
}
