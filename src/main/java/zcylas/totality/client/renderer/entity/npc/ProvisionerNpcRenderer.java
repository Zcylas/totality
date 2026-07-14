package zcylas.totality.client.renderer.entity.npc;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.client.renderer.entity.state.npc.ProvisionerNpcRenderState;
import zcylas.totality.entity.npc.NpcGender;
import zcylas.totality.entity.npc.ProvisionerNpcEntity;

/**
 * Temporary humanoid-skin renderer for the Provisioner (Phase 4, Part A) — reuses the existing
 * generic NPC humanoid model exactly as {@link BankerNpcRenderer} does, following the SAME
 * established male/female texture-selection pattern (a synced {@code gender} field on the render
 * state, resolved to one of two textures at draw time). Deliberately its OWN renderer/texture
 * pair rather than reusing the Banker's — a Provisioner is a distinct merchant archetype with its
 * own skins (design document Section 8: still gender+name identity only, no new skin-category
 * axis introduced by having a second per-archetype texture pair).
 */
public class ProvisionerNpcRenderer extends HumanoidMobRenderer<ProvisionerNpcEntity, ProvisionerNpcRenderState, HumanoidModel<ProvisionerNpcRenderState>> {

    private static final Identifier TEXTURE_MALE = Identifier.fromNamespaceAndPath(
            Totality.MOD_ID, "textures/entity/npc/provisioner/male.png");
    private static final Identifier TEXTURE_FEMALE = Identifier.fromNamespaceAndPath(
            Totality.MOD_ID, "textures/entity/npc/provisioner/female.png");

    /** Safe fallback (Part A requirement) if gender data is ever unexpectedly unavailable —
     *  reuses the generic NPC's default skin rather than the Banker's (which would misrepresent
     *  the Provisioner as a different archetype) or crashing on a null render state field. */
    private static final Identifier TEXTURE_FALLBACK = Identifier.fromNamespaceAndPath(
            Totality.MOD_ID, "textures/entity/npc/npc_default.png");

    public ProvisionerNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ProvisionerNpcRenderState createRenderState() {
        return new ProvisionerNpcRenderState();
    }

    @Override
    public void extractRenderState(ProvisionerNpcEntity entity, ProvisionerNpcRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.gender = entity.getGender();
    }

    @Override
    public Identifier getTextureLocation(ProvisionerNpcRenderState state) {
        return textureFor(state.gender);
    }

    /** Pure gender-to-texture mapping, extracted specifically so it can be verified directly
     *  (Phase 4, Part A/I) without needing a real renderer/GL context — a null {@code gender}
     *  (defensive only; the render state field is never actually null in practice) falls back to
     *  the generic NPC texture rather than guessing MALE or FEMALE. */
    public static Identifier textureFor(@Nullable NpcGender gender) {
        if (gender == null) return TEXTURE_FALLBACK;
        return switch (gender) {
            case FEMALE -> TEXTURE_FEMALE;
            case MALE -> TEXTURE_MALE;
        };
    }
}
