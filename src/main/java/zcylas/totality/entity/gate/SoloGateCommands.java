package zcylas.totality.entity.gate;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.init.ModEntities;

import java.util.List;
import java.util.function.Consumer;

/**
 * Operator-only dev commands for inspecting the gate test (nothing else uses the gate):
 * <ul>
 *   <li>/gatetest pair: a Blue Normal Gate and a Red Gate side by side, 5 blocks ahead, facing you;</li>
 *   <li>/gatetest open blue|red: one gate, 5 blocks ahead;</li>
 *   <li>/gatetest pulse: the entry/distortion reaction on every test gate within 64 blocks (visual only);</li>
 *   <li>/gatetest close: closes them (animated); /gatetest clear: removes them at once.</li>
 * </ul>
 */
public final class SoloGateCommands {

    private static final double RANGE = 64.0;

    private SoloGateCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("gatetest")
                        .requires(source -> {
                            ServerPlayer p = source.getPlayer();
                            return p != null && source.getServer().getPlayerList().isOp(p.nameAndId());
                        })
                        .then(Commands.literal("pair").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Vec3 side = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F).scale(2.4);
                            spawnAhead(player, side, GatePalette.BLUE);
                            spawnAhead(player, side.scale(-1), GatePalette.RED);
                            return 2;
                        }))
                        .then(Commands.literal("open")
                                .then(Commands.literal("blue").executes(ctx -> open(ctx, GatePalette.BLUE)))
                                .then(Commands.literal("red").executes(ctx -> open(ctx, GatePalette.RED))))
                        .then(Commands.literal("pulse").executes(ctx -> each(ctx.getSource(), SoloGateEntity::pulse, "Pulsed")))
                        .then(Commands.literal("close").executes(ctx -> each(ctx.getSource(), SoloGateEntity::close, "Closing")))
                        .then(Commands.literal("clear").executes(ctx -> each(ctx.getSource(), SoloGateEntity::discard, "Removed")))));
    }

    private static int open(CommandContext<CommandSourceStack> ctx, GatePalette palette) throws CommandSyntaxException {
        spawnAhead(ctx.getSource().getPlayerOrException(), Vec3.ZERO, palette);
        return 1;
    }

    private static void spawnAhead(ServerPlayer player, Vec3 sideways, GatePalette palette) {
        Vec3 feet = player.position().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(5.0)).add(sideways);
        spawn(player.level(), new Vec3(feet.x, feet.y + SoloGateEntity.CENTRE_HEIGHT, feet.z), player.getYRot() + 180.0F, palette);
    }

    /** Opens a test gate centred at {@code centre}, facing along {@code yaw}, in {@code palette}. */
    public static SoloGateEntity spawn(ServerLevel level, Vec3 centre, float yaw, GatePalette palette) {
        SoloGateEntity gate = ModEntities.SOLO_GATE.create(level, EntitySpawnReason.COMMAND);
        gate.snapTo(centre.x, centre.y, centre.z, yaw, 0.0F);
        gate.setPalette(palette);
        level.addFreshEntity(gate);
        return gate;
    }

    private static int each(CommandSourceStack source, Consumer<SoloGateEntity> action, String verb) {
        List<SoloGateEntity> gates = source.getLevel().getEntitiesOfClass(SoloGateEntity.class,
                AABB.ofSize(source.getPosition(), RANGE * 2, RANGE * 2, RANGE * 2));
        gates.forEach(action);
        source.sendSuccess(() -> Component.literal(verb + " " + gates.size() + " test gate(s)."), false);
        return gates.size();
    }
}
