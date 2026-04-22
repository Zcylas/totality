package zcylas.totality.item.magic.rune.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractEffectRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class PickupEffect extends AbstractEffectRune {

    public static final PickupEffect INSTANCE = new PickupEffect();

    private PickupEffect() {
        super("pickup", "Pickup");
    }

    @Override
    public int getManaCost() { return 10; }

    @Override
    public int getTier() { return 1; }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_PICKUP; }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {
        pickup(hit.getLocation(), level, caster, stats);
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        pickup(hit.getLocation(), level, caster, stats);
    }

    private void pickup(Vec3 location, Level level, LivingEntity caster, FormulaStats stats) {
        if (!(level instanceof ServerLevel)) return;
        if (!(caster instanceof Player player)) return;

        double radius = 2.0 + stats.getAoeRadius();
        AABB box = new AABB(
                location.subtract(radius, radius, radius),
                location.add(radius, radius, radius));

        // Pick up item entities
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);
        for (ItemEntity itemEntity : items) {
            if (itemEntity.isRemoved()) continue;
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) continue;

            // Try to add to player inventory
            ItemStack remaining = addToInventory(player, stack.copy());
            if (remaining.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(remaining);
            }
        }

        // Pick up XP orbs
        if (caster instanceof ServerPlayer serverPlayer) {
            List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, box);
            for (ExperienceOrb orb : orbs) {
                if (orb.isRemoved()) continue;
                serverPlayer.giveExperiencePoints(orb.getValue());
                orb.discard();
            }
        }
    }

    /**
     * Tries to add a stack to the player's inventory.
     * Returns whatever couldn't fit.
     */
    private ItemStack addToInventory(Player player, ItemStack stack) {
        if (player.getInventory().add(stack)) {
            return ItemStack.EMPTY;
        }
        return stack;
    }

    public String getDescription() { return "Picks up nearby items and XP orbs."; }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("aoe", "pierce");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("aoe",   "Increases the pickup radius.");
        map.put("pierce", "Increases the number of items picked up.");
    }
}