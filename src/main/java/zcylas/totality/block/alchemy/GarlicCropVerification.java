package zcylas.totality.block.alchemy;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.Totality;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.init.blocks.AlchemyBlocks;
import zcylas.totality.init.items.SKIngredientItems;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Dev-environment-gated, opt-in (live-world) self-test of the Garlic crop (Creative Test E2) on the server, with a fake
 * survival player on a stage of its own (farmland rows at y 100 in chunk 44, 44): registration, planting a Clove on
 * farmland (and not elsewhere), growth through all eight ages, bone meal, the drops (immature: one Clove; mature: Garlic,
 * with Fortune adding Garlic — sampled), a real survival harvest, the 1 Garlic to 4 Cloves recipe, the renewable loop,
 * and dry farmland under the crop staying farmland.
 */
public final class GarlicCropVerification {

    private static final int CHUNK = 44;
    private static final int X0 = CHUNK * 16;
    private static final int FLOOR_Y = 100;
    private static final int SAMPLES = 4000;

    private static int stage;
    private static int dueTick;

    private GarlicCropVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!VerificationReporter.isDevEnvironment()) return;
            server.overworld().setChunkForced(CHUNK, CHUNK, true);
            stage = 1;
            dueTick = server.getTickCount() + 400;
        });
        ServerTickEvents.END_SERVER_TICK.register(GarlicCropVerification::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (stage != 1 || server.getTickCount() < dueTick) return;
        stage = 0;
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "GarlicCropVerification");
        try {
            run(server.overworld(), r);
        } finally {
            server.overworld().setChunkForced(CHUNK, CHUNK, false);
            r.summarize();
        }
    }

    private static void run(ServerLevel level, VerificationReporter r) {
        buildStage(level);
        ServerPlayer farmer = TotalityFakePlayer.create(level, "GarlicFarmer");
        farmer.setGameMode(GameType.SURVIVAL);
        farmer.setPos(X0 + 8.5, FLOOR_Y + 1, X0 + 1.5);
        CropBlock crop = AlchemyBlocks.GARLIC_CROP;

        // ── Registration ──────────────────────────────────────────────────────
        check(r, "totality:garlic_crop and totality:garlic_clove are registered; the Clove plants the crop", () ->
                BuiltInRegistries.BLOCK.getValue(id("garlic_crop")) == crop
                        && BuiltInRegistries.ITEM.getValue(id("garlic_clove")) == SKIngredientItems.GARLIC_CLOVE
                        && SKIngredientItems.GARLIC_CLOVE.getBlock() == crop
                        && crop.defaultBlockState().getCloneItemStack(level, BlockPos.ZERO, false).is(SKIngredientItems.GARLIC_CLOVE));
        check(r, "eight ages (0-7); the existing Garlic item is unchanged and has no block", () ->
                crop.getMaxAge() == 7 && !((Item) SKIngredientItems.GARLIC instanceof BlockItem)
                        && BuiltInRegistries.ITEM.getValue(id("garlic")) == SKIngredientItems.GARLIC);
        ItemStack cloveStack = new ItemStack(SKIngredientItems.GARLIC_CLOVE);
        check(r, "the Clove is classified SEED, INGREDIENT, FOOD (in that order) and is edible", () ->
                ItemComponents.classificationsOf(cloveStack).equals(List.of(ItemType.SEED, ItemType.INGREDIENT, ItemType.FOOD))
                        && cloveStack.has(DataComponents.FOOD) && cloveStack.has(DataComponents.CONSUMABLE));
        check(r, "crop tags: minecraft:crops and minecraft:maintains_farmland", () ->
                crop.defaultBlockState().is(BlockTags.CROPS) && crop.defaultBlockState().is(BlockTags.MAINTAINS_FARMLAND));

        // ── Planting ──────────────────────────────────────────────────────────
        BlockPos soil = new BlockPos(X0 + 2, FLOOR_Y, X0 + 3);
        check(r, "the stage is open to the sky (crops need light 8+ or open sky)", () -> level.canSeeSky(soil.above()));
        farmer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SKIngredientItems.GARLIC_CLOVE, 3));
        InteractionResult planted = use(farmer, soil);
        check(r, "a survival player plants a Clove on farmland: age 0 garlic, one Clove used", () ->
                planted.consumesAction() && level.getBlockState(soil.above()).is(crop)
                        && crop.getAge(level.getBlockState(soil.above())) == 0 && farmer.getMainHandItem().getCount() == 2);
        BlockPos dirt = new BlockPos(X0 + 2, FLOOR_Y, X0 + 12);
        InteractionResult onDirt = use(farmer, dirt);
        check(r, "a Clove cannot be planted on dirt (nothing placed, nothing used)", () ->
                !onDirt.consumesAction() || !level.getBlockState(dirt.above()).is(crop));
        check(r, "... and the stack is untouched", () -> farmer.getMainHandItem().getCount() == 2);
        check(r, "garlic cannot survive off farmland", () -> !crop.defaultBlockState().canSurvive(level, dirt.above()));

        // ── Growth through all stages ─────────────────────────────────────────
        BlockPos grow = soil.above();
        RandomSource random = RandomSource.create(44L);
        List<Integer> seen = new ArrayList<>();
        seen.add(0);
        for (int i = 0; i < 20000 && level.getBlockState(grow).is(crop) && crop.getAge(level.getBlockState(grow)) < 7; i++) {
            level.getBlockState(grow).randomTick(level, grow, random);
            int age = crop.getAge(level.getBlockState(grow));
            if (age != seen.getLast()) seen.add(age);
        }
        check(r, "random ticks grow it one age at a time through 0-7 " + seen, () ->
                seen.equals(List.of(0, 1, 2, 3, 4, 5, 6, 7)));
        check(r, "fully grown it stops: no bone meal target at age 7", () ->
                crop.isMaxAge(level.getBlockState(grow)) && !crop.isValidBonemealTarget(level, grow, level.getBlockState(grow)));

        // ── Bone meal ─────────────────────────────────────────────────────────
        BlockPos boned = new BlockPos(X0 + 4, FLOOR_Y + 1, X0 + 3);
        level.setBlockAndUpdate(boned, crop.getStateForAge(0));
        ItemStack meal = new ItemStack(Items.BONE_MEAL, 64);
        boolean grew = BoneMealItem.growCrop(meal, level, boned);
        check(r, "bone meal advances it (vanilla crop bonus 2-5 ages) and is used", () ->
                grew && crop.getAge(level.getBlockState(boned)) >= 2 && meal.getCount() == 63);
        for (int i = 0; i < 10 && !crop.isMaxAge(level.getBlockState(boned)); i++) BoneMealItem.growCrop(meal, level, boned);
        check(r, "repeated bone meal reaches maturity", () -> crop.isMaxAge(level.getBlockState(boned)));

        // ── Drops ─────────────────────────────────────────────────────────────
        Holder<Enchantment> fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        for (int age = 0; age < 7; age++) {
            List<ItemStack> drops = Block.getDrops(crop.getStateForAge(age), level, grow, null, farmer, fortuneHoe(fortune, 3));
            int finalAge = age;
            check(r, "age " + age + " (immature, even with Fortune III): exactly one Clove, no Garlic", () ->
                    total(drops, SKIngredientItems.GARLIC_CLOVE) == 1 && total(drops, SKIngredientItems.GARLIC) == 0
                            && drops.size() == 1 && finalAge < 7);
        }
        List<ItemStack> mature = Block.getDrops(crop.getStateForAge(7), level, grow, null, farmer, ItemStack.EMPTY);
        check(r, "mature, bare hand: exactly one Garlic and no Clove", () ->
                total(mature, SKIngredientItems.GARLIC) == 1 && total(mature, SKIngredientItems.GARLIC_CLOVE) == 0 && mature.size() == 1);
        for (int levelF = 0; levelF <= 3; levelF++) {
            int[] histogram = new int[5];
            long sum = 0;
            boolean clean = true;
            for (int i = 0; i < SAMPLES; i++) {
                List<ItemStack> drops = Block.getDrops(crop.getStateForAge(7), level, grow, null, farmer, fortuneHoe(fortune, levelF));
                int garlic = total(drops, SKIngredientItems.GARLIC);
                clean &= total(drops, SKIngredientItems.GARLIC_CLOVE) == 0;
                if (garlic < 1 || garlic > 1 + levelF) clean = false;
                else histogram[garlic]++;
                sum += garlic;
            }
            double mean = sum / (double) SAMPLES;
            double expected = 1.0 + levelF * (4.0 / 7.0);
            boolean ok = clean && Math.abs(mean - expected) < 0.06;
            int shown = levelF;
            check(r, "Fortune " + shown + ": Garlic = 1 + Binomial(" + shown + ", 4/7) — " + SAMPLES + " samples, mean "
                    + String.format("%.3f", mean) + " (expected " + String.format("%.3f", expected) + "), counts 1..4 = "
                    + histogram[1] + "/" + histogram[2] + "/" + histogram[3] + "/" + histogram[4] + ", never a Clove", () -> ok);
        }

        // ── A real survival harvest ───────────────────────────────────────────
        BlockPos young = new BlockPos(X0 + 6, FLOOR_Y + 1, X0 + 3);
        level.setBlockAndUpdate(young, crop.getStateForAge(3));
        farmer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        boolean brokeYoung = farmer.gameMode.destroyBlock(young);
        check(r, "breaking an immature crop in survival drops one Clove", () ->
                brokeYoung && level.getBlockState(young).isAir() && dropped(level, young, SKIngredientItems.GARLIC_CLOVE) == 1
                        && dropped(level, young, SKIngredientItems.GARLIC) == 0);
        clearDrops(level, young);
        boolean brokeMature = farmer.gameMode.destroyBlock(grow);
        check(r, "breaking the mature crop in survival drops one Garlic (bare hand)", () ->
                brokeMature && dropped(level, grow, SKIngredientItems.GARLIC) == 1 && dropped(level, grow, SKIngredientItems.GARLIC_CLOVE) == 0);
        clearDrops(level, grow);

        // ── Crafting and the renewable loop ───────────────────────────────────
        MinecraftServer server = level.getServer();
        for (int slot = 0; slot < 4; slot++) {
            List<ItemStack> grid = new ArrayList<>(List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
            grid.set(slot, new ItemStack(SKIngredientItems.GARLIC));
            CraftingInput input = CraftingInput.of(2, 2, grid);
            ItemStack result = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                    .map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
            int shownSlot = slot;
            check(r, "crafting: one Garlic (grid slot " + shownSlot + ") makes four Garlic Cloves", () ->
                    result.is(SKIngredientItems.GARLIC_CLOVE) && result.getCount() == 4);
        }
        CraftingInput cloves = CraftingInput.of(2, 2, List.of(new ItemStack(SKIngredientItems.GARLIC_CLOVE), new ItemStack(SKIngredientItems.GARLIC_CLOVE),
                new ItemStack(SKIngredientItems.GARLIC_CLOVE), new ItemStack(SKIngredientItems.GARLIC_CLOVE)));
        check(r, "no reverse recipe: four Cloves do not craft Garlic", () ->
                server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, cloves, level).isEmpty());
        BlockPos replant = new BlockPos(X0 + 8, FLOOR_Y, X0 + 3);
        farmer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SKIngredientItems.GARLIC_CLOVE, 4));
        InteractionResult again = use(farmer, replant);
        check(r, "renewable: a crafted Clove replants on farmland", () ->
                again.consumesAction() && level.getBlockState(replant.above()).is(crop) && farmer.getMainHandItem().getCount() == 3);

        // ── Dry farmland under garlic stays farmland ──────────────────────────
        BlockPos dry = new BlockPos(X0 + 10, FLOOR_Y, X0 + 3);
        level.setBlockAndUpdate(dry.above(), crop.getStateForAge(2));
        level.setBlockAndUpdate(dry, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 0));
        for (int i = 0; i < 20; i++) level.getBlockState(dry).randomTick(level, dry, random);
        check(r, "dry farmland under garlic is not turned to dirt; the crop stays", () ->
                level.getBlockState(dry).is(Blocks.FARMLAND) && level.getBlockState(dry.above()).is(crop));
        farmer.discard();
    }

    private static InteractionResult use(ServerPlayer player, BlockPos soil) {
        Vec3 hit = Vec3.atCenterOf(soil).add(0, 0.5, 0);
        return player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, soil, false));
    }

    private static ItemStack fortuneHoe(Holder<Enchantment> fortune, int levelF) {
        ItemStack hoe = new ItemStack(Items.IRON_HOE);
        if (levelF > 0) hoe.enchant(fortune, levelF);
        return hoe;
    }

    private static void buildStage(ServerLevel level) {
        for (int x = X0; x < X0 + 16; x++) {
            for (int z = X0; z < X0 + 16; z++) {
                BlockState floor = z < X0 + 8 ? Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE)
                        : Blocks.DIRT.defaultBlockState();
                level.setBlockAndUpdate(new BlockPos(x, FLOOR_Y - 1, z), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, FLOOR_Y, z), floor);
                // clear up to the world's surface: a crop needs light 8+ or open sky, and the random verification
                // world can have terrain over the stage (it did once: planting was refused)
                int top = Math.max(FLOOR_Y + 4, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
                for (int y = FLOOR_Y + 1; y <= top; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static int total(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
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
