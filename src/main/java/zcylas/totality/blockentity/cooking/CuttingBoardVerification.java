package zcylas.totality.blockentity.cooking;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.block.cooking.CuttingBoardBlock;
import zcylas.totality.init.ModBlockEntities;
import zcylas.totality.init.blocks.CookingBlocks;
import zcylas.totality.init.items.SKIngredientItems;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.function.Supplier;

/**
 * Dev-environment-gated, opt-in (live-world) self-test of the Cutting Board (Creative Test F) on the server, with fake
 * survival players on a stone stage of its own (y 100 in chunk 46, 46): registration, placement and facing, the low
 * shape, adding Garlic (one per use, same ingredient only, never overwriting), chopping with a sword (one Garlic in,
 * exactly four Cloves out, one point of sword wear, nothing without a sword or an ingredient), output into a full
 * inventory, taking the stack back, save/load and the client update tag, and breaking the board with contents.
 */
public final class CuttingBoardVerification {

    private static final int CHUNK = 46;
    private static final int X0 = CHUNK * 16;
    private static final int FLOOR_Y = 100;

    private static int stage;
    private static int dueTick;

    private CuttingBoardVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!VerificationReporter.isDevEnvironment()) return;
            server.overworld().setChunkForced(CHUNK, CHUNK, true);
            stage = 1;
            dueTick = server.getTickCount() + 400;
        });
        ServerTickEvents.END_SERVER_TICK.register(CuttingBoardVerification::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (stage != 1 || server.getTickCount() < dueTick) return;
        stage = 0;
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "CuttingBoardVerification");
        try {
            run(server.overworld(), r);
        } finally {
            server.overworld().setChunkForced(CHUNK, CHUNK, false);
            r.summarize();
        }
    }

    private static void run(ServerLevel level, VerificationReporter r) {
        buildStage(level);
        ServerPlayer cook = TotalityFakePlayer.create(level, "BoardCook");
        cook.setGameMode(GameType.SURVIVAL);
        cook.setPos(X0 + 8.5, FLOOR_Y + 1, X0 + 1.5);
        CuttingBoardBlock block = CookingBlocks.CUTTING_BOARD;

        // ── Registration ──────────────────────────────────────────────────────
        check(r, "totality:cutting_board is a block with a block item and a block entity", () ->
                BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "cutting_board")) == block
                        && block.asItem() instanceof BlockItem item && item.getBlock() == block
                        && block.newBlockEntity(BlockPos.ZERO, block.defaultBlockState()).getType() == ModBlockEntities.CUTTING_BOARD);

        // ── Placement ─────────────────────────────────────────────────────────
        BlockPos floor = new BlockPos(X0 + 3, FLOOR_Y, X0 + 3);
        cook.setYRot(0.0F);   // looking south
        cook.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block, 2));
        InteractionResult placed = use(cook, floor, Direction.UP);
        BlockPos pos = floor.above();
        check(r, "a survival player places it on a stone top, facing where they look (south); one item used", () ->
                placed.consumesAction() && level.getBlockState(pos).is(block)
                        && level.getBlockState(pos).getValue(CuttingBoardBlock.FACING) == Direction.SOUTH
                        && cook.getMainHandItem().getCount() == 1
                        && level.getBlockEntity(pos) instanceof CuttingBoardBlockEntity);
        cook.setYRot(-90.0F);   // looking east
        BlockPos floor2 = new BlockPos(X0 + 6, FLOOR_Y, X0 + 3);
        use(cook, floor2, Direction.UP);
        check(r, "placed while looking east it faces east", () ->
                level.getBlockState(floor2.above()).is(block) && level.getBlockState(floor2.above()).getValue(CuttingBoardBlock.FACING) == Direction.EAST);
        BlockPos pillar = new BlockPos(X0 + 10, FLOOR_Y + 3, X0 + 3);
        level.setBlockAndUpdate(pillar, Blocks.STONE.defaultBlockState());
        cook.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block, 1));
        InteractionResult onSide = use(cook, pillar, Direction.NORTH);
        check(r, "it will not hang on a wall with nothing under it (nothing placed, nothing used)", () ->
                !onSide.consumesAction() && !level.getBlockState(pillar.north()).is(block) && cook.getMainHandItem().getCount() == 1);
        VoxelShape south = level.getBlockState(pos).getShape(level, pos, CollisionContext.empty());
        VoxelShape east = level.getBlockState(floor2.above()).getShape(level, floor2.above(), CollisionContext.empty());
        check(r, "low profile: 2 px high, 14 x 8 px, turned with its facing", () ->
                Math.abs(south.max(Direction.Axis.Y) - 2 / 16.0) < 1e-6
                        && Math.abs(south.max(Direction.Axis.X) - south.min(Direction.Axis.X) - 14 / 16.0) < 1e-6
                        && Math.abs(south.max(Direction.Axis.Z) - south.min(Direction.Axis.Z) - 8 / 16.0) < 1e-6
                        && Math.abs(east.max(Direction.Axis.Z) - east.min(Direction.Axis.Z) - 14 / 16.0) < 1e-6);
        CuttingBoardBlockEntity board = (CuttingBoardBlockEntity) level.getBlockEntity(pos);

        // ── Adding ingredients ────────────────────────────────────────────────
        cook.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SKIngredientItems.GARLIC, 5));
        InteractionResult added = use(cook, pos, Direction.UP);
        check(r, "right-click with Garlic puts one Garlic on the board", () ->
                added.consumesAction() && board.getIngredient().is(SKIngredientItems.GARLIC) && board.getIngredient().getCount() == 1
                        && cook.getMainHandItem().getCount() == 4);
        use(cook, pos, Direction.UP);
        check(r, "a second use adds a second (same ingredient stacks)", () -> board.getIngredient().getCount() == 2 && cook.getMainHandItem().getCount() == 3);
        ItemStack named = new ItemStack(SKIngredientItems.GARLIC, 1);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Odd Garlic"));
        cook.setItemInHand(InteractionHand.MAIN_HAND, named);
        InteractionResult refused = use(cook, pos, Direction.UP);
        check(r, "a different ingredient never overwrites or joins the stored one", () ->
                !refused.consumesAction() && board.getIngredient().getCount() == 2 && !board.getIngredient().has(DataComponents.CUSTOM_NAME)
                        && cook.getMainHandItem().getCount() == 1);
        cook.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT, 3));
        use(cook, pos, Direction.UP);
        check(r, "an item the board cannot cut is not taken", () ->
                board.getIngredient().getCount() == 2 && cook.getMainHandItem().getCount() == 3);

        // ── Chopping ──────────────────────────────────────────────────────────
        cook.getInventory().clearContent();
        cook.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        InteractionResult chopped = use(cook, pos, Direction.UP);
        check(r, "one sword use: one Garlic consumed, exactly four Garlic Cloves to the player, one point of sword wear", () ->
                chopped.consumesAction() && board.getIngredient().getCount() == 1 && count(cook, SKIngredientItems.GARLIC_CLOVE) == 4
                        && cook.getMainHandItem().getDamageValue() == 1 && dropped(level, pos, SKIngredientItems.GARLIC_CLOVE) == 0);
        use(cook, pos, Direction.UP);
        check(r, "the second chop empties the board: eight Cloves in all", () ->
                board.getIngredient().isEmpty() && count(cook, SKIngredientItems.GARLIC_CLOVE) == 8);
        InteractionResult nothing = use(cook, pos, Direction.UP);
        check(r, "a sword on an empty board does nothing (no Cloves from nowhere)", () ->
                !nothing.consumesAction() && count(cook, SKIngredientItems.GARLIC_CLOVE) == 8 && cook.getMainHandItem().getDamageValue() == 2);
        cook.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SKIngredientItems.GARLIC, 1));
        use(cook, pos, Direction.UP);
        cook.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        use(cook, pos, Direction.UP);
        check(r, "a non-sword tool does not chop", () -> board.getIngredient().getCount() == 1 && count(cook, SKIngredientItems.GARLIC_CLOVE) == 8);

        // ── Full inventory: nothing lost, nothing doubled ─────────────────────
        Inventory inventory = cook.getInventory();
        inventory.clearContent();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        inventory.setItem(inventory.getSelectedSlot(), new ItemStack(Items.IRON_SWORD));
        use(cook, pos, Direction.UP);
        check(r, "full inventory: the four Cloves are popped on top of the board, the Garlic is used once", () ->
                board.getIngredient().isEmpty() && dropped(level, pos, SKIngredientItems.GARLIC_CLOVE) == 4 && count(cook, SKIngredientItems.GARLIC_CLOVE) == 0);
        clearDrops(level, pos);
        inventory.setItem(1, new ItemStack(SKIngredientItems.GARLIC_CLOVE, 62));
        board.addOne(new ItemStack(SKIngredientItems.GARLIC, 1), cook);
        use(cook, pos, Direction.UP);
        check(r, "nearly full: two Cloves fill the stack, the other two land on the board", () ->
                count(cook, SKIngredientItems.GARLIC_CLOVE) == 64 && dropped(level, pos, SKIngredientItems.GARLIC_CLOVE) == 2);
        clearDrops(level, pos);

        // ── Taking the stack back ─────────────────────────────────────────────
        inventory.clearContent();
        board.addOne(new ItemStack(SKIngredientItems.GARLIC, 1), cook);
        board.addOne(new ItemStack(SKIngredientItems.GARLIC, 1), cook);
        board.addOne(new ItemStack(SKIngredientItems.GARLIC, 1), cook);
        InteractionResult taken = use(cook, pos, Direction.UP);
        check(r, "an empty hand takes the whole stored stack back", () ->
                taken.consumesAction() && board.getIngredient().isEmpty() && count(cook, SKIngredientItems.GARLIC) == 3);

        // ── Save / load and the client update ─────────────────────────────────
        board.addOne(new ItemStack(SKIngredientItems.GARLIC, 1), cook);
        board.addOne(new ItemStack(SKIngredientItems.GARLIC, 1), cook);
        CompoundTag saved = board.saveWithFullMetadata(level.registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess());
        check(r, "save and load keep the stored Garlic (2)", () ->
                loaded instanceof CuttingBoardBlockEntity copy && copy.getIngredient().is(SKIngredientItems.GARLIC) && copy.getIngredient().getCount() == 2);
        CompoundTag update = board.getUpdateTag(level.registryAccess());
        CuttingBoardBlockEntity client = new CuttingBoardBlockEntity(pos, level.getBlockState(pos));
        client.loadCustomOnly(input(update, level));
        check(r, "the client update tag carries the stored stack", () -> client.getIngredient().getCount() == 2);
        board.takeAll(cook);
        CuttingBoardBlockEntity cleared = new CuttingBoardBlockEntity(pos, level.getBlockState(pos));
        cleared.loadCustomOnly(input(update, level));
        cleared.loadCustomOnly(input(board.getUpdateTag(level.registryAccess()), level));
        check(r, "an emptied board's update clears the client's copy", () -> cleared.getIngredient().isEmpty());

        // ── Breaking with contents ────────────────────────────────────────────
        inventory.clearContent();
        board.addOne(new ItemStack(SKIngredientItems.GARLIC, 1), cook);
        board.addOne(new ItemStack(SKIngredientItems.GARLIC, 1), cook);
        cook.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        boolean broken = cook.gameMode.destroyBlock(pos);
        check(r, "survival break: the board item and the two stored Garlic drop, once each", () ->
                broken && level.getBlockState(pos).isAir() && dropped(level, pos, block.asItem()) == 1
                        && dropped(level, pos, SKIngredientItems.GARLIC) == 2);
        clearDrops(level, pos);
        BlockPos creativePos = floor2.above();
        CuttingBoardBlockEntity creativeBoard = (CuttingBoardBlockEntity) level.getBlockEntity(creativePos);
        creativeBoard.addOne(new ItemStack(SKIngredientItems.GARLIC, 1), cook);
        cook.setGameMode(GameType.CREATIVE);
        cook.gameMode.destroyBlock(creativePos);
        check(r, "creative break: no board item, but the stored Garlic is not lost", () ->
                level.getBlockState(creativePos).isAir() && dropped(level, creativePos, block.asItem()) == 0
                        && dropped(level, creativePos, SKIngredientItems.GARLIC) == 1);
        clearDrops(level, creativePos);
        cook.setGameMode(GameType.SURVIVAL);
        BlockPos shelf = new BlockPos(X0 + 12, FLOOR_Y + 1, X0 + 8);
        level.setBlockAndUpdate(shelf, Blocks.OAK_PLANKS.defaultBlockState());
        level.setBlockAndUpdate(shelf.above(), block.defaultBlockState());
        ((CuttingBoardBlockEntity) level.getBlockEntity(shelf.above())).addOne(new ItemStack(SKIngredientItems.GARLIC, 1), cook);
        level.setBlockAndUpdate(shelf, Blocks.AIR.defaultBlockState());
        check(r, "losing its support pops the board and its Garlic", () ->
                level.getBlockState(shelf.above()).isAir() && dropped(level, shelf.above(), block.asItem()) == 1
                        && dropped(level, shelf.above(), SKIngredientItems.GARLIC) == 1);
        clearDrops(level, shelf.above());
        cook.discard();
    }

    private static InteractionResult use(ServerPlayer player, BlockPos target, Direction face) {
        Vec3 hit = Vec3.atCenterOf(target).add(face.getUnitVec3().scale(0.5));
        BlockState state = player.level().getBlockState(target);
        if (state.getBlock() instanceof CuttingBoardBlock) hit = Vec3.atLowerCornerOf(target).add(0.4, 2 / 16.0, 0.5);
        return player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, target, false));
    }

    private static ValueInput input(CompoundTag tag, ServerLevel level) {
        return TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
    }

    private static void buildStage(ServerLevel level) {
        for (int x = X0; x < X0 + 16; x++) {
            for (int z = X0; z < X0 + 16; z++) {
                level.setBlockAndUpdate(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState());
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 5; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) n += stack.getCount();
        }
        return n;
    }

    private static int dropped(ServerLevel level, BlockPos at, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(1.5), e -> e.getItem().is(item))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static void clearDrops(ServerLevel level, BlockPos at) {
        level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(1.5)).forEach(ItemEntity::discard);
    }

    private static void check(VerificationReporter r, String label, Supplier<Boolean> body) {
        try {
            r.check(label, body.get(), "");
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
