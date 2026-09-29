package zcylas.totality.client.renderer.entity.skateboard;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The default skateboard (Context/References/Other/Skateboard_Reference.png): 40 x 12 px (2.5 x 0.75 blocks), grip
 * 6.05 px up (0.38 blocks), cuboids and planes only. Model units are pixels; +y is down (up is negative) and the nose
 * points to -z, as the renderer expects.
 * <p>Four visual regions, each drawn with its own 64 x 64 texture so any of them can be reskinned alone:
 * <ul>
 *   <li><b>deck</b> (wood): a flat middle 12 x 1 x 28 (two halves, to fit the texture) and a nose and tail kicked up
 *       20 deg about the flat's top edge, each stepped 12 / 10 / 8 wide towards the tip (the rounded ends);</li>
 *   <li><b>grip</b>: planes 0.05 above the deck's top faces, half a pixel in from its edges so a wood rim shows;</li>
 *   <li><b>trucks</b>: per truck a baseplate and kingpin under the deck and a hanger across to the wheels;</li>
 *   <li><b>wheels</b>: four 3 x 4 x 4 wheels, each with a hub on its outer face that turns as the board rolls (the
 *       wheel itself stays square to the ground, so its corners never dip below it).</li>
 * </ul>
 * The deck, grip and truck mounts ride on {@code deck_frame}, which rolls about the kingpins' line (the deck leaning
 * into turns); hangers and wheels stay level. The rider is placed by the entity, never from this model, so the board
 * can later turn or flip under the rider on its own.
 */
public final class SkateboardModel {

    /** The grip's top, in pixels above the ground (SkateboardEntity.GRIP_TOP). */
    public static final float GRIP_TOP = 6.05F;
    private static final float FRAME_Y = -3.0F;
    private static final float KICK_ANGLE = 20.0F * Mth.DEG_TO_RAD;
    private static final float TRUCK_Z = 12.0F;
    private static final float WHEEL_X = 5.0F;
    private static final float AXLE_Y = -2.0F;
    private static final float[][] WHEELS = {{WHEEL_X, -TRUCK_Z}, {-WHEEL_X, -TRUCK_Z}, {WHEEL_X, TRUCK_Z}, {-WHEEL_X, TRUCK_Z}};

    public final ModelPart root;
    public final ModelPart deckFrame;
    public final ModelPart deck;
    public final ModelPart grip;
    public final ModelPart truckMounts;
    public final ModelPart hangers;
    public final ModelPart wheels;
    public final ModelPart[] hubs = new ModelPart[4];

    public SkateboardModel(ModelPart root) {
        this.root = root;
        this.deckFrame = root.getChild("deck_frame");
        this.deck = this.deckFrame.getChild("deck");
        this.grip = this.deckFrame.getChild("grip");
        this.truckMounts = this.deckFrame.getChild("truck_mounts");
        this.hangers = root.getChild("hangers");
        this.wheels = root.getChild("wheels");
        for (int i = 0; i < 4; i++) this.hubs[i] = this.wheels.getChild("wheel_" + i).getChild("hub");
    }

    /** Sets the pose: the deck's lean (degrees, positive to the right) and the hubs' turn (radians). */
    public void setupAnim(float lean, float wheelSpin) {
        this.deckFrame.zRot = -lean * Mth.DEG_TO_RAD;
        for (ModelPart hub : this.hubs) hub.xRot = wheelSpin;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition frame = root.addOrReplaceChild("deck_frame", CubeListBuilder.create(), PartPose.offset(0.0F, FRAME_Y, 0.0F));

        // Deck (wood). Frame coordinates: the deck's top is at y -3, its bottom at y -2 (6 and 5 px above the ground).
        PartDefinition deck = frame.addOrReplaceChild("deck", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0F, -3.0F, -14.0F, 12, 1, 14)
                .texOffs(0, 0).addBox(-6.0F, -3.0F, 0.0F, 12, 1, 14), PartPose.ZERO);
        for (int end = -1; end <= 1; end += 2) {
            // Kicks hinge on the flat's top edge at the nose (-z) and tail (+z), turning the tip upwards.
            deck.addOrReplaceChild(end < 0 ? "nose" : "tail", kick(end, false),
                    PartPose.offsetAndRotation(0.0F, -3.0F, 14.0F * end, KICK_ANGLE * end, 0.0F, 0.0F));
        }

