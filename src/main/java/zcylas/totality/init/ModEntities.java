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
import zcylas.totality.entity.magic.FireballProjectileEntity;
import zcylas.totality.entity.magic.LingerEntity;
import zcylas.totality.entity.magic.OrbitProjectileEntity;
import zcylas.totality.entity.magic.SpellBoltEntity;
import zcylas.totality.entity.magic.SummonSkeletonEntity;
import zcylas.totality.entity.npc.BankerNpcEntity;
import zcylas.totality.entity.npc.TotalityNpcEntity;
import zcylas.totality.entity.rest.RestSeatEntity;

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


    private static final ResourceKey<EntityType<?>> SPELL_BOLT_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "spell_bolt"));

    public static final EntityType<SpellBoltEntity> SPELL_BOLT =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "spell_bolt"),
                    EntityType.Builder.<SpellBoltEntity>of(SpellBoltEntity::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(64)
                            .updateInterval(1) // update every tick for smooth movement
                            .build(SPELL_BOLT_KEY)
            );

    public static void register() {}

    private static final ResourceKey<EntityType<?>> FIREBALL_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "fireball_projectile"));

    public static final EntityType<FireballProjectileEntity> FIREBALL_PROJECTILE =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "fireball_projectile"),
                    EntityType.Builder.<FireballProjectileEntity>of(
                                    FireballProjectileEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(FIREBALL_KEY)
            );

    private static final ResourceKey<EntityType<?>> TOTALITY_NPC_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "totality_npc"));

    public static final EntityType<TotalityNpcEntity> TOTALITY_NPC =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "totality_npc"),
                    EntityType.Builder.<TotalityNpcEntity>of(
                                    TotalityNpcEntity::new,
                                    MobCategory.MISC)
                            .sized(0.6f, 1.8f)
                            .clientTrackingRange(64)
                            .build(TOTALITY_NPC_KEY)
            );

    private static final ResourceKey<EntityType<?>> BANKER_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "banker"));

    public static final EntityType<BankerNpcEntity> BANKER =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "banker"),
                    EntityType.Builder.<BankerNpcEntity>of(
                                    BankerNpcEntity::new,
                                    MobCategory.MISC)
                            .sized(0.6f, 1.8f)
                            .clientTrackingRange(64)
                            .build(BANKER_KEY)
            );

    private static final ResourceKey<EntityType<?>> REST_SEAT_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "rest_seat"));

    public static final EntityType<RestSeatEntity> REST_SEAT =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "rest_seat"),
                    EntityType.Builder.<RestSeatEntity>of(
                                    RestSeatEntity::new,
                                    MobCategory.MISC)
                            .sized(0.0001f, 0.0001f)
                            .clientTrackingRange(64)
                            .build(REST_SEAT_KEY)
            );

    private ModEntities() {}
}