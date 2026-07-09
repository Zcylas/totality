package zcylas.totality.client.renderer.entity.npc;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.client.renderer.entity.state.npc.BankerNpcRenderState;
import zcylas.totality.entity.npc.BankerNpcEntity;
import zcylas.totality.entity.npc.NpcGender;

/**
 * Temporary humanoid-skin renderer — swap for a custom Blockbench model/renderer
 * once the Banker's proper model is built.
 */
public class BankerNpcRenderer extends HumanoidMobRenderer<BankerNpcEntity, BankerNpcRenderState, HumanoidModel<BankerNpcRenderState>> {

    private static final Identifier TEXTURE_MALE = Identifier.fromNamespaceAndPath(
            Totality.MOD_ID, "textures/entity/npc/banker/male.png");
    private static final Identifier TEXTURE_FEMALE = Identifier.fromNamespaceAndPath(
            Totality.MOD_ID, "textures/entity/npc/banker/female.png");

    public BankerNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public BankerNpcRenderState createRenderState() {
        return new BankerNpcRenderState();
    }

    @Override
    public void extractRenderState(BankerNpcEntity entity, BankerNpcRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.gender = entity.getGender();
    }

    @Override
    public Identifier getTextureLocation(BankerNpcRenderState state) {
        return state.gender == NpcGender.FEMALE ? TEXTURE_FEMALE : TEXTURE_MALE;
    }
}
