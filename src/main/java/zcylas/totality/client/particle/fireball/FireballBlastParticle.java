package zcylas.totality.client.particle.fireball;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.LightCoordsUtil;

/**
 * The detonation sphere: one full-bright, camera-facing pixel-art sprite. It bursts out to the blast radius in about
 * three ticks (impact star and expansion frames), overshoots slightly, holds the full sphere, then breaks up and
 * fades. Seen from any side it reads as a sphere of fire the size of the area the blast reaches.
 */
public class FireballBlastParticle extends SingleQuadParticle {

    static final int LIFETIME = 20;
    /** Frame per age tick: the star and expansion frames with the burst, the full sphere held, then breaking up. */
    private static final int[] FRAME_BY_AGE = {0, 1, 2, 3, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 6, 6, 6, 7, 7, 7};

    private final SpriteSet sprites;
    private final float radius;

    FireballBlastParticle(ClientLevel level, double x, double y, double z, float radius, SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.radius = radius;
        this.lifetime = LIFETIME;
        this.gravity = 0.0F;
        this.friction = 0.0F;
        this.hasPhysics = false;
        this.xd = this.yd = this.zd = 0;
        this.quadSize = radius;
        this.setSprite(sprites.get(FRAME_BY_AGE[0], 7));
    }

    @Override
    public void tick() {
        super.tick();
        this.setSprite(this.sprites.get(FRAME_BY_AGE[Math.min(this.age, LIFETIME - 1)], 7));
        float t = (float) this.age / this.lifetime;
        this.alpha = t < 0.9F ? 1.0F : Math.max(0.0F, 1.0F - (t - 0.9F) / 0.1F);
    }

    /** Bursts from a fifth of the radius to 105 % in three ticks, settles on the radius, then swells a little. */
    @Override
    public float getQuadSize(float partialTick) {
        float a = this.age + partialTick;
        float grow = a < 3.0F ? 0.2F + 0.85F * (a / 3.0F) : a < 5.0F ? 1.05F - 0.05F * ((a - 3.0F) / 2.0F) : 1.0F + 0.004F * (a - 5.0F);
        return this.radius * grow;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return LightCoordsUtil.FULL_BRIGHT;
    }
}
