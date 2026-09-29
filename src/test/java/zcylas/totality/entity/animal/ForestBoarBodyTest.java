package zcylas.totality.entity.animal;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import zcylas.totality.entity.animal.ForestBoarEntity.Behavior;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Forest Boar's targeting regions: the visible snout, head and rump outside the 0.9-wide physical box can be hit,
 * empty air beside the body (including a turned square box's corners) cannot, and the regions follow the boar's yaw,
 * head yaw and lowered poses.
 */
class ForestBoarBodyTest {

    private static final Vec3 O = Vec3.ZERO;

    /** A ray from the side (+x) at height y, crossing the boar's forward axis at z = fwd, for a boar facing +z. */
    private static boolean sideRay(List<ForestBoarBody.Region> r, double fwd, double y) {
        return ForestBoarBody.clip(r, new Vec3(3, y, fwd), new Vec3(-3, y, fwd)).isPresent();
    }

    private static List<ForestBoarBody.Region> facingPlusZ(Behavior b) {
        return ForestBoarBody.regions(O, 0.0F, 0.0F, 0.0F, b);
    }

    @Test
    void snoutHeadAndRumpOutsideThePhysicalBoxCanBeHit() {
        var r = facingPlusZ(Behavior.WANDER);
        AABB square = new AABB(-0.45, 0, -0.45, 0.45, 1.3, 0.45);
        assertTrue(sideRay(r, 0.85, 0.55), "snout from the side");
        assertFalse(square.clip(new Vec3(3, 0.55, 0.85), new Vec3(-3, 0.55, 0.85)).isPresent(), "the square box misses it");
        assertTrue(sideRay(r, 0.6, 1.2), "head top / ears");
        assertTrue(sideRay(r, -0.7, 0.8), "rump from the side");
        assertTrue(sideRay(r, 0.0, 0.7), "flank");
        assertTrue(ForestBoarBody.clip(r, new Vec3(0, 0.8, -3), new Vec3(0, 0.8, 0)).isPresent(), "rump from behind");
    }

    @Test
    void emptySpaceBesideAndAroundTheBodyIsNotHit() {
        var r = facingPlusZ(Behavior.WANDER);
        assertFalse(ForestBoarBody.clip(r, new Vec3(0.72, 0.55, 3), new Vec3(0.72, 0.55, -3)).isPresent(), "beside, along the flank");
        assertFalse(sideRay(r, 1.1, 0.55), "in front of the snout");
        assertFalse(sideRay(r, -0.95, 0.8), "behind the rump");
        assertFalse(sideRay(r, 0.0, 1.45), "above the back");
        assertFalse(sideRay(r, 0.8, 0.2), "under the chin, in front of the forelegs");
    }

    @Test
    void theRegionsTurnWithTheBoarSoASquareBoxCornerIsEmpty() {
        var turned = ForestBoarBody.regions(O, 45.0F, 45.0F, 0.0F, Behavior.WANDER);
        // Straight down through (0.42, 0.42): inside the axis-aligned 0.9 box, but 0.59 to the side of the turned body.
        assertFalse(ForestBoarBody.clip(turned, new Vec3(0.42, 3, 0.42), new Vec3(0.42, -1, 0.42)).isPresent());
        // Its snout, 0.85 ahead along the turned facing (-sin 45, cos 45).
        double s = Math.sin(Math.toRadians(45)), c = Math.cos(Math.toRadians(45));
        Vec3 snout = new Vec3(-s * 0.85, 0.55, c * 0.85);
        assertTrue(ForestBoarBody.contains(turned, snout));
    }

    @Test
    void theHeadFollowsHeadYawAndTheGrazingPose() {
        var lookingLeft = ForestBoarBody.regions(O, 0.0F, 30.0F, 0.0F, Behavior.WANDER);
        double s = Math.sin(Math.toRadians(30)), c = Math.cos(Math.toRadians(30));
        Vec3 turnedSnout = new Vec3(-s * 0.5, 0.55, 0.37 + c * 0.5);
        assertTrue(ForestBoarBody.contains(lookingLeft, turnedSnout));
        assertFalse(ForestBoarBody.contains(facingPlusZ(Behavior.WANDER), new Vec3(0.0, 0.15, 0.95)), "no snout at the grass while standing");
        assertTrue(ForestBoarBody.contains(facingPlusZ(Behavior.GRAZE), new Vec3(0.0, 0.15, 0.95)), "the lowered snout while grazing");
        assertTrue(ForestBoarBody.contains(facingPlusZ(Behavior.CHARGE), new Vec3(0.0, 0.25, 0.7)), "the lowered tusks while charging");
    }

    @Test
    void chargeContactTouchesAheadButNotBeside() {
        var r = facingPlusZ(Behavior.CHARGE);
        AABB ahead = new AABB(-0.3, 0, 0.95, 0.3, 1.8, 1.55);          // a player just in front of the tusks
        AABB beside = new AABB(0.65, 0, -0.3, 1.25, 1.8, 0.3);         // a player 0.2 past the flank
        AABB diagonalGap = new AABB(0.6, 0, 1.0, 1.2, 1.8, 1.6);       // in front and to the side
        assertTrue(ForestBoarBody.touches(r, ahead, 0.1));
        assertFalse(ForestBoarBody.touches(r, beside, 0.1));
        assertFalse(ForestBoarBody.touches(r, diagonalGap, 0.1));
    }
}
