package zcylas.totality.block.alchemy;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.harvest.HarvestHandler;
import zcylas.totality.api.ability.harvest.HarvestRegistry;
import zcylas.totality.api.ability.harvest.handler.MountainFlowerHarvestHandler;
import zcylas.totality.api.ability.impl.HarvestAbility;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.api.core.util.MountainFlowerBushBlock;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceTarget;
import zcylas.totality.api.rpg.resources.integration.PlayerBaselineResources;
import zcylas.totality.init.blocks.AlchemyBlocks;
import zcylas.totality.init.items.SKIngredientItems;
import zcylas.totality.item.food.TotalityFoodItem;
import zcylas.totality.networking.resource.BaselineResourceLifecycleEvents;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.List;
import java.util.function.Supplier;

import static zcylas.totality.api.core.util.MountainFlowerBushBlock.HARVESTED;

/**
 * Dev-environment-gated, opt-in (live-world) self-test of the Jueyun Chili and its plant (Creative Test I) on the
 * server, with fake survival players on a stage of its own (grass at y 100 in chunk 50, 50): registration and
 * classification, eating through the Totality Food resource, the two harvest states, placement and support,
 * right-click harvesting (exactly one chili, never from a picked plant, never twice for one fruit — also with two
 * players in the same tick), the Harvest ability, regrowth by random tick and bone meal, breaking without
 * duplication, and state persistence / network state-id round trips.
 */
public final class JueyunChiliVerification {

    private static final int CHUNK = 50;
    private static final int X0 = CHUNK * 16;
    private static final int FLOOR_Y = 100;

    private static int stage;
    private static int dueTick;

