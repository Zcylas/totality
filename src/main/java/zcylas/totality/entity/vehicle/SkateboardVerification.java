package zcylas.totality.entity.vehicle;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.init.ModEntities;
import zcylas.totality.init.items.VehicleItems;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.List;
import java.util.function.Supplier;

/**
 * Dev-environment-gated, opt-in (live-world) self-test of the default skateboard on the server, with fake players on a
 * stage of its own (a stone floor at y 100 in chunk 42, 42, a wall across it): placing it from the item, mounting and
 * where the rider stands, the ridden collision box and targeting, stepping off, sneak pick-up (exactly one item, never
 * twice, never from under a rider), breaking (one drop; creative removes it), save and load, and, over real server
 * ticks, an unridden board coasting over a row of slabs to a stop short of a wall (its nose kept out of it), and
 * falling to the ground.
 * The ridden movement itself runs on the rider's client and is checked in the client capture (scene 54).
 */
public final class SkateboardVerification {

    private static final int CHUNK = 42;
    private static final int X0 = CHUNK * 16;
    private static final int FLOOR_Y = 100;
    private static final int WALL_Z = X0 + 14;
    private static final int SLAB_Z = X0 + 9;
    private static final int MOTION_TICKS = 120;

    private static VerificationReporter reporter;
    private static int stage;
    private static int dueTick;
    private static SkateboardEntity coaster;
    private static SkateboardEntity faller;

