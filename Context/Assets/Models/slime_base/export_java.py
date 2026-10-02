#!/usr/bin/env python3
"""Converts slime_base.bbmodel into a Minecraft (Mojang-mapped, 26.x) ModelPart geometry class.

    python3 export_java.py   -> export/SlimeBaseGeometry.java

Same conventions as Totality's Forest Boar generator (Blockbench "free" project with per-face UVs -> ModelPart):
  position        (x, y, z)       -> (-x, 24 - y, z)        (a 180-degree turn about Z)
  group rotation  (rx, ry, rz)    -> (-rx, -ry, rz) radians
  face UVs        Blockbench east/west are Minecraft's WEST/EAST polygons; north/south map directly;
                  up/down rectangles are flipped on both axes: (u1, v1, u2, v2) -> (u2, v2, u1, v1)
Polygon order of a ModelPart.Cube: DOWN, UP, WEST, NORTH, EAST, SOUTH.
The class is self-contained (its own per-face UV helper, as FaceUvParts does for the boar).
"""
import json
import math
from pathlib import Path

HERE = Path(__file__).parent
SRC = HERE / "slime_base.bbmodel"
OUT = HERE / "export" / "SlimeBaseGeometry.java"
HEIGHT_BLOCKS = 0.6  # reference sheet: the small slime is 0.6 blocks tall

model = json.loads(SRC.read_text())
elements = {e["uuid"]: e for e in model["elements"]}
groups = {g["uuid"]: g for g in model.get("groups", [])}  # Blockbench 5 keeps group properties outside the outliner


def resolve(node):
    """Outliner node -> group dict with name/origin/rotation and resolved children (element uuids stay strings)."""
    g = dict(groups.get(node["uuid"], {}), **{k: v for k, v in node.items() if k != "children"})
    g["children"] = [resolve(c) if isinstance(c, dict) else c for c in node["children"]]
    return g
tex_w = model["resolution"]["width"]
tex_h = model["resolution"]["height"]
ys = [v for e in model["elements"] for v in (e["from"][1], e["to"][1])]
rest_height = max(ys) - min(ys)


def f(v):
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    return (s if "." in s else s + ".0") + "F"


def mc(p):
    return [-p[0], 24 - p[1], p[2]]


def flip(uv):
    return [uv[2], uv[3], uv[0], uv[1]]


def cube_code(e, pivot_mc, indent):
    lo = [min(a, b) for a, b in zip(mc(e["from"]), mc(e["to"]))]
    size = [abs(a - b) for a, b in zip(e["from"], e["to"])]
    rel = [lo[i] - pivot_mc[i] for i in range(3)]
    fc = e["faces"]
    uvs = flip(fc["up"]["uv"]) + flip(fc["down"]["uv"]) + fc["east"]["uv"] + fc["north"]["uv"] + fc["west"]["uv"] + fc["south"]["uv"]
    return (f'{indent}box("{e["name"]}", {", ".join(f(v) for v in rel + size)},\n'
            f'{indent}        {", ".join(f(v) for v in uvs)})')


lines = []
counter = [0]


def group_code(g, parent_mc):
    """Emits the part for group g and returns its variable name."""
    pivot = mc(g["origin"])
    rot = g.get("rotation", [0, 0, 0])
    child_vars = [(c["name"], group_code(c, pivot)) for c in g["children"] if isinstance(c, dict)]
    cubes = [elements[c] for c in g["children"] if isinstance(c, str)]
    off = [pivot[i] - parent_mc[i] for i in range(3)]
    if any(rot):
        r = [-math.radians(rot[0]), -math.radians(rot[1]), math.radians(rot[2])]
        pose = f"PartPose.offsetAndRotation({', '.join(f(v) for v in off + r)})"
    else:
        pose = f"PartPose.offset({', '.join(f(v) for v in off)})"
    var = "".join(w.capitalize() if i else w for i, w in enumerate(g["name"].split("_")))
    cube_list = ",\n".join(cube_code(e, pivot, "                ") for e in cubes)
    named = "".join(f', "{n}", {v}' for n, v in child_vars)
    lines.append(f"        ModelPart {var} = part({pose},\n                List.of({chr(10) if cubes else ''}{cube_list}){named});")
    return var


root = resolve(model["outliner"][0])
root_var = group_code(root, [0, 0, 0])
n_cubes = len(model["elements"])
scale = HEIGHT_BLOCKS * 16 / rest_height

java = f"""package zcylas.totality.client.entity.slime;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GENERATED from slime_base.bbmodel by Totality-Models/slime_base/export_java.py. Do not edit: edit the .bbmodel and
 * re-run the tool. Shared Small Slime base: {n_cubes} cuboids with per-face UVs on the {tex_w}x{tex_h} grayscale texture.
 * Parts: "{root['name']}" (pivot on the ground, for squash/stretch) > "body" (recolourable body) and "eyes" > eye_right,
 * eye_left (own texture region, so they can be coloured independently of the body).
 */
final class SlimeBaseGeometry {{

    private SlimeBaseGeometry() {{}}

    static final int TEXTURE_WIDTH = {tex_w};
    static final int TEXTURE_HEIGHT = {tex_h};
    /** {HEIGHT_BLOCKS} blocks (the reference sheet) over the model's rest height ({rest_height:g} units); apply on the root part. */
    static final float RENDER_SCALE = {f(scale)};
    static final float HEIGHT_BLOCKS = {f(HEIGHT_BLOCKS)};

    /** The order of a six-faced {{@link ModelPart.Cube}}'s polygons (see its constructor). */
    private static final Direction[] POLYGON_ORDER = {{Direction.DOWN, Direction.UP, Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH}};

    static ModelPart createRoot() {{
{chr(10).join(lines)}
        return part(PartPose.ZERO, List.of(), "{root['name']}", {root_var});
    }}

    private static ModelPart.Cube box(String name, float x, float y, float z, float w, float h, float d, float... uv) {{
        if (uv.length != 24) throw new IllegalArgumentException(name + ": expected 6 face UV rectangles");
        ModelPart.Cube cube = new ModelPart.Cube(0, 0, x, y, z, w, h, d, 0.0F, 0.0F, 0.0F, false,
                TEXTURE_WIDTH, TEXTURE_HEIGHT, EnumSet.allOf(Direction.class));
        for (int i = 0; i < POLYGON_ORDER.length; i++) {{
            cube.polygons[i] = new ModelPart.Polygon(cube.polygons[i].vertices(), uv[i * 4], uv[i * 4 + 1], uv[i * 4 + 2], uv[i * 4 + 3],
                    TEXTURE_WIDTH, TEXTURE_HEIGHT, false, POLYGON_ORDER[i]);
        }}
        return cube;
    }}

    private static ModelPart part(PartPose pose, List<ModelPart.Cube> cubes, Object... namedChildren) {{
        Map<String, ModelPart> children = new LinkedHashMap<>();
        for (int i = 0; i < namedChildren.length; i += 2) {{
            children.put((String) namedChildren[i], (ModelPart) namedChildren[i + 1]);
        }}
        ModelPart part = new ModelPart(cubes, children);
        part.setInitialPose(pose);
        part.loadPose(pose);
        return part;
    }}
}}
"""
OUT.parent.mkdir(exist_ok=True)
OUT.write_text(java)
print(OUT, n_cubes, "cuboids, render scale", round(scale, 4))