    private JueyunChiliVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!VerificationReporter.isDevEnvironment()) return;
            server.overworld().setChunkForced(CHUNK, CHUNK, true);
            stage = 1;
            dueTick = server.getTickCount() + 400;
        });
        ServerTickEvents.END_SERVER_TICK.register(JueyunChiliVerification::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (stage != 1 || server.getTickCount() < dueTick) return;
        stage = 0;
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "JueyunChiliVerification");
        try {
            run(server.overworld(), r);
        } finally {
            server.overworld().setChunkForced(CHUNK, CHUNK, false);
            r.summarize();
        }
    }

    private static void run(ServerLevel level, VerificationReporter r) {
        buildStage(level);
        ServerPlayer picker = TotalityFakePlayer.create(level, "ChiliPicker");
        picker.setGameMode(GameType.SURVIVAL);
        picker.setPos(X0 + 8.5, FLOOR_Y + 1, X0 + 1.5);
        JueyunChiliPlantBlock plant = AlchemyBlocks.JUEYUN_CHILI_PLANT;
        BlockState fruiting = plant.defaultBlockState().setValue(HARVESTED, false);
        BlockState picked = plant.defaultBlockState().setValue(HARVESTED, true);

        // ── Registration and the item ─────────────────────────────────────────
        check(r, "totality:jueyun_chili_plant (with a placeable block item) and totality:jueyun_chili are registered", () ->
                BuiltInRegistries.BLOCK.getValue(id("jueyun_chili_plant")) == plant
                        && BuiltInRegistries.ITEM.getValue(id("jueyun_chili_plant")) == plant.asItem()
                        && BuiltInRegistries.ITEM.getValue(id("jueyun_chili")) == SKIngredientItems.JUEYUN_CHILI);
        check(r, "the plant is a Mountain Flower Bush whose fruit is the Jueyun Chili", () ->
                plant instanceof MountainFlowerBushBlock && plant.getFlowerItem() == SKIngredientItems.JUEYUN_CHILI);
        ItemStack chili = new ItemStack(SKIngredientItems.JUEYUN_CHILI);
        check(r, "the chili is classified FOOD, INGREDIENT (in that order), edible, a TotalityFoodItem, stacks to 64", () ->
                ItemComponents.classificationsOf(chili).equals(List.of(ItemType.FOOD, ItemType.INGREDIENT))
                        && chili.has(DataComponents.FOOD) && chili.has(DataComponents.CONSUMABLE)
                        && SKIngredientItems.JUEYUN_CHILI instanceof TotalityFoodItem && chili.getMaxStackSize() == 64);
        ServerPlayer eater = TotalityFakePlayer.create(level, "ChiliEater");
        BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(eater);
        PlayerBaselineResources.reconcile(eater);
        PlayerResourceService.INSTANCE.set(eater, ResourceTarget.scalar(PlayerResourceIds.FOOD, 40),
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.ADMIN_COMMAND)));
        ItemStack bite = new ItemStack(SKIngredientItems.JUEYUN_CHILI, 2);
        SKIngredientItems.JUEYUN_CHILI.finishUsingItem(bite, level, eater);
        check(r, "eating one restores exactly 4 Food (40 -> 44) and uses one chili; the eat takes 32 ticks", () ->
                food(eater) == 44 && bite.getCount() == 1
                        && SKIngredientItems.JUEYUN_CHILI.getUseDuration(bite, eater) == 32);
        eater.discard();

        // ── Exactly two harvest states ────────────────────────────────────────
        check(r, "exactly two states, one boolean property 'harvested' (no crop age)", () ->
                plant.getStateDefinition().getPossibleStates().size() == 2
                        && plant.getStateDefinition().getProperties().size() == 1
                        && plant.getStateDefinition().getProperty("harvested") == HARVESTED);
        check(r, "a newly placed plant is picked (the bush default) and only a picked plant random-ticks", () ->
                plant.defaultBlockState() == picked && picked.isRandomlyTicking() && !fruiting.isRandomlyTicking());
        check(r, "wild-plant block properties: no collision, instant break, destroyed by pistons", () ->
                fruiting.getCollisionShape(level, BlockPos.ZERO).isEmpty() && fruiting.getDestroySpeed(level, BlockPos.ZERO) == 0.0F
                        && fruiting.getPistonPushReaction() == PushReaction.DESTROY);

        // ── Placement and support ─────────────────────────────────────────────
        BlockPos grass = new BlockPos(X0 + 2, FLOOR_Y, X0 + 3);
        picker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(plant.asItem(), 3));
        InteractionResult placed = use(picker, grass, Direction.UP);
        check(r, "a survival player places the plant on grass: a picked plant, one item used", () ->
                placed.consumesAction() && level.getBlockState(grass.above()) == picked && picker.getMainHandItem().getCount() == 2);
        BlockPos stone = new BlockPos(X0 + 4, FLOOR_Y, X0 + 12);
        InteractionResult onStone = use(picker, stone, Direction.UP);
        check(r, "it cannot be placed on stone (nothing placed, stack untouched)", () ->
                !level.getBlockState(stone.above()).is(plant) && picker.getMainHandItem().getCount() == 2 && !onStone.consumesAction());
        check(r, "it survives on grass, dirt, coarse dirt, moss and farmland (#supports_vegetation), not on stone, sand or air", () ->
                survivesOn(level, Blocks.GRASS_BLOCK) && survivesOn(level, Blocks.DIRT) && survivesOn(level, Blocks.COARSE_DIRT)
                        && survivesOn(level, Blocks.MOSS_BLOCK) && survivesOn(level, Blocks.FARMLAND)
                        && !survivesOn(level, Blocks.STONE) && !survivesOn(level, Blocks.SAND) && !survivesOn(level, Blocks.AIR));
        BlockPos loose = new BlockPos(X0 + 6, FLOOR_Y + 1, X0 + 3);
        level.setBlockAndUpdate(loose, fruiting);
        level.setBlockAndUpdate(loose.below(), Blocks.AIR.defaultBlockState());
        check(r, "removing its ground pops the plant, and a fruiting plant drops nothing (bush rule: no loot table)", () ->
                level.getBlockState(loose).isAir() && dropped(level, loose, SKIngredientItems.JUEYUN_CHILI) == 0
                        && dropped(level, loose, plant.asItem()) == 0);
        level.setBlockAndUpdate(loose.below(), Blocks.GRASS_BLOCK.defaultBlockState());

        // ── Right-click harvesting ────────────────────────────────────────────
        BlockPos bush = grass.above();
        level.setBlockAndUpdate(bush, fruiting);
        picker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult harvested = use(picker, bush, Direction.NORTH);
        check(r, "right-clicking a fruiting plant: success, exactly one Jueyun Chili, the plant stays and is picked", () ->
                harvested.consumesAction() && dropped(level, bush, SKIngredientItems.JUEYUN_CHILI) == 1
                        && level.getBlockState(bush) == picked);
        int extra = 0;
        for (int i = 0; i < 5; i++) {
            use(picker, bush, Direction.NORTH);
            extra += dropped(level, bush, SKIngredientItems.JUEYUN_CHILI) - 1;
        }
        int repeats = extra;
        check(r, "five more right-clicks on the picked plant produce no chili and leave it picked", () ->
                repeats == 0 && level.getBlockState(bush) == picked);
        clearDrops(level, bush);
        picker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        level.setBlockAndUpdate(bush, fruiting);
        use(picker, bush, Direction.NORTH);
        check(r, "holding an ordinary item harvests the same way (one chili)", () ->
                dropped(level, bush, SKIngredientItems.JUEYUN_CHILI) == 1 && level.getBlockState(bush) == picked);
        clearDrops(level, bush);
        picker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        ServerPlayer second = TotalityFakePlayer.create(level, "ChiliPicker2");
        second.setGameMode(GameType.SURVIVAL);
        second.setPos(X0 + 3.5, FLOOR_Y + 1, X0 + 1.5);
        level.setBlockAndUpdate(bush, fruiting);
        use(picker, bush, Direction.NORTH);
        use(second, bush, Direction.NORTH);
        use(picker, bush, Direction.NORTH);
        check(r, "two players right-clicking one fruiting plant in the same tick: one chili in total", () ->
                dropped(level, bush, SKIngredientItems.JUEYUN_CHILI) == 1 && level.getBlockState(bush) == picked);
        clearDrops(level, bush);
        second.discard();

        // ── The Harvest ability (existing Mountain Flower handler) ────────────
        HarvestHandler handler = HarvestRegistry.handlers().stream().filter(h -> h.canHarvest(fruiting)).findFirst().orElse(null);
        check(r, "the Harvest ability claims a fruiting plant through MountainFlowerHarvestHandler, never a picked one", () ->
                handler instanceof MountainFlowerHarvestHandler
                        && HarvestRegistry.handlers().stream().noneMatch(h -> h.canHarvest(picked)));
        level.setBlockAndUpdate(bush, fruiting);
        HarvestAbility ability = new HarvestAbility();
        ability.onActivate(picker, new AbilityContext(bush, fruiting, "Harvest"));
        ability.onActivate(picker, new AbilityContext(bush, fruiting, "Harvest"));
        check(r, "Harvest ability (no Green Thumb): one chili, plant picked; a stale second activation adds nothing", () ->
                dropped(level, bush, SKIngredientItems.JUEYUN_CHILI) == 1 && level.getBlockState(bush) == picked);
        clearDrops(level, bush);

        // ── Regrowth ──────────────────────────────────────────────────────────
        RandomSource random = RandomSource.create(50L);
        int ticks = 0;
        while (ticks < 5000 && level.getBlockState(bush) == picked) {
            level.getBlockState(bush).randomTick(level, bush, random);
            ticks++;
        }
        int regrowTicks = ticks;
        check(r, "random ticks regrow a picked plant to fruiting (bush 1-in-50 chance; took " + regrowTicks + " random ticks)", () ->
                level.getBlockState(bush) == fruiting);
        int before = dropped(level, bush, SKIngredientItems.JUEYUN_CHILI);
        for (int i = 0; i < 200; i++) level.getBlockState(bush).randomTick(level, bush, random);
        check(r, "a fruiting plant stays fruiting and never drops on its own", () ->
                level.getBlockState(bush) == fruiting && dropped(level, bush, SKIngredientItems.JUEYUN_CHILI) == before);
        ItemStack meal = new ItemStack(Items.BONE_MEAL, 8);
        boolean mealOnFull = BoneMealItem.growCrop(meal, level, bush);
        check(r, "bone meal does nothing to a fruiting plant (not a target, not used)", () ->
                !mealOnFull && meal.getCount() == 8);
        use(picker, bush, Direction.NORTH);
        clearDrops(level, bush);
        boolean mealOnPicked = BoneMealItem.growCrop(meal, level, bush);
        check(r, "bone meal regrows a picked plant at once and is used", () ->
                mealOnPicked && meal.getCount() == 7 && level.getBlockState(bush) == fruiting);

        // ── Breaking ──────────────────────────────────────────────────────────
        check(r, "neither state has drops by loot (identical to the Mountain Flower Bushes)", () ->
                Block.getDrops(fruiting, level, bush, null, picker, ItemStack.EMPTY).isEmpty()
                        && Block.getDrops(picked, level, bush, null, picker, ItemStack.EMPTY).isEmpty()
                        && Block.getDrops(AlchemyBlocks.RED_MOUNTAIN_FLOWER_BUSH.defaultBlockState().setValue(HARVESTED, false),
                        level, bush, null, picker, ItemStack.EMPTY).isEmpty());
        use(picker, bush, Direction.NORTH);
        boolean broke = picker.gameMode.destroyBlock(bush);
        check(r, "harvest then break in survival: one chili in total, the plant gone, nothing else dropped", () ->
                broke && level.getBlockState(bush).isAir() && dropped(level, bush, SKIngredientItems.JUEYUN_CHILI) == 1
                        && dropped(level, bush, plant.asItem()) == 0);
        clearDrops(level, bush);
        level.setBlockAndUpdate(bush, fruiting);
        boolean brokeFull = picker.gameMode.destroyBlock(bush);
        check(r, "breaking a fruiting plant in survival drops nothing (bush rule) — the fruit is only ever picked", () ->
                brokeFull && level.getBlockState(bush).isAir() && dropped(level, bush, SKIngredientItems.JUEYUN_CHILI) == 0);

        // ── Persistence and synchronisation ───────────────────────────────────
        check(r, "both states survive the chunk-save NBT round trip (NbtUtils write/read)", () -> {
            for (BlockState s : List.of(fruiting, picked)) {
                CompoundTag tag = NbtUtils.writeBlockState(s);
                if (NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), tag) != s) return false;
            }
            return true;
        });
        check(r, "both states have distinct network state ids that round-trip (what block-update packets carry)", () ->
                Block.getId(fruiting) != Block.getId(picked)
                        && Block.stateById(Block.getId(fruiting)) == fruiting && Block.stateById(Block.getId(picked)) == picked);
        picker.discard();
    }

    private static boolean survivesOn(ServerLevel level, Block ground) {
        BlockPos pos = new BlockPos(X0 + 12, FLOOR_Y + 1, X0 + 12);
        level.setBlock(pos.below(), ground.defaultBlockState(), Block.UPDATE_CLIENTS);
        boolean ok = AlchemyBlocks.JUEYUN_CHILI_PLANT.defaultBlockState().canSurvive(level, pos);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        return ok;
    }

    private static long food(ServerPlayer player) {
        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.FOOD);
        return result instanceof ResourceQueryResult.Success success ? success.snapshot().currentUnits() : -1;
    }

    private static InteractionResult use(ServerPlayer player, BlockPos pos, Direction face) {
        Vec3 hit = Vec3.atCenterOf(pos).relative(face, 0.5);
        return player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, pos, false));
    }

    private static void buildStage(ServerLevel level) {
        for (int x = X0; x < X0 + 16; x++) {
            for (int z = X0; z < X0 + 16; z++) {
                BlockState floor = z < X0 + 8 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
                level.setBlockAndUpdate(new BlockPos(x, FLOOR_Y - 1, z), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, FLOOR_Y, z), floor);
                int top = Math.max(FLOOR_Y + 4, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
                for (int y = FLOOR_Y + 1; y <= top; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(X0, FLOOR_Y - 2, X0, X0 + 16, FLOOR_Y + 40, X0 + 16)).forEach(ItemEntity::discard);
    }

    private static int dropped(ServerLevel level, BlockPos at, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(1.5), e -> e.getItem().is(item))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static void clearDrops(ServerLevel level, BlockPos at) {
        level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(1.5)).forEach(ItemEntity::discard);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, path);
    }

    private static void check(VerificationReporter r, String label, Supplier<Boolean> body) {
        try {
            r.check(label, body.get(), "");
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
