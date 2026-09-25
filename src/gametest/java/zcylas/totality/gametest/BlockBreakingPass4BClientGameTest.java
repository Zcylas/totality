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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * Block Breaking V2 Pass 4B in a real client and integrated world (real player, real server ticks): a summoned
 * lightning bolt deoxidizing damaged copper, a full Coral Block dying from vanilla's own SCHEDULED tick after its water
 * is removed, Sponge absorption and Concrete solidification from real neighbour updates, and Cauldron bucket/bottle
 * use returning the real items. Results go to {@code totality-block-breaking-pass4b-results.txt}.
 */
public class BlockBreakingPass4BClientGameTest implements FabricClientGameTest {

    private final List<String> results = new ArrayList<>();
    private int failures;

    private synchronized void check(String name, boolean ok, Object detail) {
        String line = (ok ? "PASS " : "FAIL ") + name + " — " + detail;
        results.add(line);
        System.out.println("[BlockBreakingPass4B] " + line);
        if (!ok) failures++;
    }

    private static Block block(String id) { return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)); }

    private static void strike(ServerLevel level, BlockPos pos, float damage) {
        BlockBreaking.applyImpact(level, new MiningImpact(pos, Direction.UP, Vec3.atCenterOf(pos), damage, 4,
                MiningSource.of(MiningSource.Kind.PLAYER_TOOL, null)));
    }

    private static String rec(ServerLevel level, BlockPos pos) {
        var e = BlockDamageStorage.get(level).get(pos, level.getBlockState(pos));
        return e == null ? "none" : e.block + " " + e.integrity + "/" + e.max;
    }

    private BlockPos base;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("time set day");
            world.getServer().runCommand("weather clear");
            world.getServer().runOnServer(this::setup);
            context.waitTicks(40);                                            // lightning acts during its ticks
            world.getServer().runOnServer(this::afterLightning);
            context.waitTicks(140);                                           // coral's scheduled death tick (60-100 ticks)
            world.getServer().runOnServer(this::afterCoral);
            // The bolt set fire around the copper (vanilla); clear it so the copper's crack overlay is visible.
            world.getServer().runCommand("fill " + (base.getX() - 2) + " " + base.getY() + " " + (base.getZ() - 2) + " "
                    + (base.getX() + 2) + " " + (base.getY() + 2) + " " + (base.getZ() + 2) + " air replace fire");
            shot(context, world, base, "p4b_01_lightning_deoxidized_copper_keeps_crack");
            shot(context, world, base.offset(2, 0, 0), "p4b_02_solidified_concrete_keeps_crack");
        }
        context.runOnClient(mc -> {
            try {
                Files.write(mc.gameDirectory.toPath().resolve("totality-block-breaking-pass4b-results.txt"), results);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
        if (failures > 0) throw new AssertionError(failures + " Block Breaking Pass 4B check(s) failed");
    }

    private void setup(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = (ServerLevel) player.level();
        base = player.blockPosition().offset(3, 4, 0);
        for (int x = -1; x < 9; x++) for (int z = -2; z < 5; z++) level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());

        // Lightning: damaged Oxidized Copper, then a real bolt summoned on top of it.
        BlockPos copper = base;
        level.setBlockAndUpdate(copper, block("oxidized_copper").defaultBlockState());
        strike(level, copper, 60f);                                                            // 90 / 150
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "summon lightning_bolt " + copper.getX() + ".5 " + (copper.getY() + 1) + " " + copper.getZ() + ".5");

        // Coral: damaged Tube Coral Block kept alive by water, then the water removed (vanilla schedules the death tick).
        BlockPos coral = base.offset(4, 0, 0);
        level.setBlockAndUpdate(coral.east(), Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(coral, Blocks.TUBE_CORAL_BLOCK.defaultBlockState());
        strike(level, coral, 10f);                                                             // 40 / 50
        level.setBlockAndUpdate(coral.east(), Blocks.STONE.defaultBlockState());

        // Sponge and Concrete Powder: real neighbour updates from placing water.
        BlockPos sponge = base.offset(7, 0, 0), powder = base.offset(2, 0, 0);
        level.setBlockAndUpdate(sponge, Blocks.SPONGE.defaultBlockState());
        strike(level, sponge, 25f);                                                            // 25 / 50
        level.setBlockAndUpdate(sponge.above(), Blocks.WATER.defaultBlockState());
        check("sponge.absorbs_in_place_keeping_25_of_50", level.getBlockState(sponge).is(Blocks.WET_SPONGE)
                && rec(level, sponge).equals("minecraft:wet_sponge 25.0/50.0") && level.getBlockState(sponge.above()).isAir(), rec(level, sponge));
        Block limePowder = Blocks.CONCRETE_POWDER.pick(DyeColor.LIME), lime = Blocks.CONCRETE.pick(DyeColor.LIME);
        level.setBlockAndUpdate(powder, limePowder.defaultBlockState());
        strike(level, powder, 30f);                                                            // 45 / 75
        level.setBlockAndUpdate(powder.north(), Blocks.WATER.defaultBlockState());
        check("concrete.solidifies_in_place_45_of_75_to_90_of_150", level.getBlockState(powder).is(lime)
                && rec(level, powder).equals("minecraft:lime_concrete 90.0/150.0"), rec(level, powder));

        // Cauldron with the real player: water bucket in, three bottles out (items really exchanged).
        BlockPos cauldron = base.offset(6, 0, -2);
        level.setBlockAndUpdate(cauldron, Blocks.CAULDRON.defaultBlockState());
        strike(level, cauldron, 30f);                                                          // 120 / 150
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cauldron).add(0, 0.5, 0), Direction.UP, cauldron, false);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WATER_BUCKET));
        level.getBlockState(cauldron).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        boolean bucketBack = player.getMainHandItem().is(Items.BUCKET);
        String filled = rec(level, cauldron);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GLASS_BOTTLE, 3));
        for (int i = 0; i < 3; i++) level.getBlockState(cauldron).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        int potions = player.getInventory().countItem(Items.POTION);
        check("cauldron.bucket_and_bottles_keep_120_of_150_and_exchange_items",
                filled.equals("minecraft:water_cauldron 120.0/150.0") && bucketBack && rec(level, cauldron).equals("minecraft:cauldron 120.0/150.0") && potions == 3,
                filled + " -> " + rec(level, cauldron) + " bucketBack=" + bucketBack + " potions=" + potions);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /** Real-world screenshot facing {@code target} from 2.5 blocks south, so the transformed block's crack overlay is visible. */
    private void shot(ClientGameTestContext context, TestSingleplayerContext world, BlockPos target, String name) {
        world.getServer().runCommand("tp @a " + (target.getX() + 0.5) + " " + target.getY() + " " + (target.getZ() + 3.5)
                + " facing " + (target.getX() + 0.5) + " " + (target.getY() + 0.5) + " " + (target.getZ() + 0.5));
        world.getServer().runOnServer(server -> BlockDamageStorage.syncTo(server.getPlayerList().getPlayers().getFirst()));
        context.waitTicks(20);
        context.setScreen(() -> null);                  // close the join-time Ancestry screen so the world is visible
        context.waitTicks(10);
        java.nio.file.Path file = context.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of(name).disableCounterPrefix());
        results.add("SHOT " + name + " -> " + file.getFileName());
    }

    private void afterLightning(MinecraftServer server) {
        ServerLevel level = server.overworld();
        check("lightning.summoned_bolt_deoxidizes_in_place_keeping_90_of_150", level.getBlockState(base).is(block("copper_block"))
                && rec(level, base).equals("minecraft:copper_block 90.0/150.0"), rec(level, base) + " " + level.getBlockState(base));
    }

    private void afterCoral(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos coral = base.offset(4, 0, 0);
        check("coral.scheduled_death_tick_keeps_40_of_50", level.getBlockState(coral).is(Blocks.DEAD_TUBE_CORAL_BLOCK)
                && rec(level, coral).equals("minecraft:dead_tube_coral_block 40.0/50.0"), rec(level, coral) + " " + level.getBlockState(coral));
    }
}
