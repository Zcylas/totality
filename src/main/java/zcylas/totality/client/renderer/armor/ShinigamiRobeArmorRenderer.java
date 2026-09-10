package zcylas.totality.client.renderer.armor;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import zcylas.totality.Totality;
import zcylas.totality.init.items.BleachItems;

/**
 * Renders the Shinigami Robe's custom Blockbench geometry (body/right_arm/left_arm) on the
 * player, following the same body/arm poses each frame as vanilla's own HumanoidModel.
 *
 * The source .bbmodel is a "free" (Generic Model) project authored in Blockbench's own Y-up,
 * feet-at-origin convention — NOT Minecraft's Java entity ModelPart convention, which is Y-down
 * relative to each part's own pivot. CubeListBuilder/ModelPart.Cube only support single-anchor
 * box-UV (auto-unfolded from one texOffs + width/height/depth), not arbitrary per-face UV like
 * block/item models do. The addBox position/size and CubeDeformation grow values below were
 * solved algebraically (against the decompiled ModelPart.Cube UV formula) so the auto-unfolded
 * box-UV reproduces the bbmodel's exact per-face UV rectangles while the CubeDeformation grow
 * independently corrects the rendered size back down to the source cuboid's true dimensions —
 * see the implementation report for the full derivation. Geometry, pivots and the 128x64 texture
 * itself are otherwise untouched.
 */
public final class ShinigamiRobeArmorRenderer implements ArmorRenderer {

    // Direct (non-atlas) RenderTypes.armorCutoutNoCull bind this via a raw TextureManager lookup —
    // unlike a model's "layer0" texture reference, that lookup does NOT auto-prepend "textures/" or
    // append ".png", so the full literal resource path must be given here (confirmed against
    // vanilla's own equivalent call site, EquipmentClientInfo.Layer#getTextureLocation).
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Totality.MOD_ID, "textures/entity/equipment/shinigami_robe.png");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;

    private ShinigamiRobeArmorRenderer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(32, 32)
                        .addBox(-8f, -6f, -4f, 16f, 24f, 8f,
                                new CubeDeformation(-3.65f, -5.65f, -1.65f)),
                PartPose.offset(0f, 0f, 0f));

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(80, 32)
                        .addBox(-5f, -8f, -4f, 8f, 24f, 8f,
                                new CubeDeformation(-1.6f, -5.6f, -1.6f)),
                PartPose.offset(-5f, 2f, 0f));

        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(80, 32).mirror()
                        .addBox(-3f, -8f, -4f, 8f, 24f, 8f,
                                new CubeDeformation(-1.6f, -5.6f, -1.6f)),
                PartPose.offset(5f, 2f, 0f));

        this.root = LayerDefinition.create(mesh, 128, 64).bakeRoot();
        this.body = this.root.getChild("body");
        this.rightArm = this.root.getChild("right_arm");
        this.leftArm = this.root.getChild("left_arm");
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, ItemStack stack,
                        HumanoidRenderState renderState, EquipmentSlot slot, int light,
                        HumanoidModel<HumanoidRenderState> contextModel) {
        if (slot != EquipmentSlot.CHEST) return;

        body.loadPose(contextModel.body.storePose());
        rightArm.loadPose(contextModel.rightArm.storePose());
        leftArm.loadPose(contextModel.leftArm.storePose());

        // Dedicated armor render type: unlike the generic entityCutout, this applies the same
        // polygon-offset/layering vanilla armor uses to avoid z-fighting against the player's own
        // skin mesh underneath (our geometry is only slightly larger than the vanilla body/arms).
        RenderType renderType = RenderTypes.armorCutoutNoCull(TEXTURE);
        int overlay = OverlayTexture.NO_OVERLAY;

        // Submit the whole tree once from the shared root (cubes-less parent whose children are
        // body/right_arm/left_arm) instead of 3 separate submitModelPart calls/Model.Simple
        // instances, matching how a single armor piece is normally submitted.
        collector.submitModelPart(root, poseStack, renderType, light, overlay, null);
    }

    public static void register() {
        ArmorRenderer.register(new ShinigamiRobeArmorRenderer(), BleachItems.SHINIGAMI_ROBE);
    }
}
