#version 330

// Totality layered fire explosion (VFX Experiment 3, Fireball V2): vertex stage.
// UV0 packs the element's local corner and its quantisation grid (decoded in vfx_fire.fsh); Color packs heat, erosion,
// seed and opacity.

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
}
