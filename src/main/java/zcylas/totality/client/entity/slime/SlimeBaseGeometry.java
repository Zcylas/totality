package zcylas.totality.client.entity.slime;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GENERATED from slime_base.bbmodel by Totality-Models/slime_base/export_java.py. Do not edit: edit the .bbmodel and
 * re-run the tool. Shared Small Slime base: 43 cuboids with per-face UVs on the 128x128 grayscale texture.
 * Parts: "root" (pivot on the ground, for squash/stretch) > "body" (recolourable body) and "eyes" > eye_right,
 * eye_left (own texture region, so they can be coloured independently of the body).
 */
final class SlimeBaseGeometry {

    private SlimeBaseGeometry() {}

    static final int TEXTURE_WIDTH = 128;
    static final int TEXTURE_HEIGHT = 128;
    /** 0.6 blocks (the reference sheet) over the model's rest height (15 units); apply on the root part. */
    static final float RENDER_SCALE = 0.64F;
    static final float HEIGHT_BLOCKS = 0.6F;

    /** The order of a six-faced {@link ModelPart.Cube}'s polygons (see its constructor). */
    private static final Direction[] POLYGON_ORDER = {Direction.DOWN, Direction.UP, Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH};

    static ModelPart createRoot() {
        ModelPart body = part(PartPose.offset(0.0F, 0.0F, 0.0F),
                List.of(
                box("body_00", -5.0F, -12.0F, -6.0F, 10.0F, 12.0F, 12.0F,
                        70.0F, 62.0F, 50.0F, 38.0F, 110.0F, 62.0F, 90.0F, 38.0F, 88.0F, 6.0F, 112.0F, 30.0F, 10.0F, 6.0F, 30.0F, 30.0F, 8.0F, 36.0F, 32.0F, 60.0F, 50.0F, 6.0F, 70.0F, 30.0F),
                box("body_01", -6.0F, -12.0F, -5.0F, 12.0F, 12.0F, 10.0F,
                        72.0F, 60.0F, 48.0F, 40.0F, 112.0F, 60.0F, 88.0F, 40.0F, 90.0F, 6.0F, 110.0F, 30.0F, 8.0F, 6.0F, 32.0F, 30.0F, 10.0F, 36.0F, 30.0F, 60.0F, 48.0F, 6.0F, 72.0F, 30.0F),
                box("body_02", -5.0F, -13.0F, -5.0F, 10.0F, 13.0F, 10.0F,
                        70.0F, 60.0F, 50.0F, 40.0F, 110.0F, 60.0F, 90.0F, 40.0F, 90.0F, 4.0F, 110.0F, 30.0F, 10.0F, 4.0F, 30.0F, 30.0F, 10.0F, 34.0F, 30.0F, 60.0F, 50.0F, 4.0F, 70.0F, 30.0F),
                box("body_03", -6.0F, -13.0F, -4.0F, 12.0F, 13.0F, 8.0F,
                        72.0F, 58.0F, 48.0F, 42.0F, 112.0F, 58.0F, 88.0F, 42.0F, 92.0F, 4.0F, 108.0F, 30.0F, 8.0F, 4.0F, 32.0F, 30.0F, 12.0F, 34.0F, 28.0F, 60.0F, 48.0F, 4.0F, 72.0F, 30.0F),
                box("body_04", -4.0F, -13.0F, -6.0F, 8.0F, 13.0F, 12.0F,
                        68.0F, 62.0F, 52.0F, 38.0F, 108.0F, 62.0F, 92.0F, 38.0F, 88.0F, 4.0F, 112.0F, 30.0F, 12.0F, 4.0F, 28.0F, 30.0F, 8.0F, 34.0F, 32.0F, 60.0F, 52.0F, 4.0F, 68.0F, 30.0F),
                box("body_05", -3.0F, -12.0F, -7.0F, 6.0F, 12.0F, 14.0F,
                        66.0F, 64.0F, 54.0F, 36.0F, 106.0F, 64.0F, 94.0F, 36.0F, 86.0F, 6.0F, 114.0F, 30.0F, 14.0F, 6.0F, 26.0F, 30.0F, 6.0F, 36.0F, 34.0F, 60.0F, 54.0F, 6.0F, 66.0F, 30.0F),
                box("body_06", -7.0F, -12.0F, -3.0F, 14.0F, 12.0F, 6.0F,
                        74.0F, 56.0F, 46.0F, 44.0F, 114.0F, 56.0F, 86.0F, 44.0F, 94.0F, 6.0F, 106.0F, 30.0F, 6.0F, 6.0F, 34.0F, 30.0F, 14.0F, 36.0F, 26.0F, 60.0F, 46.0F, 6.0F, 74.0F, 30.0F),
                box("body_07", -4.0F, -14.0F, -4.0F, 8.0F, 14.0F, 8.0F,
                        68.0F, 58.0F, 52.0F, 42.0F, 108.0F, 58.0F, 92.0F, 42.0F, 92.0F, 2.0F, 108.0F, 30.0F, 12.0F, 2.0F, 28.0F, 30.0F, 12.0F, 32.0F, 28.0F, 60.0F, 52.0F, 2.0F, 68.0F, 30.0F),
                box("body_08", -3.0F, -14.0F, -5.0F, 6.0F, 14.0F, 10.0F,
                        66.0F, 60.0F, 54.0F, 40.0F, 106.0F, 60.0F, 94.0F, 40.0F, 90.0F, 2.0F, 110.0F, 30.0F, 14.0F, 2.0F, 26.0F, 30.0F, 10.0F, 32.0F, 30.0F, 60.0F, 54.0F, 2.0F, 66.0F, 30.0F),
                box("body_09", -5.0F, -14.0F, -3.0F, 10.0F, 14.0F, 6.0F,
                        70.0F, 56.0F, 50.0F, 44.0F, 110.0F, 56.0F, 90.0F, 44.0F, 94.0F, 2.0F, 106.0F, 30.0F, 10.0F, 2.0F, 30.0F, 30.0F, 14.0F, 32.0F, 26.0F, 60.0F, 50.0F, 2.0F, 70.0F, 30.0F),
                box("body_10", -2.0F, -13.0F, -7.0F, 4.0F, 13.0F, 14.0F,
                        64.0F, 64.0F, 56.0F, 36.0F, 104.0F, 64.0F, 96.0F, 36.0F, 86.0F, 4.0F, 114.0F, 30.0F, 16.0F, 4.0F, 24.0F, 30.0F, 6.0F, 34.0F, 34.0F, 60.0F, 56.0F, 4.0F, 64.0F, 30.0F),
                box("body_11", -7.0F, -13.0F, -2.0F, 14.0F, 13.0F, 4.0F,
                        74.0F, 54.0F, 46.0F, 46.0F, 114.0F, 54.0F, 86.0F, 46.0F, 96.0F, 4.0F, 104.0F, 30.0F, 6.0F, 4.0F, 34.0F, 30.0F, 16.0F, 34.0F, 24.0F, 60.0F, 46.0F, 4.0F, 74.0F, 30.0F),
                box("body_12", -2.0F, -15.0F, -2.0F, 4.0F, 15.0F, 4.0F,
                        64.0F, 54.0F, 56.0F, 46.0F, 104.0F, 54.0F, 96.0F, 46.0F, 96.0F, 0.0F, 104.0F, 30.0F, 16.0F, 0.0F, 24.0F, 30.0F, 16.0F, 30.0F, 24.0F, 60.0F, 56.0F, 0.0F, 64.0F, 30.0F),
                box("body_13", -1.0F, -15.0F, -3.0F, 2.0F, 15.0F, 6.0F,
                        62.0F, 56.0F, 58.0F, 44.0F, 102.0F, 56.0F, 98.0F, 44.0F, 94.0F, 0.0F, 106.0F, 30.0F, 18.0F, 0.0F, 22.0F, 30.0F, 14.0F, 30.0F, 26.0F, 60.0F, 58.0F, 0.0F, 62.0F, 30.0F),
                box("body_14", -3.0F, -15.0F, -1.0F, 6.0F, 15.0F, 2.0F,
                        66.0F, 52.0F, 54.0F, 48.0F, 106.0F, 52.0F, 94.0F, 48.0F, 98.0F, 0.0F, 102.0F, 30.0F, 14.0F, 0.0F, 26.0F, 30.0F, 18.0F, 30.0F, 22.0F, 60.0F, 54.0F, 0.0F, 66.0F, 30.0F),
                box("body_15", -6.0F, -11.0F, -7.0F, 12.0F, 10.0F, 14.0F,
                        72.0F, 64.0F, 48.0F, 36.0F, 112.0F, 64.0F, 88.0F, 36.0F, 86.0F, 8.0F, 114.0F, 28.0F, 8.0F, 8.0F, 32.0F, 28.0F, 6.0F, 38.0F, 34.0F, 58.0F, 48.0F, 8.0F, 72.0F, 28.0F),
                box("body_16", -7.0F, -11.0F, -6.0F, 14.0F, 10.0F, 12.0F,
                        74.0F, 62.0F, 46.0F, 38.0F, 114.0F, 62.0F, 86.0F, 38.0F, 88.0F, 8.0F, 112.0F, 28.0F, 6.0F, 8.0F, 34.0F, 28.0F, 8.0F, 38.0F, 32.0F, 58.0F, 46.0F, 8.0F, 74.0F, 28.0F),
                box("body_17", -6.0F, -12.0F, -6.0F, 12.0F, 11.0F, 12.0F,
                        72.0F, 62.0F, 48.0F, 38.0F, 112.0F, 62.0F, 88.0F, 38.0F, 88.0F, 6.0F, 112.0F, 28.0F, 8.0F, 6.0F, 32.0F, 28.0F, 8.0F, 36.0F, 32.0F, 58.0F, 48.0F, 6.0F, 72.0F, 28.0F),
                box("body_18", -7.0F, -12.0F, -5.0F, 14.0F, 11.0F, 10.0F,
                        74.0F, 60.0F, 46.0F, 40.0F, 114.0F, 60.0F, 86.0F, 40.0F, 90.0F, 6.0F, 110.0F, 28.0F, 6.0F, 6.0F, 34.0F, 28.0F, 10.0F, 36.0F, 30.0F, 58.0F, 46.0F, 6.0F, 74.0F, 28.0F),
                box("body_19", -5.0F, -12.0F, -7.0F, 10.0F, 11.0F, 14.0F,
                        70.0F, 64.0F, 50.0F, 36.0F, 110.0F, 64.0F, 90.0F, 36.0F, 86.0F, 6.0F, 114.0F, 28.0F, 10.0F, 6.0F, 30.0F, 28.0F, 6.0F, 36.0F, 34.0F, 58.0F, 50.0F, 6.0F, 70.0F, 28.0F),
                box("body_20", -4.0F, -11.0F, -8.0F, 8.0F, 10.0F, 16.0F,
                        68.0F, 66.0F, 52.0F, 34.0F, 108.0F, 66.0F, 92.0F, 34.0F, 84.0F, 8.0F, 116.0F, 28.0F, 12.0F, 8.0F, 28.0F, 28.0F, 4.0F, 38.0F, 36.0F, 58.0F, 52.0F, 8.0F, 68.0F, 28.0F),
                box("body_21", -8.0F, -11.0F, -4.0F, 16.0F, 10.0F, 8.0F,
                        76.0F, 58.0F, 44.0F, 42.0F, 116.0F, 58.0F, 84.0F, 42.0F, 92.0F, 8.0F, 108.0F, 28.0F, 4.0F, 8.0F, 36.0F, 28.0F, 12.0F, 38.0F, 28.0F, 58.0F, 44.0F, 8.0F, 76.0F, 28.0F),
                box("body_22", -2.0F, -12.0F, -8.0F, 4.0F, 11.0F, 16.0F,
                        64.0F, 66.0F, 56.0F, 34.0F, 104.0F, 66.0F, 96.0F, 34.0F, 84.0F, 6.0F, 116.0F, 28.0F, 16.0F, 6.0F, 24.0F, 28.0F, 4.0F, 36.0F, 36.0F, 58.0F, 56.0F, 6.0F, 64.0F, 28.0F),
                box("body_23", -8.0F, -12.0F, -2.0F, 16.0F, 11.0F, 4.0F,
                        76.0F, 54.0F, 44.0F, 46.0F, 116.0F, 54.0F, 84.0F, 46.0F, 96.0F, 6.0F, 104.0F, 28.0F, 4.0F, 6.0F, 36.0F, 28.0F, 16.0F, 36.0F, 24.0F, 58.0F, 44.0F, 6.0F, 76.0F, 28.0F),
                box("body_24", -7.0F, -10.0F, -7.0F, 14.0F, 8.0F, 14.0F,
                        74.0F, 64.0F, 46.0F, 36.0F, 114.0F, 64.0F, 86.0F, 36.0F, 86.0F, 10.0F, 114.0F, 26.0F, 6.0F, 10.0F, 34.0F, 26.0F, 6.0F, 40.0F, 34.0F, 56.0F, 46.0F, 10.0F, 74.0F, 26.0F),
                box("body_25", -8.0F, -10.0F, -6.0F, 16.0F, 8.0F, 12.0F,
                        76.0F, 62.0F, 44.0F, 38.0F, 116.0F, 62.0F, 84.0F, 38.0F, 88.0F, 10.0F, 112.0F, 26.0F, 4.0F, 10.0F, 36.0F, 26.0F, 8.0F, 40.0F, 32.0F, 56.0F, 44.0F, 10.0F, 76.0F, 26.0F),
                box("body_26", -6.0F, -10.0F, -8.0F, 12.0F, 8.0F, 16.0F,
                        72.0F, 66.0F, 48.0F, 34.0F, 112.0F, 66.0F, 88.0F, 34.0F, 84.0F, 10.0F, 116.0F, 26.0F, 8.0F, 10.0F, 32.0F, 26.0F, 4.0F, 40.0F, 36.0F, 56.0F, 48.0F, 10.0F, 72.0F, 26.0F),
                box("body_27", -4.0F, -10.0F, -9.0F, 8.0F, 8.0F, 18.0F,
                        68.0F, 68.0F, 52.0F, 32.0F, 108.0F, 68.0F, 92.0F, 32.0F, 82.0F, 10.0F, 118.0F, 26.0F, 12.0F, 10.0F, 28.0F, 26.0F, 2.0F, 40.0F, 38.0F, 56.0F, 52.0F, 10.0F, 68.0F, 26.0F),
                box("body_28", -9.0F, -10.0F, -4.0F, 18.0F, 8.0F, 8.0F,
                        78.0F, 58.0F, 42.0F, 42.0F, 118.0F, 58.0F, 82.0F, 42.0F, 92.0F, 10.0F, 108.0F, 26.0F, 2.0F, 10.0F, 38.0F, 26.0F, 12.0F, 40.0F, 28.0F, 56.0F, 42.0F, 10.0F, 78.0F, 26.0F),
                box("body_29", -1.0F, -11.0F, -9.0F, 2.0F, 9.0F, 18.0F,
                        62.0F, 68.0F, 58.0F, 32.0F, 102.0F, 68.0F, 98.0F, 32.0F, 82.0F, 8.0F, 118.0F, 26.0F, 18.0F, 8.0F, 22.0F, 26.0F, 2.0F, 38.0F, 38.0F, 56.0F, 58.0F, 8.0F, 62.0F, 26.0F),
                box("body_30", -9.0F, -11.0F, -1.0F, 18.0F, 9.0F, 2.0F,
                        78.0F, 52.0F, 42.0F, 48.0F, 118.0F, 52.0F, 82.0F, 48.0F, 98.0F, 8.0F, 102.0F, 26.0F, 2.0F, 8.0F, 38.0F, 26.0F, 18.0F, 38.0F, 22.0F, 56.0F, 42.0F, 8.0F, 78.0F, 26.0F),
                box("body_31", -5.0F, -9.0F, -9.0F, 10.0F, 6.0F, 18.0F,
                        70.0F, 68.0F, 50.0F, 32.0F, 110.0F, 68.0F, 90.0F, 32.0F, 82.0F, 12.0F, 118.0F, 24.0F, 10.0F, 12.0F, 30.0F, 24.0F, 2.0F, 42.0F, 38.0F, 54.0F, 50.0F, 12.0F, 70.0F, 24.0F),
                box("body_32", -9.0F, -9.0F, -5.0F, 18.0F, 6.0F, 10.0F,
                        78.0F, 60.0F, 42.0F, 40.0F, 118.0F, 60.0F, 82.0F, 40.0F, 90.0F, 12.0F, 110.0F, 24.0F, 2.0F, 12.0F, 38.0F, 24.0F, 10.0F, 42.0F, 30.0F, 54.0F, 42.0F, 12.0F, 78.0F, 24.0F),
                box("body_33", -10.0F, -8.0F, -2.0F, 20.0F, 5.0F, 4.0F,
                        80.0F, 54.0F, 40.0F, 46.0F, 120.0F, 54.0F, 80.0F, 46.0F, 96.0F, 14.0F, 104.0F, 24.0F, 0.0F, 14.0F, 40.0F, 24.0F, 16.0F, 44.0F, 24.0F, 54.0F, 40.0F, 14.0F, 80.0F, 24.0F),
                box("body_34", -10.0F, -9.0F, -1.0F, 20.0F, 6.0F, 2.0F,
                        80.0F, 52.0F, 40.0F, 48.0F, 120.0F, 52.0F, 80.0F, 48.0F, 98.0F, 12.0F, 102.0F, 24.0F, 0.0F, 12.0F, 40.0F, 24.0F, 18.0F, 42.0F, 22.0F, 54.0F, 40.0F, 12.0F, 80.0F, 24.0F),
                box("body_35", -7.0F, -8.0F, -8.0F, 14.0F, 4.0F, 16.0F,
                        74.0F, 66.0F, 46.0F, 34.0F, 114.0F, 66.0F, 86.0F, 34.0F, 84.0F, 14.0F, 116.0F, 22.0F, 6.0F, 14.0F, 34.0F, 22.0F, 4.0F, 44.0F, 36.0F, 52.0F, 46.0F, 14.0F, 74.0F, 22.0F),
                box("body_36", -8.0F, -8.0F, -7.0F, 16.0F, 4.0F, 14.0F,
                        76.0F, 64.0F, 44.0F, 36.0F, 116.0F, 64.0F, 84.0F, 36.0F, 86.0F, 14.0F, 114.0F, 22.0F, 4.0F, 14.0F, 36.0F, 22.0F, 6.0F, 44.0F, 34.0F, 52.0F, 44.0F, 14.0F, 76.0F, 22.0F),
                box("body_37", -6.0F, -7.0F, -9.0F, 12.0F, 3.0F, 18.0F,
                        72.0F, 68.0F, 48.0F, 32.0F, 112.0F, 68.0F, 88.0F, 32.0F, 82.0F, 16.0F, 118.0F, 22.0F, 8.0F, 16.0F, 32.0F, 22.0F, 2.0F, 46.0F, 38.0F, 52.0F, 48.0F, 16.0F, 72.0F, 22.0F),
                box("body_38", -9.0F, -7.0F, -6.0F, 18.0F, 3.0F, 12.0F,
                        78.0F, 62.0F, 42.0F, 38.0F, 118.0F, 62.0F, 82.0F, 38.0F, 88.0F, 16.0F, 112.0F, 22.0F, 2.0F, 16.0F, 38.0F, 22.0F, 8.0F, 46.0F, 32.0F, 52.0F, 42.0F, 16.0F, 78.0F, 22.0F),
                box("body_39", -10.0F, -8.0F, -3.0F, 20.0F, 4.0F, 6.0F,
                        80.0F, 56.0F, 40.0F, 44.0F, 120.0F, 56.0F, 80.0F, 44.0F, 94.0F, 14.0F, 106.0F, 22.0F, 0.0F, 14.0F, 40.0F, 22.0F, 14.0F, 44.0F, 26.0F, 52.0F, 40.0F, 14.0F, 80.0F, 22.0F),
                box("body_40", -10.0F, -7.0F, -4.0F, 20.0F, 2.0F, 8.0F,
                        80.0F, 58.0F, 40.0F, 42.0F, 120.0F, 58.0F, 80.0F, 42.0F, 92.0F, 16.0F, 108.0F, 20.0F, 0.0F, 16.0F, 40.0F, 20.0F, 12.0F, 46.0F, 28.0F, 50.0F, 40.0F, 16.0F, 80.0F, 20.0F)));
        ModelPart eyeRight = part(PartPose.offset(-3.5F, 0.0F, 0.0F),
                List.of(
                box("eye_right_plate", -1.5F, -3.0F, -0.06F, 3.0F, 6.0F, 0.05F,
                        12.0F, 66.0F, 10.0F, 64.0F, 12.0F, 66.0F, 10.0F, 64.0F, 10.0F, 64.0F, 12.0F, 66.0F, 0.0F, 64.0F, 6.0F, 76.0F, 10.0F, 64.0F, 12.0F, 66.0F, 10.0F, 64.0F, 12.0F, 66.0F)));
        ModelPart eyeLeft = part(PartPose.offset(3.5F, 0.0F, 0.0F),
                List.of(
                box("eye_left_plate", -1.5F, -3.0F, -0.06F, 3.0F, 6.0F, 0.05F,
                        12.0F, 66.0F, 10.0F, 64.0F, 12.0F, 66.0F, 10.0F, 64.0F, 10.0F, 64.0F, 12.0F, 66.0F, 0.0F, 64.0F, 6.0F, 76.0F, 10.0F, 64.0F, 12.0F, 66.0F, 10.0F, 64.0F, 12.0F, 66.0F)));
        ModelPart eyes = part(PartPose.offset(0.0F, -6.0F, -9.0F),
                List.of(), "eye_right", eyeRight, "eye_left", eyeLeft);
        ModelPart root = part(PartPose.offset(0.0F, 24.0F, 0.0F),
                List.of(), "body", body, "eyes", eyes);
        return part(PartPose.ZERO, List.of(), "root", root);
    }

