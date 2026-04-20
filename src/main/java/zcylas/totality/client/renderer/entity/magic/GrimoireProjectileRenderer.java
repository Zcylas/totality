package zcylas.totality.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import zcylas.totality.entity.magic.GrimoireProjectileEntity;


public class GrimoireProjectileRenderer
        extends EntityRenderer<GrimoireProjectileEntity, EntityRenderState> {

    public GrimoireProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

}