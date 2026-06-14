package zcylas.totality.client.renderer.entity.npc;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.client.renderer.entity.state.npc.TotalityNpcRenderState;
import zcylas.totality.entity.npc.TotalityNpcEntity;

public class TotalityNpcRenderer extends HumanoidMobRenderer<TotalityNpcEntity, TotalityNpcRenderState, HumanoidModel<TotalityNpcRenderState>> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Totality.MOD_ID, "textures/entity/npc/npc_default.png");

    public TotalityNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public TotalityNpcRenderState createRenderState() {
        return new TotalityNpcRenderState();
    }

    @Override
    public Identifier getTextureLocation(TotalityNpcRenderState state) {
        return TEXTURE;
    }
}