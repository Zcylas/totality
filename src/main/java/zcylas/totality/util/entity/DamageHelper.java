package zcylas.totality.util.entity;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.jetbrains.annotations.Nullable;

/**
 * Factory helpers for creating custom {@link DamageSource DamageSources}
 * from a {@link ResourceKey}{@code <}{@link DamageType}{@code >}.
 *
 * <p>Vanilla's {@code DamageSources} class only covers built-in damage types.
 * Any mod-defined damage type (Heat Vision, Ground Slam, Blood Drain, etc.)
 * must be constructed manually — this class removes that boilerplate.</p>
 *
 * <p>Ported from puzzles-lib (Fuzs). {@code LookupHelper} dependency inlined.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * // In ModDamageTypes:
 * ResourceKey<DamageType> HEAT_VISION = ResourceKey.create(
 *         Registries.DAMAGE_TYPE, Totality.id("heat_vision"));
 *
 * // In HeatVisionAbility.java:
 * DamageSource source = DamageHelper.damageSource(player.level(), HEAT_VISION, player);
 * target.hurt(source, damage);
 * }</pre>
 */
public final class DamageHelper {

    private DamageHelper() {}

    // -----------------------------------------------------------------------
    // Holder lookup
    // -----------------------------------------------------------------------

    /** Look up a {@link DamageType} holder from an {@link Entity}'s level. */
    public static Holder<DamageType> lookup(Entity entity, ResourceKey<DamageType> key) {
        return lookup(entity.level().registryAccess(), key);
    }

    /** Look up a {@link DamageType} holder from a {@link LevelReader}. */
    public static Holder<DamageType> lookup(LevelReader level, ResourceKey<DamageType> key) {
        return lookup(level.registryAccess(), key);
    }

    /** Look up a {@link DamageType} holder from a {@link HolderLookup.Provider}. */
    public static Holder<DamageType> lookup(HolderLookup.Provider registries, ResourceKey<DamageType> key) {
        return registries.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
    }

    /** Look up a {@link DamageType} holder from a {@link RegistryAccess}. */
    public static Holder<DamageType> lookup(RegistryAccess registries, ResourceKey<DamageType> key) {
        return registries.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
    }

    // -----------------------------------------------------------------------
    // DamageSource factories
    // -----------------------------------------------------------------------

    /**
     * Create a {@link DamageSource} with no entity attribution
     * (e.g. environmental hazards, traps).
     */
    public static DamageSource damageSource(LevelReader level, ResourceKey<DamageType> type) {
        return damageSource(level, type, null, null);
    }

    /**
     * Create a {@link DamageSource} where the direct and causing entity are the same
     * (e.g. a mob striking with an ability directly).
     */
    public static DamageSource damageSource(LevelReader level, ResourceKey<DamageType> type,
                                            @Nullable Entity attacker) {
        return damageSource(level, type, attacker, attacker);
    }

    /**
     * Create a {@link DamageSource} with separate direct and causing entities.
     *
     * @param directEntity  the entity physically dealing the hit (e.g. a projectile)
     * @param causingEntity the entity responsible for it (e.g. the player who fired)
     */
    public static DamageSource damageSource(LevelReader level, ResourceKey<DamageType> type,
                                            @Nullable Entity directEntity,
                                            @Nullable Entity causingEntity) {
        return damageSource(level.registryAccess(), type, directEntity, causingEntity);
    }

    /**
     * Create a {@link DamageSource} with full control over both entity fields.
     */
    public static DamageSource damageSource(RegistryAccess registries, ResourceKey<DamageType> type,
                                            @Nullable Entity directEntity,
                                            @Nullable Entity causingEntity) {
        return new DamageSource(lookup(registries, type), directEntity, causingEntity);
    }
}
