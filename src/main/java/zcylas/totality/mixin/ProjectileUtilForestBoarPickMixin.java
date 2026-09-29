package zcylas.totality.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import zcylas.totality.entity.animal.ForestBoarBody;
import zcylas.totality.entity.animal.ForestBoarEntity;

import java.util.Optional;

/**
 * Forest Boar targeting. Vanilla's crosshair pick ({@code LocalPlayer.pick} -> this overload of
 * {@code getEntityHitResult}) tests each candidate's axis-aligned bounding box. For a Forest Boar only, the ray is
 * tested against its turned body regions instead ({@link ForestBoarBody}), so the snout, head and rump outside the
 * square box can be hit and the empty corners of a turned box cannot. Every other entity is untouched; the boar's
 * physical box (collision, navigation) is unchanged.
 */
@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilForestBoarPickMixin {

    private static final String PICK =
            "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;";

    /** The candidate whose box is being tested (the loop reads its bounding box first, then clips it). */
    @Unique
    private static final ThreadLocal<Entity> totality$candidate = new ThreadLocal<>();

    @WrapOperation(method = PICK, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB totality$rememberCandidate(Entity entity, Operation<AABB> original) {
        totality$candidate.set(entity);
        return original.call(entity);
    }

    @WrapOperation(method = PICK, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"))
    private static Optional<Vec3> totality$clipBoarBody(AABB box, Vec3 from, Vec3 to, Operation<Optional<Vec3>> original) {
        if (totality$candidate.get() instanceof ForestBoarEntity boar) return ForestBoarBody.clip(ForestBoarBody.regions(boar), from, to);
        return original.call(box, from, to);
    }

    @WrapOperation(method = PICK, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;contains(Lnet/minecraft/world/phys/Vec3;)Z"))
    private static boolean totality$insideBoarBody(AABB box, Vec3 point, Operation<Boolean> original) {
        Entity candidate = totality$candidate.get();
        totality$candidate.remove();
        if (candidate instanceof ForestBoarEntity boar) return ForestBoarBody.contains(ForestBoarBody.regions(boar), point);
        return original.call(box, point);
    }
}
