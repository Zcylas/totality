package zcylas.totality.entity.portal;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
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

/**
 * Operator-only dev commands for inspecting the visual portal test (nothing else uses the portal):
 * <ul>
 *   <li>/portaltest open [size]: opens a portal standing on the ground 3.5 blocks ahead, facing you;</li>
 *   <li>/portaltest collapse: collapses every test portal within 64 blocks;</li>
 *   <li>/portaltest clear: removes them at once (no animation).</li>
 * </ul>
 * Any orientation: /summon totality:visual_portal ~ ~2 ~ {Rotation:[yaw,pitch]} (the position is the portal's centre).
 */
public final class VisualPortalCommands {

    private static final double RANGE = 64.0;

    private VisualPortalCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("portaltest")
                        .requires(source -> {
                            ServerPlayer p = source.getPlayer();
                            return p != null && source.getServer().getPlayerList().isOp(p.nameAndId());
                        })
                        .then(Commands.literal("open")
                                .executes(ctx -> open(ctx, 1.0F))
                                .then(Commands.argument("size", FloatArgumentType.floatArg(VisualPortalEntity.MIN_SIZE, VisualPortalEntity.MAX_SIZE))
                                        .executes(ctx -> open(ctx, FloatArgumentType.getFloat(ctx, "size")))))
                        .then(Commands.literal("collapse").executes(ctx -> {
                            List<VisualPortalEntity> portals = nearby(ctx.getSource());
                            portals.forEach(VisualPortalEntity::collapse);
                            ctx.getSource().sendSuccess(() -> Component.literal("Collapsing " + portals.size() + " test portal(s)."), false);
                            return portals.size();
                        }))
                        .then(Commands.literal("clear").executes(ctx -> {
                            List<VisualPortalEntity> portals = nearby(ctx.getSource());
                            portals.forEach(VisualPortalEntity::discard);
                            ctx.getSource().sendSuccess(() -> Component.literal("Removed " + portals.size() + " test portal(s)."), false);
                            return portals.size();
                        }))));
    }

    private static int open(CommandContext<CommandSourceStack> ctx, float size) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Vec3 ahead = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(3.5);
        Vec3 feet = player.position().add(ahead);
        spawn(player.level(), new Vec3(feet.x, feet.y + VisualPortalVfx.HALF_HEIGHT * size + 0.05, feet.z), player.getYRot() + 180.0F, 0.0F, size);
        return 1;
    }

    /** Opens a test portal centred at {@code centre}, facing along (yaw, pitch). */
    public static VisualPortalEntity spawn(ServerLevel level, Vec3 centre, float yaw, float pitch, float size) {
        VisualPortalEntity portal = ModEntities.VISUAL_PORTAL.create(level, EntitySpawnReason.COMMAND);
        portal.snapTo(centre.x, centre.y, centre.z, yaw, pitch);
        portal.setSize(size);
        level.addFreshEntity(portal);
        return portal;
    }

    private static List<VisualPortalEntity> nearby(CommandSourceStack source) {
        return source.getLevel().getEntitiesOfClass(VisualPortalEntity.class, AABB.ofSize(source.getPosition(), RANGE * 2, RANGE * 2, RANGE * 2));
    }
}
