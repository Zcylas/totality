package zcylas.totality.init;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import zcylas.totality.Totality;
import zcylas.totality.entity.base_weapon.ThrownShurikenEntity;
import zcylas.totality.entity.magic.GrimoireProjectileEntity;
import zcylas.totality.entity.magic.LingerEntity;
import zcylas.totality.entity.magic.OrbitProjectileEntity;
import zcylas.totality.entity.magic.SummonSkeletonEntity;

public class ModEntities {

    private static final ResourceKey<EntityType<?>> GRIMOIRE_PROJECTILE_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "grimoire_projectile"));
    private static final ResourceKey<EntityType<?>> LINGER_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "linger"));
    private static final ResourceKey<EntityType<?>> ORBIT_PROJECTILE_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "orbit_projectile"));
    private static final ResourceKey<EntityType<?>> SUMMON_SKELETON_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "summon_skeleton"));
    private static final ResourceKey<EntityType<?>> THROWN_SHURIKEN_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "thrown_shuriken"));


    public static final EntityType<SummonSkeletonEntity> SUMMON_SKELETON =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "summon_skeleton"),
                    EntityType.Builder.<SummonSkeletonEntity>of(
                                    SummonSkeletonEntity::new,
                                    MobCategory.MISC)
                            .sized(0.6f, 1.99f)
                            .clientTrackingRange(64)
                            .build(SUMMON_SKELETON_KEY)
            );

    public static final EntityType<LingerEntity> LINGER_ENTITY =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "linger"),
                    EntityType.Builder.<LingerEntity>of(
                                    LingerEntity::new,
                                    MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .noLootTable()
                            .clientTrackingRange(64)
                            .build(LINGER_KEY)
            );public static final EntityType<ThrownShurikenEntity> THROWN_SHURIKEN =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "thrown_shuriken"),
                    EntityType.Builder.<ThrownShurikenEntity>of(
                                    ThrownShurikenEntity::new,
                                    MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build(THROWN_SHURIKEN_KEY)
            );

    public static final EntityType<GrimoireProjectileEntity> GRIMOIRE_PROJECTILE =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "grimoire_projectile"),
                    EntityType.Builder.<GrimoireProjectileEntity>of(
                                    GrimoireProjectileEntity::new,
                                    MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(64)
                            .build(GRIMOIRE_PROJECTILE_KEY)
            );
    public static final EntityType<OrbitProjectileEntity> ORBIT_PROJECTILE =
            Registry.register(BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "orbit_projectile"),
                    EntityType.Builder.<OrbitProjectileEntity>of(
                            OrbitProjectileEntity::new,
                            MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .build(ORBIT_PROJECTILE_KEY)
            );


    public static void register() {}

    private ModEntities() {}
}