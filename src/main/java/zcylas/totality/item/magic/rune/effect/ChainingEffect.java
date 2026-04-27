package zcylas.totality.item.magic.rune.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractEffectRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.Map;
import java.util.Set;

public class ChainingEffect extends AbstractEffectRune {

    public static final ChainingEffect INSTANCE = new ChainingEffect();

    private static final int    BASE_MAX_ENTITIES = 3;
    private static final double BASE_ENTITY_DIST  = 8.0;
    private static final double BONUS_ENTITY_DIST = 4.0;

    private static final int    BASE_MAX_BLOCKS   = 8;
    private static final double BASE_BLOCK_DIST   = 1.75;

    private ChainingEffect() { super("chaining", "Chaining"); }

    @Override public int getManaCost() { return 300; }
    @Override public int getTier()     { return 3; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_CHAINING; }
    public String getDescription() { return "Chains remaining spell effects to nearby targets of the same type."; }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("aoe", "pierce", "sensitive");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("aoe",   "Increases the number of chained targets.");
        map.put("pierce","Increases the chain search distance.");
        map.put("sensitive", "Chains to any nearby entity, not just the same type.");
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel)) return;

        BlockState struck = level.getBlockState(hit.getBlockPos());
        int maxBlocks = BASE_MAX_BLOCKS + (int)(stats.getAoeRadius() * 2);
        double dist = BASE_BLOCK_DIST + stats.getPierceCount() * 1.0;
        int searchDist = (int) Math.ceil(dist);
        BlockPos origin = hit.getBlockPos();
        int count = 0;
        int chainingIndex = context.getFormula().getRunes().indexOf(this) + 1;

        for (BlockPos nearby : BlockPos.betweenClosed(
                origin.offset(-searchDist, -searchDist, -searchDist),
                origin.offset(searchDist, searchDist, searchDist))) {
            if (count >= maxBlocks) break;
            if (nearby.equals(origin)) continue;
            if (!stats.isSensitive() && !level.getBlockState(nearby).is(struck.getBlock())) continue;

            BlockHitResult chainedHit = new BlockHitResult(
                    Vec3.atCenterOf(nearby), hit.getDirection(),
                    nearby.immutable(), false);

            FormulaContext childContext = context.makeChildContext(chainingIndex);
            new FormulaResolver(childContext).onResolveEffect(level, chainedHit);
            count++;
        }
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel)) return;

        Entity struck = hit.getEntity();
        int maxEntities = BASE_MAX_ENTITIES + (int)(stats.getAoeRadius() * 2);
        double dist = BASE_ENTITY_DIST + stats.getPierceCount() * BONUS_ENTITY_DIST;

        Vec3 pos = struck.position();
        AABB box = new AABB(
                pos.x - dist, pos.y - dist, pos.z - dist,
                pos.x + dist, pos.y + dist, pos.z + dist);

        int chainingIndex = context.getFormula().getRunes().indexOf(this) + 1;
        int count = 0;
        for (Entity nearby : level.getEntities(struck, box, Entity::isAlive)) {
            if (count >= maxEntities) break;
            if (nearby == caster) continue;
            if (!stats.isSensitive() && nearby.getType() != struck.getType()) continue;

            FormulaContext childContext = context.makeChildContext(chainingIndex);
            new FormulaResolver(childContext).onResolveEffect(level, new EntityHitResult(nearby));
            count++;
        }
    }
}