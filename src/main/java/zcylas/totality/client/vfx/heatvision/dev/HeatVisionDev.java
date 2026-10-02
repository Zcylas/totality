package zcylas.totality.client.vfx.heatvision.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.renderer.ability.HeatVisionBeam;
import zcylas.totality.client.renderer.ability.HeatVisionBeamRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Development-only Heat Vision V2 tooling under {@code /totalityvfx heatvision} (VFX Experiment 2): client-side test
 * beams (nothing is sent to the server, nothing is damaged), the pre-V2 renderer for A/B comparison, GPU timing.
 */
public final class HeatVisionDev {

    private static final double TEST_RANGE = 16.0;

    private HeatVisionDev() {}

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("heatvision")
                .executes(HeatVisionDev::status)
                .then(ClientCommands.literal("classic")
                        .then(ClientCommands.literal("on").executes(ctx -> classic(ctx, true)))
                        .then(ClientCommands.literal("off").executes(ctx -> classic(ctx, false))))
                .then(ClientCommands.literal("test")
                        .then(ClientCommands.literal("clear").executes(ctx -> {
                            HeatVisionBeamRenderer.setTestBeams(List.of());
                            return status(ctx);
                        }))
                        .then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 256)).executes(ctx -> {
                            fanInFront(IntegerArgumentType.getInteger(ctx, "count"));
                            return status(ctx);
                        })))
                .then(ClientCommands.literal("timing")
                        .then(ClientCommands.literal("on").executes(ctx -> {
                            HeatVisionBeamRenderer.setTiming(true);
                            return status(ctx);
                        }))
                        .then(ClientCommands.literal("off").executes(ctx -> {
                            HeatVisionBeamRenderer.setTiming(false);
                            return status(ctx);
                        })));
    }

    /** {@code count} parallel test beams from a line two blocks in front of the player, along the view direction. */
    public static void fanInFront(int count) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        List<HeatVisionBeam> beams = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double offset = count == 1 ? 0.0 : (i / (double) (count - 1) - 0.5) * 8.0;
            double lift = ((i % 3) - 1) * 0.6;
            Vec3 start = eye.add(look.scale(2.0)).add(right.scale(offset)).add(0, lift, 0);
            beams.add(beam(start, start.add(look.scale(TEST_RANGE))));
        }
        HeatVisionBeamRenderer.setTestBeams(beams);
    }

    /** A test beam from {@code start} towards {@code end}, stopped by the first block (that is its impact). */
    public static HeatVisionBeam beam(Vec3 start, Vec3 end) {
        Minecraft mc = Minecraft.getInstance();
        BlockHitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                mc.player));
        boolean blocked = hit.getType() != HitResult.Type.MISS;
        return new HeatVisionBeam(start, blocked ? hit.getLocation() : end, blocked, 1.0f, 1.0f);
    }

    private static int classic(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        HeatVisionBeamRenderer.setClassic(on);
        return status(ctx);
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        long ns = HeatVisionBeamRenderer.lastGpuNanos();
        ctx.getSource().sendFeedback(Component.literal(String.format(Locale.ROOT,
                "[Heat Vision] %s renderer, %d beam(s) last frame, %d draw call(s), GPU %s, vertex buffers %d KiB",
                HeatVisionBeamRenderer.classic() ? "classic (pre-V2)" : "V2", HeatVisionBeamRenderer.lastBeamCount(),
                HeatVisionBeamRenderer.lastDrawCalls(), ns < 0 ? "not measured" : String.format(Locale.ROOT, "%.3f ms", ns / 1.0e6),
                HeatVisionBeamRenderer.vertexBufferBytes() / 1024)));
        return 1;
    }
}
