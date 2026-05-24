package zcylas.totality.api.magic.grimoire.damage;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public class SpellDamageSource extends DamageSource {

    private int luckLevel = 0;

    public SpellDamageSource(Holder<DamageType> type) {
        super(type);
    }

    public SpellDamageSource(Holder<DamageType> type, @Nullable Entity entity) {
        super(type, entity);
    }

    public SpellDamageSource(Holder<DamageType> type, @Nullable Entity direct, @Nullable Entity cause) {
        super(type, direct, cause);
    }

    public int getLuckLevel() { return luckLevel; }
    public void setLuckLevel(int luckLevel) { this.luckLevel = luckLevel; }
}