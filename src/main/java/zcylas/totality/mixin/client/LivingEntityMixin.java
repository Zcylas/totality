package zcylas.totality.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import zcylas.totality.api.combat.damage.DamageResistanceComponent;
import zcylas.totality.api.combat.damage.DamageResistanceHolder;
import zcylas.totality.api.combat.condition.ConditionComponent;
import zcylas.totality.api.combat.condition.ConditionHolder;
import zcylas.totality.api.mob.stats.MobCombatStats;
import zcylas.totality.api.mob.stats.MobCombatStatsHolder;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.api.rpg.ancestry.ClientAncestryManager;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements DamageResistanceHolder, ConditionHolder, MobCombatStatsHolder {

    @Unique
    private final DamageResistanceComponent totality$damageResistances = new DamageResistanceComponent();

    @Unique
    private final ConditionComponent totality$conditions = new ConditionComponent();

    @Unique
    private final MobCombatStats totality$mobCombatStats = new MobCombatStats();


    @Override
    public DamageResistanceComponent totality$getDamageResistances() {
        return totality$damageResistances;
    }

    @Override
    public ConditionComponent totality$getConditions() {
        return totality$conditions;
    }

    @Override
    public MobCombatStats totality$getMobCombatStats() {
        return totality$mobCombatStats;
    }

    @ModifyReturnValue(method = "getScale", at = @At("RETURN"))
    private float totality$scaleForAncestry(float original) {
        if (!((LivingEntity)(Object)this instanceof Player player)) return original;

        float scale;
        if (player.level().isClientSide()) {
            if (!player.equals(net.minecraft.client.Minecraft.getInstance().player)) return original;
            scale = ClientAncestryManager.getHeightScale();
        } else {
            if (!(player instanceof ServerPlayer serverPlayer)) return original;
            scale = AncestryComponents.get(serverPlayer).getHeightScale();
        }

        return scale != 1.0f ? original * scale : original;
    }
}