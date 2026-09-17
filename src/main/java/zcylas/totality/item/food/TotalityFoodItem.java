package zcylas.totality.item.food;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceAmount;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.food.FoodVanillaCompatibilityBridge;

/**
 * The shared, reusable base for every ordinary Totality-authored edible item. Restores an authored
 * amount directly to the true, authoritative {@code totality:food} resource (0-100) on a fixed,
 * authored consume duration — never vanilla {@code FoodData} nutrition. See
 * {@code TOTALITY_FOOD_0_100_AND_TOTALITY_FOOD_ITEM_IMPLEMENTATION_REPORT_2026-09-17.md}.
 *
 * <p>Deliberately always eatable regardless of current Food (canonical task decision: "being full
 * does not mean a person is physically incapable of eating") — achieved by attaching a
 * zero-nutrition, {@code canAlwaysEat = true} {@link FoodProperties}. {@code canAlwaysEat = true}
 * makes {@code Consumable#canConsume}/{@code Player#canEat} return {@code true} unconditionally, and
 * merely having a {@code FoodProperties} component at all (regardless of its values) is what
 * satisfies {@code InventoryActionHandler}'s {@code stack.has(DataComponents.FOOD)} gate for the
 * inventory quick-use path — both without either caller needing to know anything about Food.
 *
 * <p><b>Corrected 2026-09-17 (Pizza/Saturation correction pass):</b> zero nutrition does <b>not</b>
 * by itself make vanilla's own {@code FoodData#eat}/{@code #add} a no-op — the real decompiled 26.2
 * {@code add(int, float)} still clamps {@code saturationLevel} down to the *current* vanilla
 * {@code foodLevel} mirror even for a literal {@code add(0, 0.0F)}, which was silently destroying
 * real Saturation on every Totality food consumption whenever the mirror happened to be lower than
 * it. {@link FoodVanillaCompatibilityBridge#interceptFoodDataEat} now skips vanilla's own {@code eat}
 * entirely for a zero intended nutrition (see that method's own Javadoc), so this class's temporary
 * Saturation contribution below is the only Saturation effect a Totality food item causes.
 *
 * <p>Restoration clamps at the player's resolved Food maximum (baseline 100, see
 * {@code FoodMaximumResolver}) via {@link PlayerResourceService#restore} — eating never creates Food
 * overflow, even when the authored amount exceeds remaining capacity; the item is still consumed
 * regardless of how much Food was actually restored.
 *
 * <p><b>Temporary authored Saturation ({@code temporarySaturationRestoration}, added in the same
 * correction pass):</b> vanilla Saturation remains only a <i>temporary</i> compatibility metabolic
 * buffer until the future Diet/Metabolism/Metabolic Reserve system exists — this is not part of any
 * canonical Diet API, is not exposed as a Generic Resource, and is deliberately easy to remove or
 * replace wholesale later. Applied directly to {@code FoodData#setSaturation}, added to whatever
 * Saturation the player already has (never replacing it), and clamped into vanilla's own real
 * invariant range {@code [0, foodLevel]} — the same ceiling {@code FoodData#add} itself enforces.
 * The mirror is explicitly re-synced ({@link FoodVanillaCompatibilityBridge#resyncMirrorIfStale})
 * immediately before computing that ceiling, specifically so the clamp is evaluated against the
 * <i>freshly post-eating</i> mirror (reflecting the Food this same call just restored), never the
 * stale pre-eating one — clamping a Pizza's {@code +12.0} against a stale {@code 0} mirror (true Food
 * 0 before eating) would incorrectly floor the authored Saturation to {@code 0} instead of the
 * correct post-eating ceiling.
 *
 * <p>Deliberately does not implement the future fast-eating/risk concept (authored consume duration
 * accelerated by Skills/Abilities/Species/items, with consequences for eating too fast) — this base
 * only guarantees an authored, per-food-item duration exists to accelerate later; see the
 * implementation report's "Future fast-eating system" section.
 */
public class TotalityFoodItem extends Item {

    private final long foodRestoration;
    private final float temporarySaturationRestoration;

    public TotalityFoodItem(Item.Properties properties, long foodRestoration,
                             float temporarySaturationRestoration, float consumeSeconds) {
        super(properties.food(
                new FoodProperties(0, 0.0F, true),
                Consumable.builder()
                        .consumeSeconds(consumeSeconds)
                        .animation(ItemUseAnimation.EAT)
                        .build()));
        this.foodRestoration = foodRestoration;
        this.temporarySaturationRestoration = temporarySaturationRestoration;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            PlayerResourceService.INSTANCE.restore(player,
                    ResourceAmount.scalar(PlayerResourceIds.FOOD, foodRestoration),
                    ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.FOOD_RESTORE)));
            if (temporarySaturationRestoration > 0.0F) {
                FoodVanillaCompatibilityBridge.resyncMirrorIfStale(player);
                FoodData foodData = player.getFoodData();
                float ceiling = foodData.getFoodLevel();
                float newSaturation = Mth.clamp(
                        foodData.getSaturationLevel() + temporarySaturationRestoration, 0.0F, ceiling);
                foodData.setSaturation(newSaturation);
            }
        }
        return result;
    }
}
