package zcylas.totality.client.particle.fireball;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/**
 * Fireball's own small particles; its frames play over its life and it fades at the end:
 * <ul>
 *   <li>SMOKE: an ember-lit puff that rises slowly, swells and thins out to grey (lit by the world, not full-bright).</li>
 *   <li>FRAGMENT: a charred chunk with a glowing edge, thrown out, falling and bouncing, its edge cooling.</li>
 * </ul>
 */
public class FireballParticle extends SingleQuadParticle {

    public enum Kind { SMOKE, FRAGMENT }

    private final SpriteSet sprites;
    private final Kind kind;

    FireballParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                     SpriteSet sprites, Kind kind, RandomSource random) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.kind = kind;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        if (kind == Kind.SMOKE) {
            this.lifetime = 28 + random.nextInt(22);
            this.friction = 0.92F;
            this.gravity = -0.01F;
            this.quadSize = 0.45F + random.nextFloat() * 0.3F;
            this.hasPhysics = false;
        } else {
            this.lifetime = 18 + random.nextInt(16);
            this.friction = 0.9F;
            this.gravity = 0.6F;
            this.quadSize = 0.12F + random.nextFloat() * 0.06F;
            this.hasPhysics = true;
        }
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        float t = (float) this.age / this.lifetime;
        float fadeFrom = this.kind == Kind.SMOKE ? 0.45F : 0.75F;
        this.alpha = t < fadeFrom ? 1.0F : Math.max(0.0F, 1.0F - (t - fadeFrom) / (1.0F - fadeFrom));
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = (this.age + partialTick) / this.lifetime;
        return this.kind == Kind.SMOKE ? this.quadSize * (1.0F + t * 0.8F) : this.quadSize;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    /** Fragments glow; smoke takes the world's light. */
    @Override
    protected int getLightCoords(float partialTick) {
        return this.kind == Kind.FRAGMENT ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTick);
    }
}
