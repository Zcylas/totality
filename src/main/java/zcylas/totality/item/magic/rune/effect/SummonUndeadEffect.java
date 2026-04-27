package zcylas.totality.item.magic.rune.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractEffectRune;
import zcylas.totality.api.mana.PlayerManaManager;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.entity.magic.SummonSkeletonEntity;
import zcylas.totality.init.ModEffects;

import java.util.Map;
import java.util.Set;

public class SummonUndeadEffect extends AbstractEffectRune {

    public static final SummonUndeadEffect INSTANCE = new SummonUndeadEffect();

    private static final int BASE_COUNT = 3;
    private static final int BASE_LIFE_TICKS = 20 * 30; // 30 seconds base

    private SummonUndeadEffect() { super("summon_undead", "Summon Undead"); }

    @Override public int getManaCost() { return 50; }
    @Override public int getTier() { return 3; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_SUMMON_UNDEAD; }

    @Override
    public String getDescription() {
        return "Summons skeleton warriors to fight for you. Amplify upgrades their weapons, Pierce makes them archers, Split summons more.";
    }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("amplify", "pierce", "split", "extend_time", "reduce_time");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("amplify", "Upgrades the skeleton's weapon.");
        map.put("pierce", "Summons archers with bows instead.");
        map.put("split",  "Summons additional skeletons.");
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        spawnSkeletons(hit.getEntity().position(), level, caster, stats);
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {
        spawnSkeletons(Vec3.atCenterOf(hit.getBlockPos()), level, caster, stats);
    }

    private void spawnSkeletons(Vec3 pos, Level level, LivingEntity caster, FormulaStats stats) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Block re-summoning while sickness is active
        if (caster.hasEffect(ModEffects.SUMMONING_SICKNESS)) return;

        int baseTicks = 20 * 15;
        int extendTicks = 20 * 10;
        int ticks = (int)(baseTicks + extendTicks * stats.getDurationModifier());
        if (ticks <= 0) return;

        int count = BASE_COUNT + stats.getSplitCount();
        boolean archer = stats.getPierceCount() > 0;
        ItemStack weapon = getWeapon(stats, archer, serverLevel);

        BlockPos blockPos = BlockPos.containing(pos);

        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = blockPos.offset(
                    -2 + caster.getRandom().nextInt(5), 2,
                    -2 + caster.getRandom().nextInt(5));

            SummonSkeletonEntity skeleton = new SummonSkeletonEntity(level, caster, weapon.copy());
            skeleton.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            skeleton.finalizeSpawn(serverLevel,
                    serverLevel.getCurrentDifficultyAt(spawnPos),
                    EntitySpawnReason.MOB_SUMMONED, null);
            skeleton.setLimitedLife(ticks);
            serverLevel.addFreshEntity(skeleton);
        }

        // Apply summoning sickness for same duration as skeletons
        caster.addEffect(new MobEffectInstance(ModEffects.SUMMONING_SICKNESS,
                ticks));
    }

    private ItemStack getWeapon(FormulaStats stats, boolean archer, ServerLevel level) {
        int amp = stats.getAmpCount();
        if (archer) {
            ItemStack bow = new ItemStack(Items.BOW);
            if (amp > 0) {
                bow.enchant(
                        level.registryAccess()
                                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.POWER),
                        Math.max(4, amp) - 1);
            }
            return bow;
        }
        if (amp >= 3) return new ItemStack(Items.NETHERITE_AXE);
        if (amp > 2)  return new ItemStack(Items.NETHERITE_SWORD);
        if (amp > 1)  return new ItemStack(Items.DIAMOND_SWORD);
        return new ItemStack(Items.IRON_SWORD);
    }
}