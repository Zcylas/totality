package zcylas.totality.item.magic.rune.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import zcylas.totality.api.magic.grimoire.context.FormulaContext;
import zcylas.totality.api.magic.grimoire.context.FormulaResolver;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractEffectRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.Map;
import java.util.Set;

public class HealEffect extends AbstractEffectRune {

    public static final HealEffect INSTANCE = new HealEffect();

    private static final float BASE_HEAL = 3.0f;
    private static final float AMP_HEAL  = 3.0f;

    private HealEffect() { super("heal", "Heal"); }

    @Override public int getManaCost() { return 50; }
    @Override public int getTier()     { return 2; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_HEAL; }

    @Override
    public String getDescription() {
        return "Heals the target. Deals magic damage to undead instead.";
    }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("amplify", "dampen", "fortune", "randomize");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("amplify",   "Increases healing done, or damage dealt to undead.");
        map.put("dampen",    "Decreases healing done, or damage dealt to undead.");
        map.put("fortune",   "Increases looting on undead kills.");
        map.put("randomize", "Randomizes the amount of healing done.");
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        if (!(hit.getEntity() instanceof LivingEntity target)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (target.isRemoved() || target.getHealth() <= 0) return;

        float amount = BASE_HEAL + AMP_HEAL * stats.getAmpCount();
        if (stats.isRandomized()) {
            amount = (float)(Math.random() * amount * 2);
        }
        amount = Math.max(0.5f, amount);

        // Undead entities take damage instead of being healed
        if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .wrapAsHolder(target.getType())
                .is(EntityTypeTags.UNDEAD)) {
            target.hurtServer(serverLevel,
                    serverLevel.damageSources().magic(), amount);
        } else {
            if (target instanceof Player player) {
                player.causeFoodExhaustion(2.5f);
            }
            target.heal(amount);
        }
    }
}