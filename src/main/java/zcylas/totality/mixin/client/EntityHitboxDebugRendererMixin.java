package zcylas.totality.mixin.client;

import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.entity.animal.ForestBoarBody;
import zcylas.totality.entity.animal.ForestBoarEntity;

/**
 * F3+B: next to vanilla's white bounding box (the boar's physical box: collision and movement), outline the Forest
 * Boar's targeting regions in cyan ({@link ForestBoarBody}), the way vanilla outlines the Ender Dragon's parts.
 */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {

    private static final int[][] EDGES = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    @Inject(method = "showHitboxes", at = @At("TAIL"))
    private void totality$boarTargetRegions(Entity entity, float partialTicks, boolean isServerEntity, CallbackInfo ci) {
        if (isServerEntity || !(entity instanceof ForestBoarEntity boar)) return;
        Vec3 offset = entity.getPosition(partialTicks).subtract(entity.position());
        for (ForestBoarBody.Region region : ForestBoarBody.regions(boar)) {
            Vec3[] c = region.corners();
            for (int[] e : EDGES) Gizmos.line(c[e[0]].add(offset), c[e[1]].add(offset), 0xFF40E0FF);
        }
    }
}
