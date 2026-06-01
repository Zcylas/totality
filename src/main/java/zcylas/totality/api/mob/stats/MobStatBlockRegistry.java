package zcylas.totality.api.mob.stats;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.*;

public final class MobStatBlockRegistry {

    private static final Map<Identifier, MobStatBlock> BY_ENTITY_TYPE = new LinkedHashMap<>();

    public static void register(MobStatBlock block) {
        BY_ENTITY_TYPE.put(block.getEntityType(), block);
    }

    public static @Nullable MobStatBlock get(Identifier entityTypeKey) {
        return BY_ENTITY_TYPE.get(entityTypeKey);
    }

    public static @Nullable MobStatBlock get(LivingEntity entity) {
        return BY_ENTITY_TYPE.get(EntityType.getKey(entity.getType()));
    }

    public static void clear() { BY_ENTITY_TYPE.clear(); }

    public static Collection<MobStatBlock> getAll() {
        return Collections.unmodifiableCollection(BY_ENTITY_TYPE.values());
    }

    private MobStatBlockRegistry() {}
}