    private SkateboardVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!VerificationReporter.isDevEnvironment()) return;
            server.overworld().setChunkForced(CHUNK, CHUNK, true);
            stage = 1;
            dueTick = server.getTickCount() + 400;
        });
        ServerTickEvents.END_SERVER_TICK.register(SkateboardVerification::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (stage == 1) {
            boolean ready = server.overworld().areEntitiesLoaded(ChunkPos.pack(CHUNK, CHUNK));
            if (!ready && server.getTickCount() < dueTick) return;
            stage = 2;
            dueTick = server.getTickCount() + MOTION_TICKS;
            immediateChecks(server);
        } else if (stage == 2 && server.getTickCount() >= dueTick) {
            stage = 0;
            motionChecks();
            server.overworld().setChunkForced(CHUNK, CHUNK, false);
            reporter.summarize();
        }
    }

    private static void immediateChecks(MinecraftServer server) {
        ServerLevel level = server.overworld();
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "SkateboardVerification");
        reporter = r;
        buildStage(level);
        ServerPlayer rider = TotalityFakePlayer.create(level, "SkateRider");
        ServerPlayer other = TotalityFakePlayer.create(level, "SkateOther");
        rider.setGameMode(GameType.SURVIVAL);
        other.setGameMode(GameType.SURVIVAL);
        rider.setPos(X0 + 8.5, FLOOR_Y + 1, X0 + 3.5);
        other.setPos(X0 + 10.5, FLOOR_Y + 1, X0 + 3.5);

        // ── Placing it from the item ──────────────────────────────────────────
        rider.setYRot(90.0F);
        rider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VehicleItems.SKATEBOARD));
        BlockPos floor = new BlockPos(X0 + 8, FLOOR_Y, X0 + 5);
        Vec3 click = new Vec3(X0 + 8.5, FLOOR_Y + 1, X0 + 5.5);
        InteractionResult placed = rider.getMainHandItem().useOn(new UseOnContext(rider, InteractionHand.MAIN_HAND,
                new BlockHitResult(click, Direction.UP, floor, false)));
        List<SkateboardEntity> boards = boardsNear(level, click, 1.0);
        check(r, "used on a block's top, the item sets one board down there, heading the player's way", () ->
                placed.consumesAction() && boards.size() == 1 && Mth.equal(boards.get(0).getYRot(), 90.0F)
                        && boards.get(0).position().distanceTo(click) < 0.01);
        check(r, "a survival player's skateboard item is used up", () -> rider.getMainHandItem().isEmpty());
        InteractionResult side = new ItemStack(VehicleItems.SKATEBOARD).useOn(new UseOnContext(other, InteractionHand.MAIN_HAND,
                new BlockHitResult(click, Direction.NORTH, floor, false)));
        check(r, "used on the side of a block it does nothing", () -> side == InteractionResult.PASS && boardsNear(level, click, 3.0).size() == 1);
        SkateboardEntity board = boards.get(0);

        // ── Mounting: standing on the grip, side-on ───────────────────────────
        check(r, "unridden: small box (0.9 x 0.35), targetable", () ->
                Mth.equal(board.getBbHeight(), 0.35F) && Mth.equal(board.getBbWidth(), 0.9F) && board.isPickable());
        boolean mounted = rider.startRiding(board);
        board.positionRider(rider);
        check(r, "use to mount: the player rides and controls it", () ->
                mounted && board.hasPassenger(rider) && board.getControllingPassenger() == rider);
        check(r, "the rider's feet are on the grip (board y + " + SkateboardEntity.GRIP_TOP + ")", () ->
                Math.abs(rider.getY() - (board.getY() + SkateboardEntity.GRIP_TOP)) < 1e-6
                        && Math.abs(rider.getX() - board.getX()) < 1e-6 && Math.abs(rider.getZ() - board.getZ()) < 1e-6);
        check(r, "side-on stance: the body faces " + SkateboardEntity.STANCE_YAW + " deg right of the heading", () ->
                Mth.equal(Mth.wrapDegrees(rider.yBodyRot - board.getYRot()), SkateboardEntity.STANCE_YAW));
        check(r, "ridden: the box grows to the rider's height, and the board cannot be targeted", () ->
                board.getBbHeight() > 2.1F && !board.isPickable());
        rider.setYRot(board.getYRot() + 170.0F);
        board.onPassengerTurned(rider);
        check(r, "the rider's view stays within " + SkateboardEntity.LOOK_LIMIT + " deg of the heading", () ->
                Math.abs(Mth.wrapDegrees(rider.getYRot() - board.getYRot())) <= SkateboardEntity.LOOK_LIMIT + 1e-3);
        other.setShiftKeyDown(true);
        InteractionResult stolen = board.interact(other, InteractionHand.MAIN_HAND, board.position());
        check(r, "no pick-up from under a rider", () ->
                stolen == InteractionResult.PASS && !board.isRemoved() && count(other) == 0);
        InteractionResult second = board.interact(other, InteractionHand.MAIN_HAND, board.position());
        check(r, "no second rider", () -> !other.isPassenger() && board.getPassengers().size() == 1 && second == InteractionResult.PASS);
        other.setShiftKeyDown(false);

        // ── Stepping off ──────────────────────────────────────────────────────
        rider.stopRiding();
        check(r, "sneak to step off: off the board, standing clear of blocks beside it, the box small again", () ->
                !rider.isPassenger() && !board.isVehicle() && level.noCollision(rider)
                        && rider.position().distanceTo(board.position()) < 2.0 && rider.getY() >= FLOOR_Y + 1 - 1e-6
                        && Mth.equal(board.getBbHeight(), 0.35F));

        // ── Picking it up: exactly one item, never twice ──────────────────────
        rider.setShiftKeyDown(true);
        InteractionResult picked = board.interact(rider, InteractionHand.MAIN_HAND, board.position());
        check(r, "sneak-use picks it up: the board is gone, the player has one skateboard", () ->
                picked.consumesAction() && board.isRemoved() && count(rider) == 1);
        board.interact(rider, InteractionHand.MAIN_HAND, board.position());
        check(r, "picking up a removed board gives nothing more", () -> count(rider) == 1);
        rider.setShiftKeyDown(false);

        // ── Breaking it: one drop; a creative player just removes it ──────────
        SkateboardEntity broken = spawn(level, X0 + 4.5, FLOOR_Y + 1, X0 + 9.5, 0.0F);
        for (int i = 0; i < 6 && !broken.isRemoved(); i++) broken.hurtServer(level, level.damageSources().playerAttack(other), 1.0F);
        check(r, "hit enough times it breaks and drops exactly one skateboard", () ->
                broken.isRemoved() && dropped(level, broken.position()) == 1);
        clearDrops(level, broken.position());
        other.setGameMode(GameType.CREATIVE);
        SkateboardEntity removed = spawn(level, X0 + 4.5, FLOOR_Y + 1, X0 + 9.5, 0.0F);
        removed.hurtServer(level, level.damageSources().playerAttack(other), 1.0F);
        check(r, "a creative player's hit removes it without a drop", () -> removed.isRemoved() && dropped(level, removed.position()) == 0);

        // ── Save and load ─────────────────────────────────────────────────────
        SkateboardEntity saved = spawn(level, X0 + 2.5, FLOOR_Y + 1, X0 + 2.5, 33.0F);
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        saved.saveWithoutId(out);
        SkateboardEntity loaded = ModEntities.SKATEBOARD.create(level, EntitySpawnReason.LOAD);
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
        check(r, "save and load keep its position and heading", () ->
                loaded.position().distanceTo(saved.position()) < 1e-6 && Mth.equal(loaded.getYRot(), 33.0F));
        saved.discard();

        // ── For the motion checks, over real ticks ────────────────────────────
        coaster = spawn(level, X0 + 8.5, FLOOR_Y + 1, X0 + 4.5, 0.0F);
        coaster.setDeltaMovement(0.0, 0.0, 0.3);
        faller = spawn(level, X0 + 12.5, FLOOR_Y + 4.5, X0 + 4.5, 0.0F);
    }

    private static void motionChecks() {
        VerificationReporter r = reporter;
        check(r, "unridden, it coasts on (heading +z at 0.3 blocks/tick), up and over a row of slabs, and comes to a stop", () ->
                coaster.getZ() > SLAB_Z + 1.5 && coaster.getDeltaMovement().horizontalDistance() < 1e-3);
        check(r, "it stops short of the wall: the nose never enters it", () ->
                coaster.getZ() + 1.23 <= WALL_Z + 1e-3);
        check(r, "unsupported, it falls and lands on the floor", () ->
                Math.abs(faller.getY() - (FLOOR_Y + 1)) < 1e-3 && faller.onGround());
        coaster.discard();
        faller.discard();
    }

    private static void buildStage(ServerLevel level) {
        for (int x = X0; x < X0 + 16; x++) {
            for (int z = X0; z < X0 + 16; z++) {
                level.setBlockAndUpdate(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState());
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 6; y++) {
                    boolean wall = z == WALL_Z && y <= FLOOR_Y + 2;
                    boolean slab = z == SLAB_Z && y == FLOOR_Y + 1 && x < X0 + 11;
                    level.setBlockAndUpdate(new BlockPos(x, y, z), wall ? Blocks.STONE.defaultBlockState()
                            : slab ? Blocks.SMOOTH_STONE_SLAB.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static SkateboardEntity spawn(ServerLevel level, double x, double y, double z, float yaw) {
        SkateboardEntity board = ModEntities.SKATEBOARD.create(level, EntitySpawnReason.COMMAND);
        board.setInitialPos(x, y, z, yaw);
        level.addFreshEntity(board);
        return board;
    }

    private static List<SkateboardEntity> boardsNear(ServerLevel level, Vec3 at, double radius) {
        return level.getEntitiesOfClass(SkateboardEntity.class, AABB.ofSize(at, radius * 2, radius * 2, radius * 2), b -> !b.isRemoved());
    }

    private static int count(ServerPlayer player) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(VehicleItems.SKATEBOARD)) n += stack.getCount();
        }
        return n;
    }

    private static int dropped(ServerLevel level, Vec3 at) {
        return level.getEntitiesOfClass(ItemEntity.class, AABB.ofSize(at, 6, 6, 6), e -> e.getItem().is(VehicleItems.SKATEBOARD))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static void clearDrops(ServerLevel level, Vec3 at) {
        level.getEntitiesOfClass(ItemEntity.class, AABB.ofSize(at, 6, 6, 6)).forEach(ItemEntity::discard);
    }

    private static void check(VerificationReporter r, String label, Supplier<Boolean> body) {
        try {
            r.check(label, body.get(), "");
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
