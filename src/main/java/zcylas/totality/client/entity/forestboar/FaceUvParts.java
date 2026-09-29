package zcylas.totality.client.entity.forestboar;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds model parts whose cuboids carry per-face UV rectangles, as Blockbench's "free" (per-face UV) projects do.
 * Vanilla's {@code CubeListBuilder} only knows the box-UV layout, so each face polygon of an ordinary vanilla
 * {@link ModelPart.Cube} is re-mapped to its own rectangle; vertices, normals and rendering stay vanilla.
 */
final class FaceUvParts {

    /** The order of a six-faced {@link ModelPart.Cube}'s polygons (see its constructor). */
    private static final Direction[] POLYGON_ORDER = {Direction.DOWN, Direction.UP, Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH};

    private FaceUvParts() {}

    /**
     * A cuboid at (x, y, z) of size (w, h, d), in ModelPart space; {@code uv} holds (u0, v0, u1, v1) for each face in
     * {@link #POLYGON_ORDER}, already converted to vanilla's orientation by the generator.
     */
    static ModelPart.Cube box(String name, float x, float y, float z, float w, float h, float d, float... uv) {
        if (uv.length != 24) throw new IllegalArgumentException(name + ": expected 6 face UV rectangles");
        ModelPart.Cube cube = new ModelPart.Cube(0, 0, x, y, z, w, h, d, 0.0F, 0.0F, 0.0F, false,
                ForestBoarGeometry.TEXTURE_WIDTH, ForestBoarGeometry.TEXTURE_HEIGHT, EnumSet.allOf(Direction.class));
        for (int i = 0; i < POLYGON_ORDER.length; i++) {
            cube.polygons[i] = new ModelPart.Polygon(cube.polygons[i].vertices(), uv[i * 4], uv[i * 4 + 1], uv[i * 4 + 2], uv[i * 4 + 3],
                    ForestBoarGeometry.TEXTURE_WIDTH, ForestBoarGeometry.TEXTURE_HEIGHT, false, POLYGON_ORDER[i]);
        }
        return cube;
    }

    /** A part with its pose (also its reset pose) and named children, given as alternating name, part. */
    static ModelPart part(PartPose pose, List<ModelPart.Cube> cubes, Object... namedChildren) {
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
