package zcylas.totality.client.renderer.entity.magic;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import zcylas.totality.entity.magic.SpellBoltEntity;

/**
 * No-op renderer for {@link SpellBoltEntity}.
 * All visuals are handled by particle effects in the entity's tick method.
 */
public class SpellBoltRenderer extends EntityRenderer<SpellBoltEntity, EntityRenderState> {

    public SpellBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}