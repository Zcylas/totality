package zcylas.totality.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.mining.BlockBreaking;
import zcylas.totality.api.mining.BlockDamageStorage;
import zcylas.totality.api.mining.MiningImpact;
import zcylas.totality.api.mining.MiningSource;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Block Breaking V2 Pass 4A in a real client and integrated world with a REAL player: in-place transformations keep
 * Integrity percentage while the vanilla side effects stay intact — the Wax On / Wax Off advancements, the Hanging
 * Roots drop from tilling Rooted Dirt, double Copper Chest inventories (nothing dropped or duplicated), the Axe's wear
 * and the stripped log's axis. Results go to {@code totality-block-breaking-pass4a-results.txt}.
 */
public class BlockBreakingPass4AClientGameTest implements FabricClientGameTest {

    private final List<String> results = new ArrayList<>();
    private int failures;

    private synchronized void check(String name, boolean ok, Object detail) {
        String line = (ok ? "PASS " : "FAIL ") + name + " — " + detail;
        results.add(line);
        System.out.println("[BlockBreakingPass4A] " + line);
        if (!ok) failures++;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runOnServer(this::transformations);
        }
        context.runOnClient(mc -> {
            try {
                Files.write(mc.gameDirectory.toPath().resolve("totality-block-breaking-pass4a-results.txt"), results);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
        if (failures > 0) throw new AssertionError(failures + " Block Breaking Pass 4A check(s) failed");
    }

    private static Block block(String id) { return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)); }

    private static void strike(ServerLevel level, BlockPos pos, float damage) {
        BlockBreaking.applyImpact(level, new MiningImpact(pos, Direction.UP, Vec3.atCenterOf(pos), damage, 4,
                MiningSource.of(MiningSource.Kind.PLAYER_TOOL, null)));
    }

    private static ItemStack use(ServerPlayer player, BlockPos pos, ItemStack stack) {
        player.setItemSlot(EquipmentSlot.MAINHAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false)));
        return player.getMainHandItem();
    }

    private static String rec(ServerLevel level, BlockPos pos) {
        var e = BlockDamageStorage.get(level).get(pos, level.getBlockState(pos));
        return e == null ? "none" : e.block + " " + e.integrity + "/" + e.max;
    }

    private static boolean advancement(MinecraftServer server, ServerPlayer player, String id) {
        var holder = server.getAdvancements().get(Identifier.withDefaultNamespace(id));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private void transformations(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = player.blockPosition().east(2).above(), b = pos.east();
        AABB around = new AABB(pos).inflate(4);

        // Stripping with the real player: percentage, axis, Axe wear.
        level.setBlockAndUpdate(pos, Blocks.BIRCH_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        strike(level, pos, 30f);
        ItemStack axe = use(player, pos, new ItemStack(Items.IRON_AXE));
        BlockState stripped = level.getBlockState(pos);
        check("strip.birch_log_percentage_axis_wear", stripped.is(Blocks.STRIPPED_BIRCH_LOG) && stripped.getValue(BlockStateProperties.AXIS) == Direction.Axis.Z
                && rec(level, pos).equals("minecraft:stripped_birch_log 70.0/100.0") && axe.getDamageValue() == 1, rec(level, pos) + " wear=" + axe.getDamageValue());

        // Waxing and wax removal: records kept, vanilla advancements granted.
        level.setBlockAndUpdate(pos, block("cut_copper").defaultBlockState());
        strike(level, pos, 50f);
        ItemStack honey = use(player, pos, new ItemStack(Items.HONEYCOMB, 4));
        boolean waxOn = advancement(server, player, "husbandry/wax_on");
        String afterWax = rec(level, pos);
        use(player, pos, new ItemStack(Items.IRON_AXE));
        boolean waxOff = advancement(server, player, "husbandry/wax_off");
        check("copper.wax_then_unwax_keep_percentage_and_grant_advancements",
                afterWax.equals("minecraft:waxed_cut_copper 100.0/150.0") && rec(level, pos).equals("minecraft:cut_copper 100.0/150.0")
                        && honey.getCount() == 3 && waxOn && waxOff,
                afterWax + " -> " + rec(level, pos) + " honeycomb=" + honey.getCount() + " wax_on=" + waxOn + " wax_off=" + waxOff);

        // Double Copper Chest: both halves converted, independent records, inventories intact, nothing dropped.
        BlockState chest = block("copper_chest").defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(pos, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
        level.setBlockAndUpdate(b, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        if (level.getBlockEntity(pos) instanceof Container c) c.setItem(3, new ItemStack(Items.DIAMOND, 7));
        if (level.getBlockEntity(b) instanceof Container c) c.setItem(5, new ItemStack(Items.IRON_INGOT, 9));
        strike(level, pos, 15f);
        strike(level, b, 45f);
        use(player, b, new ItemStack(Items.HONEYCOMB));                                         // wax from the RIGHT half
        boolean inv = level.getBlockEntity(pos) instanceof Container ca && ca.getItem(3).is(Items.DIAMOND) && ca.getItem(3).getCount() == 7
                && level.getBlockEntity(b) instanceof Container cb && cb.getItem(5).is(Items.IRON_INGOT) && cb.getItem(5).getCount() == 9;
        int loose = level.getEntitiesOfClass(ItemEntity.class, around).size();
        check("copper_chest.double_wax_keeps_both_records_and_inventories",
                rec(level, pos).equals("minecraft:waxed_copper_chest 135.0/150.0") && rec(level, b).equals("minecraft:waxed_copper_chest 105.0/150.0")
                        && inv && loose == 0, rec(level, pos) + " / " + rec(level, b) + " inventories=" + inv + " looseItems=" + loose);
        level.setBlockAndUpdate(b, Blocks.AIR.defaultBlockState());

        // Rooted Dirt tilling: percentage kept AND vanilla's Hanging Roots drop still happens.
        level.setBlockAndUpdate(pos, Blocks.ROOTED_DIRT.defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        strike(level, pos, 20f);
        ItemStack hoe = use(player, pos, new ItemStack(Items.IRON_HOE));
        boolean roots = level.getEntitiesOfClass(ItemEntity.class, around).stream().anyMatch(e -> e.getItem().is(Items.HANGING_ROOTS));
        check("soil.rooted_dirt_till_keeps_percentage_and_drops_hanging_roots",
                rec(level, pos).equals("minecraft:dirt 80.0/100.0") && roots && hoe.getDamageValue() == 1,
                rec(level, pos) + " roots=" + roots + " wear=" + hoe.getDamageValue());
        level.getEntitiesOfClass(ItemEntity.class, around).forEach(e -> e.discard());

        // Same-id replacement after a removal still starts intact in the real world.
        level.setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState());
        strike(level, pos, 40f);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.STRIPPED_OAK_LOG.defaultBlockState());
        check("replacement.authored_pair_placed_fresh_is_intact", rec(level, pos).equals("none"), rec(level, pos));
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }
}
