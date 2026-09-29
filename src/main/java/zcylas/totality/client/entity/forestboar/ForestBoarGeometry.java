package zcylas.totality.client.entity.forestboar;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

import java.util.List;

import static zcylas.totality.client.entity.forestboar.FaceUvParts.box;
import static zcylas.totality.client.entity.forestboar.FaceUvParts.part;

/**
 * GENERATED from Forest_Boar_Hybrid.bbmodel (Astra's Forest Boar geometry and texture, with the hybrid animations) by
 * Totality-Research/forest-boar/hybrid/tools/bbmodel_to_java.py. Do not edit: edit the .bbmodel and re-run the tool.
 * 63 cuboids with Astra's per-face UVs on the 128x128 texture, in Astra's group hierarchy and pivots.
 */
final class ForestBoarGeometry {

    private ForestBoarGeometry() {}

    static final int TEXTURE_WIDTH = 128;
    static final int TEXTURE_HEIGHT = 128;
    /** 1.3 blocks (the reference sheet) over the model's rest height to the ear tips; applied on the root group, whose pivot is the ground. */
    static final float RENDER_SCALE = 1.8191F;
    /** As rendered, in blocks (the entity's hitbox and eye height in ModEntities must agree with these). */
    static final float HEIGHT_BLOCKS = 1.3F;
    static final float BACK_HEIGHT_BLOCKS = 1.2392F;
    static final float BODY_WIDTH_BLOCKS = 0.9095F;
    static final float EYE_HEIGHT_BLOCKS = 0.6765F;