    private static ModelPart.Cube box(String name, float x, float y, float z, float w, float h, float d, float... uv) {
        if (uv.length != 24) throw new IllegalArgumentException(name + ": expected 6 face UV rectangles");
        ModelPart.Cube cube = new ModelPart.Cube(0, 0, x, y, z, w, h, d, 0.0F, 0.0F, 0.0F, false,
                TEXTURE_WIDTH, TEXTURE_HEIGHT, EnumSet.allOf(Direction.class));
        for (int i = 0; i < POLYGON_ORDER.length; i++) {
            cube.polygons[i] = new ModelPart.Polygon(cube.polygons[i].vertices(), uv[i * 4], uv[i * 4 + 1], uv[i * 4 + 2], uv[i * 4 + 3],
                    TEXTURE_WIDTH, TEXTURE_HEIGHT, false, POLYGON_ORDER[i]);
        }
        return cube;
    }

    private static ModelPart part(PartPose pose, List<ModelPart.Cube> cubes, Object... namedChildren) {
        Map<String, ModelPart> children = new LinkedHashMap<>();
        for (int i = 0; i < namedChildren.length; i += 2) {
            children.put((String) namedChildren[i], (ModelPart) namedChildren[i + 1]);
        }
        ModelPart part = new ModelPart(cubes, children);
        part.setInitialPose(pose);
        part.loadPose(pose);
        return part;
    }
}
