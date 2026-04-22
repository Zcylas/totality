package zcylas.totality.init;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import zcylas.totality.Totality;
import zcylas.totality.entity.magic.GrimoireProjectileEntity;
import zcylas.totality.entity.magic.OrbitProjectileEntity;

public class ModEntities {

    private static final ResourceKey<EntityType<?>> GRIMOIRE_PROJECTILE_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "grimoire_projectile"));

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
                            .build(GRIMOIRE_PROJECTILE_KEY)
            );


    public static void register() {}

    private ModEntities() {}
}