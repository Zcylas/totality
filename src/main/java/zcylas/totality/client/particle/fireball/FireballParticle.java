package zcylas.totality.client.particle.fireball;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import zcylas.totality.entity.magic.FireballParticleBudget;

/**
 * Fireball's own small particles; its frames play over its life and it fades at the end:
 * <ul>
 *   <li>SMOKE: an ember-lit puff that rises slowly, swells and thins out to grey (lit by the world, not full-bright).</li>
 *   <li>FRAGMENT: a charred chunk with a glowing edge, thrown out, falling and bouncing, its edge cooling.</li>
 *   <li>SPARK, EMBER, STREAK (Fireball V2, Firebolt's sprites): debris sparks, small dim embers that cool and drift up,
 *       and heat streaks turned to their motion on screen.</li>
 * </ul>
 * Every kind keeps a limited size on screen ({@link #MAX_HALF_ANGLE}) and fades out right in front of the camera, so a
 * particle flying at the viewer never becomes a large flat square, and each one is counted by
 * {@link FireballParticleBudget}.
 */
public class FireballParticle extends SingleQuadParticle {

    public enum Kind { SMOKE, FRAGMENT, SPARK, EMBER, STREAK }

    /** Largest on-screen half-size of a spark, ember, streak or fragment, in radians (about 9 px at 1080p, 70 degrees). */
    static final float MAX_HALF_ANGLE = 0.012F;
    /** Smoke may be larger on screen, but not a wall in front of the camera; it also fades out from further away. */
    static final float MAX_SMOKE_HALF_ANGLE = 0.035F;
    static final double NEAR_FADE_START = 0.6;
    static final double NEAR_FADE_END = 2.0;
    static final double SMOKE_NEAR_FADE_START = 1.5;
    static final double SMOKE_NEAR_FADE_END = 4.0;

    private final SpriteSet sprites;
    private final Kind kind;
    private final float peakAlpha;
    private float viewScale = 1.0F;
    private boolean counted = true;

    FireballParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                     SpriteSet sprites, Kind kind, RandomSource random) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.kind = kind;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        float peak = 1.0F;
        switch (kind) {
            case SMOKE -> {
                this.lifetime = 28 + random.nextInt(22);
                this.friction = 0.92F;
                this.gravity = -0.01F;
                this.quadSize = 0.45F + random.nextFloat() * 0.3F;
            }
            case FRAGMENT -> {
                this.lifetime = 18 + random.nextInt(16);
                this.friction = 0.9F;
                this.gravity = 0.6F;
                this.quadSize = 0.12F + random.nextFloat() * 0.06F;
                this.hasPhysics = true;
            }
            case SPARK -> {
                this.lifetime = 6 + random.nextInt(7);
                this.friction = 0.82F;
                this.gravity = 0.35F;
                this.quadSize = 0.09F + random.nextFloat() * 0.05F;
                this.hasPhysics = true;                         // sparks skip off the ground
            }
            case EMBER -> {
                this.lifetime = 12 + random.nextInt(11);
                this.friction = 0.92F;
                this.gravity = -0.015F;                         // heat rises a little
                this.quadSize = 0.07F + random.nextFloat() * 0.04F;
                peak = 0.85F;                                   // small and dim: never a flame-coloured mass
            }
            case STREAK -> {
                this.lifetime = 4 + random.nextInt(3);
                this.friction = 0.8F;
                this.gravity = 0.0F;
                this.quadSize = 0.22F + random.nextFloat() * 0.08F;
            }
        }
        this.peakAlpha = peak;
        this.alpha = peak;
        this.setSpriteFromAge(sprites);
        FireballParticleBudget.onCreated();
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        float t = (float) this.age / this.lifetime;
        float fadeFrom = switch (this.kind) {
            case SMOKE -> 0.45F;
            case EMBER -> 0.5F;
            default -> 0.7F;
        };
        this.alpha = this.peakAlpha * (t < fadeFrom ? 1.0F : Math.max(0.0F, 1.0F - (t - fadeFrom) / (1.0F - fadeFrom)));
    }

    @Override
    public void remove() {
        if (this.counted) {
            this.counted = false;
            FireballParticleBudget.onRemoved();
        }
        super.remove();
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = (this.age + partialTick) / this.lifetime;
        float size = this.kind == Kind.SMOKE ? this.quadSize * (1.0F + t * 0.8F) : this.quadSize;
        return size * this.viewScale;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    /** Smoke takes the world's light; everything else glows. */
    @Override
    protected int getLightCoords(float partialTick) {
        return this.kind == Kind.SMOKE ? super.getLightCoords(partialTick) : LightCoordsUtil.FULL_BRIGHT;
    }

    /**
     * Limits the on-screen size and fades the particle out right in front of the camera; streaks are turned so their
     * bright head points along their motion on screen.
     */
    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        Vec3 cam = camera.position();
        double dx = Mth.lerp(partialTick, this.xo, this.x) - cam.x;
        double dy = Mth.lerp(partialTick, this.yo, this.y) - cam.y;
        double dz = Mth.lerp(partialTick, this.zo, this.z) - cam.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float maxAngle = this.kind == Kind.SMOKE ? MAX_SMOKE_HALF_ANGLE : MAX_HALF_ANGLE;
        this.viewScale = 1.0F;
        float unscaled = getQuadSize(partialTick);
        this.viewScale = unscaled <= 0.0F ? 1.0F : (float) Math.min(1.0, dist * maxAngle / unscaled);
        float fade = this.kind == Kind.SMOKE ? nearFade(dist, SMOKE_NEAR_FADE_START, SMOKE_NEAR_FADE_END)
                : nearFade(dist, NEAR_FADE_START, NEAR_FADE_END);
        if (fade <= 0.0F) return;
        float alpha = this.alpha;
        this.alpha = alpha * fade;
        if (this.kind == Kind.STREAK) {
            Vector3f v = new Vector3f((float) this.xd, (float) this.yd, (float) this.zd);
            Vector3fc left = camera.leftVector(), up = camera.upVector();
            float right = -v.dot(left), upward = v.dot(up);
            Quaternionf rotation = new Quaternionf(camera.rotation());
            if (right * right + upward * upward > 1.0E-8F) rotation.rotateZ((float) Mth.atan2(upward, right));
            this.extractRotatedQuad(state, camera, rotation, partialTick);
        } else {
            super.extract(state, camera, partialTick);
        }
        this.alpha = alpha;
    }

    /** 0 at {@code start} blocks from the camera, 1 from {@code end} on (smoothstep). */
    static float nearFade(double dist, double start, double end) {
        double x = Math.clamp((dist - start) / (end - start), 0.0, 1.0);
        return (float) (x * x * (3.0 - 2.0 * x));
    }
}
