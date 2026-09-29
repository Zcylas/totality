package zcylas.totality.client.particle.portal;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import zcylas.totality.entity.portal.VisualPortalVfx;

/**
 * A floating pixel cube of the visual portal (1 to 3 texels at the portal's 16 texels a block), always full-bright.
 * MOTE drifts off the rim and fades; CONVERGE flies into the oval and arrives exactly as it expires (opening, and the
 * start of a collapse); SCATTER is thrown out when the portal breaks apart, slowing as it fades.
 */
public class VisualPortalParticle extends SingleQuadParticle {

    public enum Kind { MOTE, CONVERGE, SCATTER }

    private final Kind kind;

    VisualPortalParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                         SpriteSet sprites, Kind kind, RandomSource random) {
        super(level, x, y, z, sprites.get(random));
        this.kind = kind;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        int pixels = 1 + random.nextInt(kind == Kind.CONVERGE ? 2 : 3);
        this.quadSize = pixels / 32.0F;                     // half-extent: a 2-texel cube is 1/8 block across
        switch (kind) {
            case MOTE -> {
                this.lifetime = 24 + random.nextInt(20);
                this.friction = 0.98F;
            }
            case CONVERGE -> {
                this.lifetime = VisualPortalVfx.CONVERGE_TICKS;
                this.friction = 1.0F;
            }
            case SCATTER -> {
                this.lifetime = 16 + random.nextInt(16);
                this.friction = 0.86F;
            }
        }
        this.alpha = kind == Kind.SCATTER ? 1.0F : 0.0F;
    }

    @Override
    public void tick() {
        super.tick();
        float t = (float) this.age / this.lifetime;
        this.alpha = switch (this.kind) {
            case MOTE -> Math.min(1.0F, this.age / 4.0F) * (t < 0.6F ? 1.0F : (1.0F - t) / 0.4F);
            case CONVERGE -> Math.min(1.0F, this.age / 3.0F);
            case SCATTER -> t < 0.5F ? 1.0F : (1.0F - t) / 0.5F;
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
}