    static ModelPart createRoot() {
        ModelPart snout = part(PartPose.offset(0.0F, 2.4F, -3.55F),
                List.of(
                box("pink_snout", -1.5F, -1.05F, -1.25F, 3.0F, 2.0F, 1.25F,
                        100.0F, 76.0F, 94.0F, 73.0F, 100.0F, 76.0F, 94.0F, 73.0F, 81.0F, 73.0F, 84.0F, 77.0F, 73.0F, 73.0F, 79.0F, 77.0F, 81.0F, 73.0F, 84.0F, 77.0F, 86.0F, 73.0F, 92.0F, 77.0F)));
        ModelPart earLeft = part(PartPose.offsetAndRotation(-3.0F, -2.45F, -1.65F, -0.1396F, 0.0F, -0.1396F),
                List.of(
                box("ear_left", -0.7F, -1.65F, -0.5F, 1.4F, 1.65F, 0.8F,
                        9.0F, 84.0F, 6.0F, 82.0F, 9.0F, 84.0F, 6.0F, 82.0F, 122.0F, 73.0F, 124.0F, 76.0F, 114.0F, 73.0F, 120.0F, 80.0F, 122.0F, 73.0F, 124.0F, 76.0F, 1.0F, 82.0F, 4.0F, 85.0F)));
        ModelPart browLeft = part(PartPose.offsetAndRotation(-2.35F, -0.3F, -3.45F, 0.0F, 0.0F, 0.0873F),
                List.of(
                box("cream_brow_left", -1.05F, -0.35F, -0.47F, 2.3F, 0.75F, 0.62F,
                        27.0F, 84.0F, 22.0F, 82.0F, 27.0F, 84.0F, 22.0F, 82.0F, 18.0F, 82.0F, 20.0F, 84.0F, 11.0F, 82.0F, 16.0F, 84.0F, 18.0F, 82.0F, 20.0F, 84.0F, 22.0F, 82.0F, 27.0F, 84.0F)));
        ModelPart tuskLeft = part(PartPose.offset(-2.15F, 3.4F, -3.55F),
                List.of(
                box("tusk_left_root", -0.55F, -0.55F, -0.7F, 1.1F, 0.8F, 1.1F,
                        31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F),
                box("tusk_left_rise", -0.75F, -1.8F, -1.0F, 0.8F, 1.45F, 0.75F,
                        31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 33.0F, 82.0F, 35.0F, 85.0F, 33.0F, 82.0F, 35.0F, 85.0F, 33.0F, 82.0F, 35.0F, 85.0F, 33.0F, 82.0F, 35.0F, 85.0F),
                box("tusk_left_tip", -0.65F, -2.4F, -0.95F, 0.6F, 0.65F, 0.6F,
                        31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F)));
        ModelPart earRight = part(PartPose.offsetAndRotation(3.0F, -2.45F, -1.65F, -0.1396F, 0.0F, 0.1396F),
                List.of(
                box("ear_right", -0.7F, -1.65F, -0.5F, 1.4F, 1.65F, 0.8F,
                        9.0F, 84.0F, 6.0F, 82.0F, 9.0F, 84.0F, 6.0F, 82.0F, 122.0F, 73.0F, 124.0F, 76.0F, 114.0F, 73.0F, 120.0F, 80.0F, 122.0F, 73.0F, 124.0F, 76.0F, 1.0F, 82.0F, 4.0F, 85.0F)));
        ModelPart browRight = part(PartPose.offsetAndRotation(2.35F, -0.3F, -3.45F, 0.0F, 0.0F, -0.0873F),
                List.of(
                box("cream_brow_right", -1.25F, -0.35F, -0.47F, 2.3F, 0.75F, 0.62F,
                        27.0F, 84.0F, 22.0F, 82.0F, 27.0F, 84.0F, 22.0F, 82.0F, 18.0F, 82.0F, 20.0F, 84.0F, 11.0F, 82.0F, 16.0F, 84.0F, 18.0F, 82.0F, 20.0F, 84.0F, 22.0F, 82.0F, 27.0F, 84.0F)));
        ModelPart tuskRight = part(PartPose.offset(2.15F, 3.4F, -3.55F),
                List.of(
                box("tusk_right_root", -0.55F, -0.55F, -0.7F, 1.1F, 0.8F, 1.1F,
                        31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F),
                box("tusk_right_rise", -0.05F, -1.8F, -1.0F, 0.8F, 1.45F, 0.75F,
                        31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 33.0F, 82.0F, 35.0F, 85.0F, 33.0F, 82.0F, 35.0F, 85.0F, 33.0F, 82.0F, 35.0F, 85.0F, 33.0F, 82.0F, 35.0F, 85.0F),
                box("tusk_right_tip", 0.05F, -2.4F, -0.95F, 0.6F, 0.65F, 0.6F,
                        31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F, 29.0F, 82.0F, 31.0F, 84.0F)));
        ModelPart head = part(PartPose.offset(0.0F, -2.2F, -3.25F),
                List.of(
                box("head_main", -3.75F, -2.5F, -3.5F, 7.5F, 6.5F, 3.85F,
                        86.0F, 63.0F, 71.0F, 55.0F, 103.0F, 63.0F, 88.0F, 55.0F, 34.0F, 55.0F, 42.0F, 68.0F, 17.0F, 55.0F, 32.0F, 68.0F, 61.0F, 55.0F, 69.0F, 68.0F, 44.0F, 55.0F, 59.0F, 68.0F),
                box("forehead_cap", -3.5F, -3.1F, -2.75F, 7.0F, 0.7F, 3.0F,
                        31.0F, 79.0F, 17.0F, 73.0F, 47.0F, 79.0F, 33.0F, 73.0F, 121.0F, 55.0F, 127.0F, 57.0F, 105.0F, 55.0F, 119.0F, 57.0F, 121.0F, 55.0F, 127.0F, 57.0F, 1.0F, 73.0F, 15.0F, 75.0F),
                box("muzzle_base", -2.05F, 1.3F, -3.9F, 4.1F, 2.4F, 0.65F,
                        71.0F, 75.0F, 63.0F, 73.0F, 71.0F, 75.0F, 63.0F, 73.0F, 59.0F, 73.0F, 61.0F, 78.0F, 49.0F, 73.0F, 57.0F, 78.0F, 59.0F, 73.0F, 61.0F, 78.0F, 49.0F, 73.0F, 57.0F, 78.0F),
                box("lower_lip", -1.45F, 3.34F, -4.2F, 2.9F, 0.31F, 0.8F,
                        108.0F, 75.0F, 102.0F, 73.0F, 108.0F, 75.0F, 102.0F, 73.0F, 110.0F, 73.0F, 112.0F, 75.0F, 102.0F, 73.0F, 108.0F, 75.0F, 110.0F, 73.0F, 112.0F, 75.0F, 102.0F, 73.0F, 108.0F, 75.0F),
                box("head_stripe_0_face", 1.66F, -2.58F, -3.63F, 0.68F, 1.63F, 0.25F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 90.0F, 82.0F, 92.0F, 85.0F, 90.0F, 82.0F, 92.0F, 85.0F, 90.0F, 82.0F, 92.0F, 85.0F, 90.0F, 82.0F, 92.0F, 85.0F),
                box("head_stripe_0_ledge", 1.66F, -2.69F, -3.58F, 0.68F, 0.22F, 0.91F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("head_stripe_0_riser", 1.66F, -3.19F, -2.87F, 0.68F, 0.7F, 0.24F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("head_stripe_0_crown", 1.66F, -3.33F, -2.78F, 0.68F, 0.26F, 2.0F,
                        106.0F, 86.0F, 104.0F, 82.0F, 106.0F, 86.0F, 104.0F, 82.0F, 98.0F, 82.0F, 102.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 98.0F, 82.0F, 102.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("head_stripe_1_face", -0.425F, -2.58F, -3.63F, 0.85F, 1.63F, 0.25F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 90.0F, 82.0F, 92.0F, 85.0F, 90.0F, 82.0F, 92.0F, 85.0F, 90.0F, 82.0F, 92.0F, 85.0F, 90.0F, 82.0F, 92.0F, 85.0F),
                box("head_stripe_1_ledge", -0.425F, -2.69F, -3.58F, 0.85F, 0.22F, 0.91F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("head_stripe_1_riser", -0.425F, -3.19F, -2.87F, 0.85F, 0.7F, 0.24F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("head_stripe_1_crown", -0.425F, -3.33F, -2.78F, 0.85F, 0.26F, 2.0F,
                        106.0F, 86.0F, 104.0F, 82.0F, 106.0F, 86.0F, 104.0F, 82.0F, 98.0F, 82.0F, 102.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 98.0F, 82.0F, 102.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("head_stripe_2_face", -2.34F, -2.58F, -3.63F, 0.68F, 1.63F, 0.25F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 90.0F, 82.0F, 92.0F, 85.0F, 90.0F, 82.0F, 92.0F, 85.0F, 90.0F, 82.0F, 92.0F, 85.0F, 90.0F, 82.0F, 92.0F, 85.0F),
                box("head_stripe_2_ledge", -2.34F, -2.69F, -3.58F, 0.68F, 0.22F, 0.91F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("head_stripe_2_riser", -2.34F, -3.19F, -2.87F, 0.68F, 0.7F, 0.24F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("head_stripe_2_crown", -2.34F, -3.33F, -2.78F, 0.68F, 0.26F, 2.0F,
                        106.0F, 86.0F, 104.0F, 82.0F, 106.0F, 86.0F, 104.0F, 82.0F, 98.0F, 82.0F, 102.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 98.0F, 82.0F, 102.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F)),
                "snout", snout, "ear_left", earLeft, "brow_left", browLeft, "tusk_left", tuskLeft, "ear_right", earRight, "brow_right", browRight, "tusk_right", tuskRight);
        ModelPart tailTip = part(PartPose.offsetAndRotation(0.0F, 0.0F, 1.2F, -0.384F, 0.0F, 0.0F),
                List.of(
                box("tail_tip", -0.5F, -0.4F, -0.05F, 1.0F, 0.85F, 0.85F,
                        88.0F, 84.0F, 86.0F, 82.0F, 88.0F, 84.0F, 86.0F, 82.0F, 86.0F, 82.0F, 88.0F, 84.0F, 86.0F, 82.0F, 88.0F, 84.0F, 86.0F, 82.0F, 88.0F, 84.0F, 86.0F, 82.0F, 88.0F, 84.0F)));
        ModelPart tail = part(PartPose.offsetAndRotation(0.0F, -1.25F, 6.45F, -0.4887F, 0.0F, 0.0F),
                List.of(
                box("tail_stem", -0.4F, -0.4F, -0.1F, 0.8F, 0.8F, 1.45F,
                        84.0F, 85.0F, 82.0F, 82.0F, 84.0F, 85.0F, 82.0F, 82.0F, 77.0F, 82.0F, 80.0F, 84.0F, 73.0F, 82.0F, 75.0F, 84.0F, 77.0F, 82.0F, 80.0F, 84.0F, 73.0F, 82.0F, 75.0F, 84.0F)),
                "tail_tip", tailTip);
        ModelPart body = part(PartPose.offset(0.0F, -5.0F, 0.0F),
                List.of(
                box("barrel_torso", -4.0F, -5.45F, -2.8F, 8.0F, 7.3F, 8.95F,
                        93.0F, 19.0F, 77.0F, 1.0F, 111.0F, 19.0F, 95.0F, 1.0F, 19.0F, 1.0F, 37.0F, 16.0F, 1.0F, 1.0F, 17.0F, 16.0F, 57.0F, 1.0F, 75.0F, 16.0F, 39.0F, 1.0F, 55.0F, 16.0F),
                box("shoulder_mass", -3.95F, -5.9F, -4.15F, 7.9F, 7.1F, 3.9F,
                        73.0F, 29.0F, 57.0F, 21.0F, 91.0F, 29.0F, 75.0F, 21.0F, 19.0F, 21.0F, 27.0F, 35.0F, 1.0F, 21.0F, 17.0F, 35.0F, 47.0F, 21.0F, 55.0F, 35.0F, 29.0F, 21.0F, 45.0F, 35.0F),
                box("rump_cap", -3.6F, -4.95F, 5.9F, 7.2F, 6.45F, 0.75F,
                        19.0F, 39.0F, 5.0F, 37.0F, 35.0F, 39.0F, 21.0F, 37.0F, 109.0F, 21.0F, 111.0F, 34.0F, 93.0F, 21.0F, 107.0F, 34.0F, 1.0F, 37.0F, 3.0F, 50.0F, 113.0F, 21.0F, 127.0F, 34.0F),
                box("dark_underside", -3.4F, 1.75F, -2.5F, 6.8F, 0.4F, 8.15F,
                        119.0F, 53.0F, 105.0F, 37.0F, 15.0F, 71.0F, 1.0F, 55.0F, 53.0F, 37.0F, 69.0F, 39.0F, 37.0F, 37.0F, 51.0F, 39.0F, 87.0F, 37.0F, 103.0F, 39.0F, 71.0F, 37.0F, 85.0F, 39.0F),
                box("back_stripe_0_neck", 1.66F, -6.02F, -4.27F, 0.68F, 0.71F, 0.24F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_0_shoulder", 1.66F, -6.14F, -4.17F, 0.68F, 0.27F, 3.99F,
                        120.0F, 90.0F, 118.0F, 82.0F, 120.0F, 90.0F, 118.0F, 82.0F, 108.0F, 82.0F, 116.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 108.0F, 82.0F, 116.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_0_step", 1.66F, -6.1F, -0.32F, 0.68F, 0.65F, 0.25F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_0_length", 1.66F, -5.72F, -0.2F, 0.68F, 0.3F, 6.38F,
                        33.0F, 105.0F, 31.0F, 92.0F, 37.0F, 105.0F, 35.0F, 92.0F, 1.0F, 92.0F, 14.0F, 94.0F, 94.0F, 82.0F, 96.0F, 84.0F, 16.0F, 92.0F, 29.0F, 94.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_0_rump", 1.66F, -5.63F, 6.07F, 0.68F, 0.69F, 0.24F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_0_end", 1.66F, -5.17F, 6.18F, 0.68F, 0.26F, 0.52F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_1_neck", -0.425F, -6.02F, -4.27F, 0.85F, 0.71F, 0.24F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_1_shoulder", -0.425F, -6.14F, -4.17F, 0.85F, 0.27F, 3.99F,
                        120.0F, 90.0F, 118.0F, 82.0F, 120.0F, 90.0F, 118.0F, 82.0F, 108.0F, 82.0F, 116.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 108.0F, 82.0F, 116.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_1_step", -0.425F, -6.1F, -0.32F, 0.85F, 0.65F, 0.25F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_1_length", -0.425F, -5.72F, -0.2F, 0.85F, 0.3F, 6.38F,
                        71.0F, 105.0F, 69.0F, 92.0F, 75.0F, 105.0F, 73.0F, 92.0F, 39.0F, 92.0F, 52.0F, 94.0F, 94.0F, 82.0F, 96.0F, 84.0F, 54.0F, 92.0F, 67.0F, 94.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_1_rump", -0.425F, -5.63F, 6.07F, 0.85F, 0.69F, 0.24F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_1_end", -0.425F, -5.17F, 6.18F, 0.85F, 0.26F, 0.52F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_2_neck", -2.34F, -6.02F, -4.27F, 0.68F, 0.71F, 0.24F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_2_shoulder", -2.34F, -6.14F, -4.17F, 0.68F, 0.27F, 3.99F,
                        120.0F, 90.0F, 118.0F, 82.0F, 120.0F, 90.0F, 118.0F, 82.0F, 108.0F, 82.0F, 116.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 108.0F, 82.0F, 116.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_2_step", -2.34F, -6.1F, -0.32F, 0.68F, 0.65F, 0.25F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_2_length", -2.34F, -5.72F, -0.2F, 0.68F, 0.3F, 6.38F,
                        109.0F, 105.0F, 107.0F, 92.0F, 113.0F, 105.0F, 111.0F, 92.0F, 77.0F, 92.0F, 90.0F, 94.0F, 94.0F, 82.0F, 96.0F, 84.0F, 92.0F, 92.0F, 105.0F, 94.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_2_rump", -2.34F, -5.63F, 6.07F, 0.68F, 0.69F, 0.24F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F),
                box("back_stripe_2_end", -2.34F, -5.17F, 6.18F, 0.68F, 0.26F, 0.52F,
                        96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F, 94.0F, 82.0F, 96.0F, 84.0F)),
                "head", head, "tail", tail);
        ModelPart legFrontLeft = part(PartPose.offset(-2.75F, -3.7F, -4.1F),
                List.of(
                box("leg_front_left_upper", -1.05F, -0.5F, -1.05F, 2.1F, 1.6F, 2.1F,
                        47.0F, 86.0F, 43.0F, 82.0F, 47.0F, 86.0F, 43.0F, 82.0F, 37.0F, 82.0F, 41.0F, 85.0F, 37.0F, 82.0F, 41.0F, 85.0F, 37.0F, 82.0F, 41.0F, 85.0F, 37.0F, 82.0F, 41.0F, 85.0F),
                box("leg_front_left_shin", -0.88F, 0.85F, -0.88F, 1.76F, 2.25F, 1.76F,
                        53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F),
                box("leg_front_left_hoof", -0.92F, 3.05F, -0.98F, 1.84F, 0.65F, 1.88F,
                        71.0F, 86.0F, 67.0F, 82.0F, 71.0F, 86.0F, 67.0F, 82.0F, 61.0F, 82.0F, 65.0F, 84.0F, 55.0F, 82.0F, 59.0F, 84.0F, 61.0F, 82.0F, 65.0F, 84.0F, 61.0F, 82.0F, 65.0F, 84.0F)));
        ModelPart legRearLeft = part(PartPose.offset(-2.75F, -3.7F, 4.65F),
                List.of(
                box("leg_rear_left_upper", -1.05F, -0.5F, -1.05F, 2.1F, 1.6F, 2.1F,
                        47.0F, 86.0F, 43.0F, 82.0F, 47.0F, 86.0F, 43.0F, 82.0F, 37.0F, 82.0F, 41.0F, 85.0F, 37.0F, 82.0F, 41.0F, 85.0F, 37.0F, 82.0F, 41.0F, 85.0F, 37.0F, 82.0F, 41.0F, 85.0F),
                box("leg_rear_left_shin", -0.88F, 0.85F, -0.88F, 1.76F, 2.25F, 1.76F,
                        53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F),
                box("leg_rear_left_hoof", -0.92F, 3.05F, -0.98F, 1.84F, 0.65F, 1.88F,
                        71.0F, 86.0F, 67.0F, 82.0F, 71.0F, 86.0F, 67.0F, 82.0F, 61.0F, 82.0F, 65.0F, 84.0F, 55.0F, 82.0F, 59.0F, 84.0F, 61.0F, 82.0F, 65.0F, 84.0F, 61.0F, 82.0F, 65.0F, 84.0F)));
        ModelPart legFrontRight = part(PartPose.offset(2.75F, -3.7F, -4.1F),
                List.of(
                box("leg_front_right_upper", -1.05F, -0.5F, -1.05F, 2.1F, 1.6F, 2.1F,
                        47.0F, 86.0F, 43.0F, 82.0F, 47.0F, 86.0F, 43.0F, 82.0F, 37.0F, 82.0F, 41.0F, 85.0F, 37.0F, 82.0F, 41.0F, 85.0F, 37.0F, 82.0F, 41.0F, 85.0F, 37.0F, 82.0F, 41.0F, 85.0F),
                box("leg_front_right_shin", -0.88F, 0.85F, -0.88F, 1.76F, 2.25F, 1.76F,
                        53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F),
                box("leg_front_right_hoof", -0.92F, 3.05F, -0.98F, 1.84F, 0.65F, 1.88F,
                        71.0F, 86.0F, 67.0F, 82.0F, 71.0F, 86.0F, 67.0F, 82.0F, 61.0F, 82.0F, 65.0F, 84.0F, 55.0F, 82.0F, 59.0F, 84.0F, 61.0F, 82.0F, 65.0F, 84.0F, 61.0F, 82.0F, 65.0F, 84.0F)));
        ModelPart legRearRight = part(PartPose.offset(2.75F, -3.7F, 4.65F),
                List.of(
                box("leg_rear_right_upper", -1.05F, -0.5F, -1.05F, 2.1F, 1.6F, 2.1F,
                        47.0F, 86.0F, 43.0F, 82.0F, 47.0F, 86.0F, 43.0F, 82.0F, 37.0F, 82.0F, 41.0F, 85.0F, 37.0F, 82.0F, 41.0F, 85.0F, 37.0F, 82.0F, 41.0F, 85.0F, 37.0F, 82.0F, 41.0F, 85.0F),
                box("leg_rear_right_shin", -0.88F, 0.85F, -0.88F, 1.76F, 2.25F, 1.76F,
                        53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F, 49.0F, 82.0F, 53.0F, 86.0F),
                box("leg_rear_right_hoof", -0.92F, 3.05F, -0.98F, 1.84F, 0.65F, 1.88F,
                        71.0F, 86.0F, 67.0F, 82.0F, 71.0F, 86.0F, 67.0F, 82.0F, 61.0F, 82.0F, 65.0F, 84.0F, 55.0F, 82.0F, 59.0F, 84.0F, 61.0F, 82.0F, 65.0F, 84.0F, 61.0F, 82.0F, 65.0F, 84.0F)));
        ModelPart root = part(new PartPose(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F, RENDER_SCALE, RENDER_SCALE, RENDER_SCALE),
                List.of(),
                "body", body, "leg_front_left", legFrontLeft, "leg_rear_left", legRearLeft, "leg_front_right", legFrontRight, "leg_rear_right", legRearRight);
        return part(PartPose.ZERO, List.of(), "root", root);
    }
}