        // Grip, 0.05 above the deck's top faces, half a pixel in from the sides (and the tip).
        PartDefinition grip = frame.addOrReplaceChild("grip", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5.5F, -3.05F, -14.0F, 11, 0, 14)
                .texOffs(0, 0).addBox(-5.5F, -3.05F, 0.0F, 11, 0, 14), PartPose.ZERO);
        for (int end = -1; end <= 1; end += 2) {
            grip.addOrReplaceChild(end < 0 ? "nose" : "tail", kick(end, true),
                    PartPose.offsetAndRotation(0.0F, -3.0F, 14.0F * end, KICK_ANGLE * end, 0.0F, 0.0F));
        }

        // Trucks: baseplate against the deck's underside and the kingpin below it (these lean with the deck).
        CubeListBuilder mounts = CubeListBuilder.create();
        for (int end = -1; end <= 1; end += 2) {
            mounts.texOffs(0, 0).addBox(-2.0F, -2.0F, TRUCK_Z * end - 2.0F, 4, 1, 4)
                    .texOffs(16, 0).addBox(-1.0F, -1.0F, TRUCK_Z * end - 1.0F, 2, 1, 2);
        }
        frame.addOrReplaceChild("truck_mounts", mounts, PartPose.ZERO);
        // Hangers: across to the wheels, level on the axles (from 3 px up to the axle line, 2 px up).
        CubeListBuilder hangers = CubeListBuilder.create();
        for (int end = -1; end <= 1; end += 2) {
            hangers.texOffs(0, 8).addBox(-4.0F, -3.0F, TRUCK_Z * end - 1.0F, 8, 1, 2);
        }
        root.addOrReplaceChild("hangers", hangers, PartPose.ZERO);

        // Wheels (left ones mirrored so their outer faces match), each with a hub on its outer face.
        PartDefinition wheels = root.addOrReplaceChild("wheels", CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            float x = WHEELS[i][0];
            boolean left = x < 0;
            CubeListBuilder wheel = CubeListBuilder.create().texOffs(0, 0);
            if (left) wheel.mirror();
            PartDefinition part = wheels.addOrReplaceChild("wheel_" + i, wheel.addBox(-1.5F, -2.0F, -2.0F, 3, 4, 4),
                    PartPose.offset(x, AXLE_Y, WHEELS[i][1]));
            CubeListBuilder hub = CubeListBuilder.create().texOffs(0, 10);
            if (left) hub.mirror();
            part.addOrReplaceChild("hub", hub.addBox(left ? -1.55F : 1.55F, -1.5F, -1.5F, 0, 3, 3), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * A kick (nose for end = -1, tail for 1), stepped 12 / 10 / 8 wide towards the tip; the deck's boxes, or with
     * {@code grip} the grip planes 0.05 above them, half a pixel in from their sides and the tip.
     */
    private static CubeListBuilder kick(int end, boolean grip) {
        CubeListBuilder b = CubeListBuilder.create();
        float[][] steps = {{6.0F, 0.0F, 4.0F}, {5.0F, 4.0F, 1.0F}, {4.0F, 5.0F, 1.0F}};
        int[][] uv = grip ? new int[][]{{0, 16}, {0, 22}, {16, 22}} : new int[][]{{0, 16}, {0, 22}, {24, 22}};
        for (int i = 0; i < 3; i++) {
            float half = steps[i][0] - (grip ? 0.5F : 0.0F);
            float from = steps[i][1];
            float length = grip && i == 2 ? steps[i][2] - 0.5F : steps[i][2];
            float z = end < 0 ? -from - length : from;
            b.texOffs(uv[i][0], uv[i][1]);
            if (grip) b.addBox(-half, -0.05F, z, half * 2, 0, length);
            else b.addBox(-half, 0.0F, z, half * 2, 1, length);
        }
        return b;
    }
}
