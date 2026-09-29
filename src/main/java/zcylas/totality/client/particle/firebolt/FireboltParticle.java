package zcylas.totality.client.particle.firebolt;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * One Firebolt pixel-art particle: its sprite frames play over its life (hot to cooling), it is always full-bright
 * (fire lights itself) and fades out at the end. The kinds differ in size, life and motion:
 * <ul>
 *   <li>EMBER: a 1-2 texel mote that drifts and cools.</li>
 *   <li>WISP: a small flame tongue that rises and thins out.</li>
 *   <li>SPARK: a bright cross flung outward, pulled down and slowed quickly.</li>
 *   <li>STREAK: a line of heat turned to its direction of travel on screen.</li>
 *   <li>FLASH: the cast/impact bloom: grows for two ticks, then collapses.</li>
 * </ul>
 */
public class FireboltParticle extends SingleQuadParticle {

    public enum Kind { EMBER, WISP, SPARK, STREAK, FLASH }

    private final SpriteSet sprites;
    private final Kind kind;
    private final float baseSize;

    FireboltParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                     SpriteSet sprites, Kind kind, RandomSource random) {
        super(level, x, y, z, sprites.get(random));
        this.sprites = sprites;
        this.kind = kind;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        switch (kind) {
            case EMBER -> {
                this.lifetime = 10 + random.nextInt(12);
                this.friction = 0.9F;
                this.gravity = -0.02F;                          // heat rises a little
                this.quadSize = 0.22F + random.nextFloat() * 0.08F;
            }
            case WISP -> {
                this.lifetime = 7 + random.nextInt(5);
                this.friction = 0.86F;
                this.gravity = -0.04F;
                this.quadSize = 0.22F + random.nextFloat() * 0.12F;
            }
            case SPARK -> {
                this.lifetime = 6 + random.nextInt(7);
                this.friction = 0.82F;
                this.gravity = 0.35F;
                this.quadSize = 0.11F + random.nextFloat() * 0.07F;
                this.hasPhysics = true;                         // sparks skip off the ground
            }
            case STREAK -> {
                this.lifetime = 4 + random.nextInt(3);
                this.friction = 0.8F;
                this.gravity = 0.0F;
                this.quadSize = 0.34F + random.nextFloat() * 0.12F;
            }
            case FLASH -> {
                this.lifetime = 5;
                this.friction = 0.0F;
                this.gravity = 0.0F;
                this.quadSize = 0.5F;
            }
        }
        this.baseSize = this.quadSize;
        this.setSpriteFromAge(sprites);
    }

    /** Scales the particle (the flash is larger for an impact than for a cast). */
    FireboltParticle sized(float factor) {
        this.quadSize *= factor;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        float t = (float) this.age / this.lifetime;
        this.alpha = t < 0.7F ? 1.0F : Math.max(0.0F, 1.0F - (t - 0.7F) / 0.3F);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = (this.age + partialTick) / this.lifetime;
        return switch (this.kind) {
            case FLASH -> this.quadSize * (t < 0.4F ? 0.6F + t : 1.0F - (t - 0.4F) * 0.9F);
            case WISP -> this.quadSize * (1.0F - t * 0.35F);
            default -> this.quadSize;
        };
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return LightCoordsUtil.FULL_BRIGHT;
    }

    /** Streaks are turned so their bright head points along their motion as seen on screen. */
    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        if (this.kind != Kind.STREAK) {
            super.extract(state, camera, partialTick);
            return;
        }
        Vector3f v = new Vector3f((float) this.xd, (float) this.yd, (float) this.zd);
        Vector3fc left = camera.leftVector(), up = camera.upVector();
        float right = -v.dot(left), upward = v.dot(up);
        Quaternionf rotation = new Quaternionf(camera.rotation());
        if (right * right + upward * upward > 1.0E-8F) rotation.rotateZ((float) Mth.atan2(upward, right));
        this.extractRotatedQuad(state, camera, rotation, partialTick);
    }
}
