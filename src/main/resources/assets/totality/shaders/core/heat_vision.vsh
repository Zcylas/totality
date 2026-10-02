#version 330

// Totality Heat Vision V2: shared vertex stage of the beam ribbon and the impact hotspot.
// UV0.x runs across the shape (-1 .. 1), UV0.y along it (blocks from the eyes for the beam, -1 .. 1 for the hotspot).

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
