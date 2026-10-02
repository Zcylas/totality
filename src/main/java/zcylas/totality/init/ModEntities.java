package zcylas.totality.init;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import zcylas.totality.Totality;
import zcylas.totality.entity.base_weapon.ThrownShurikenEntity;
import zcylas.totality.entity.dev.SlimeTestEntity;
import zcylas.totality.entity.gate.SoloGateEntity;
import zcylas.totality.entity.magic.GrimoireProjectileEntity;
import zcylas.totality.entity.magic.FireballProjectileEntity;
import zcylas.totality.entity.magic.LingerEntity;
import zcylas.totality.entity.magic.OrbitProjectileEntity;
import zcylas.totality.entity.magic.SpellBoltEntity;
import zcylas.totality.entity.magic.SummonSkeletonEntity;
import zcylas.totality.entity.npc.BankerNpcEntity;
import zcylas.totality.entity.npc.ProvisionerNpcEntity;
import zcylas.totality.entity.npc.TotalityNpcEntity;
import zcylas.totality.entity.rest.RestSeatEntity;
import zcylas.totality.entity.vehicle.SkateboardEntity;

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

    private static final ResourceKey<EntityType<?>> PROVISIONER_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "provisioner"));

    public static final EntityType<ProvisionerNpcEntity> PROVISIONER =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "provisioner"),
                    EntityType.Builder.<ProvisionerNpcEntity>of(
                                    ProvisionerNpcEntity::new,
                                    MobCategory.MISC)
                            .sized(0.6f, 1.8f)
                            .clientTrackingRange(64)
                            .build(PROVISIONER_KEY)
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

    private static final ResourceKey<EntityType<?>> FOREST_BOAR_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "forest_boar"));

    /**
     * Forest Boar (Astra's model). The box matches the model as rendered (render scale 1.819, derived from the
     * .bbmodel by bbmodel_to_java.py): 0.91 blocks wide across the body, 1.30 blocks to the ear tips (1.24 to the
     * back); the painted eyes are 0.68 blocks up. The square box covers the body; the snout and rump overhang it,
     * as on vanilla quadrupeds, so it still fits one-block gaps.
     */
    public static final EntityType<zcylas.totality.entity.animal.ForestBoarEntity> FOREST_BOAR =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "forest_boar"),
                    EntityType.Builder.<zcylas.totality.entity.animal.ForestBoarEntity>of(
                                    zcylas.totality.entity.animal.ForestBoarEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.9f, 1.3f)
                            .eyeHeight(0.68f)
                            .clientTrackingRange(10)
                            .build(FOREST_BOAR_KEY)
            );

    private static final ResourceKey<EntityType<?>> VISUAL_PORTAL_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "visual_portal"));

    /** Creative Capability Test C: the nonfunctional, visual-only green portal (its position is the oval's centre). */
    public static final EntityType<zcylas.totality.entity.portal.VisualPortalEntity> VISUAL_PORTAL =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "visual_portal"),
                    EntityType.Builder.<zcylas.totality.entity.portal.VisualPortalEntity>of(
                                    zcylas.totality.entity.portal.VisualPortalEntity::new,
                                    MobCategory.MISC)
                            .sized(1.6f, 2.4f)
                            .clientTrackingRange(8)
                            .build(VISUAL_PORTAL_KEY)
            );

    private static final ResourceKey<EntityType<?>> SOLO_GATE_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "solo_gate"));

    /** Creative Test K: the visual-only Solo Leveling Normal Gate (its position is the vortex centre; ~3 blocks tall). */
    public static final EntityType<SoloGateEntity> SOLO_GATE =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "solo_gate"),
                    EntityType.Builder.<SoloGateEntity>of(SoloGateEntity::new, MobCategory.MISC)
                            .sized(3.0f, 3.0f)
                            .clientTrackingRange(8)
                            .build(SOLO_GATE_KEY)
            );

    private static final ResourceKey<EntityType<?>> SKATEBOARD_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "skateboard"));

    /**
     * Creative Test D: the default skateboard. The box is the deck's width (it grows to a standing rider's height while
     * ridden, see SkateboardEntity); tracked like vanilla boats.
     */
    public static final EntityType<SkateboardEntity> SKATEBOARD =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "skateboard"),
                    EntityType.Builder.<SkateboardEntity>of(SkateboardEntity::new, MobCategory.MISC)
                            .noLootTable()
                            .sized(0.9f, 0.35f)
                            .clientTrackingRange(10)
                            .build(SKATEBOARD_KEY)
            );

    private static final ResourceKey<EntityType<?>> SLIME_TEST_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "slime_test"));

    /**
     * DEVELOPMENT TEST: the V1 Small Slime model for in-game visual review, not the production Slime. The box matches
     * the model as rendered (20 x 15 x 18 units at render scale 0.64 = 0.80 x 0.60 x 0.72 blocks); the eyes' centre is
     * 0.24 blocks up.
     */
    public static final EntityType<SlimeTestEntity> SLIME_TEST =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "slime_test"),
                    EntityType.Builder.<SlimeTestEntity>of(SlimeTestEntity::new, MobCategory.MISC)
                            .noLootTable()
                            .sized(0.8f, 0.6f)
                            .eyeHeight(0.24f)
                            .clientTrackingRange(10)
                            .build(SLIME_TEST_KEY)
            );

    private ModEntities() {}
}