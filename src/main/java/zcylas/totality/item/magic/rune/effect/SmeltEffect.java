package zcylas.totality.item.magic.rune.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import zcylas.totality.api.magic.grimoire.context.FormulaContext;
import zcylas.totality.api.magic.grimoire.context.FormulaResolver;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractEffectRune;
import zcylas.totality.api.magic.grimoire.util.SpellUtil;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SmeltEffect extends AbstractEffectRune {

    public static final SmeltEffect INSTANCE = new SmeltEffect();

    private SmeltEffect() {
        super("smelt", "Smelt");
    }

    @Override
    public int getManaCost() { return 100; }

    @Override
    public int getTier() { return 2; }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_SMELT; }

    @Override
    public String getDescription() {
        return "Smelts blocks and items in the world."; }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("amplify", "aoe", "dampen", "pierce", "sensitive");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("amplify",   "Uses blasting recipes instead of smelting.");
        map.put("aoe",       "Increases the area and number of items smelted.");
        map.put("dampen",    "Uses smoking recipes instead of smelting.");
        map.put("pierce",    "Increases the number of items smelted.");
        map.put("sensitive", "Only smelts items on the ground, not blocks.");
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        double radius   = 1.0 + stats.getAoeRadius();
        int maxItems    = (int) Math.round(4 * (1 + stats.getAoeRadius() + stats.getPierceCount()));

        if (stats.isSensitive()) {
            // Only smelt item entities nearby
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(hit.getBlockPos()).inflate(radius));
            smeltItems(level, items, maxItems, stats);
            return;
        }

        // Smelt blocks in AOE
        List<BlockPos> blocks = SpellUtil.calcAoeBlocks(
                caster, hit.getBlockPos(), hit, stats.getAoeRadius());

        for (BlockPos pos : blocks) {
            smeltBlock(serverLevel, pos, caster, stats);
        }
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        if (!(level instanceof ServerLevel)) return;

        double radius = 1.0 + stats.getAoeRadius();
        int maxItems  = (int) Math.round(4 * (1 + stats.getAoeRadius() + stats.getPierceCount()));

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(hit.getEntity().blockPosition()).inflate(radius));
        smeltItems(level, items, maxItems, stats);
    }

    private void smeltBlock(ServerLevel level, BlockPos pos,
                            LivingEntity caster, FormulaStats stats) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        ItemStack blockAsItem = new ItemStack(state.getBlock().asItem());
        if (blockAsItem.isEmpty()) return;

        Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getServer()
                .getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING,
                        new SingleRecipeInput(blockAsItem), level);

        if (recipe.isPresent()) {
            ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(blockAsItem));
            if (result.isEmpty()) return;

            if (result.getItem() instanceof BlockItem blockItem) {
                level.setBlockAndUpdate(pos, blockItem.getBlock().defaultBlockState());
            } else {
                level.removeBlock(pos, false);
                level.addFreshEntity(new ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        result));
                level.updateNeighborsAt(pos, state.getBlock());
            }
        }
    }

    private void smeltItems(Level level, List<ItemEntity> itemEntities,
                            int maxItems, FormulaStats stats) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        int smelted = 0;

        for (ItemEntity itemEntity : itemEntities) {
            if (smelted >= maxItems) break;
            if (itemEntity.isRemoved()) continue;

            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) continue;

            Optional<RecipeHolder<SmeltingRecipe>> recipe = serverLevel.getServer()
                    .getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING,
                            new SingleRecipeInput(stack), serverLevel);

            if (recipe.isPresent()) {
                ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(stack));
                if (result.isEmpty()) continue;

                while (smelted < maxItems && !stack.isEmpty()) {
                    stack.shrink(1);
                    level.addFreshEntity(new ItemEntity(level,
                            itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(),
                            result.copy()));
                    smelted++;
                }
                if (stack.isEmpty()) itemEntity.discard();
                else itemEntity.setItem(stack);
            }
        }
    }

    private int getRequiredHarvestLevel(BlockState state) {
        if (state.is(net.minecraft.tags.BlockTags.NEEDS_DIAMOND_TOOL)) return 3;
        if (state.is(net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL))    return 2;
        if (state.is(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL))   return 1;
        return 0;
    }
}