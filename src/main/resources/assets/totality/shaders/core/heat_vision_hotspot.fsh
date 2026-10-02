#version 330

// Totality Heat Vision V2 impact hotspot: a camera-facing disc at the hit point.
//   texCoord0  -1 .. 1 in both directions (0 = hit point)
//   vertexColor rgb = tint scale, a = strength
// Additive output: a white-hot centre fading through orange to red, with a flickering, slightly ragged edge.

#moj_import <minecraft:globals.glsl>

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float hash(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

float noise1(float x) {
    float i = floor(x);
    float f = fract(x);
    float u = f * f * (3.0 - 2.0 * f);
    return mix(hash(i), hash(i + 1.0), u);
}

void main() {
    float seconds = GameTime * 1200.0;
    float r = length(texCoord0);
    if (r >= 1.0) {
        discard;
    }
    float angle = atan(texCoord0.y, texCoord0.x);
    float ragged = 0.85 + 0.15 * noise1(angle * 3.0 + seconds * 6.0);
    float pulse = 0.85 + 0.15 * noise1(seconds * 13.0);
    float d = r / ragged;

    float centre = exp(-d * d * 30.0);
    float glow = exp(-d * d * 6.0) * (1.0 - smoothstep(0.75, 1.0, r));

    vec3 colour = vec3(1.0, 0.95, 0.85) * centre * 1.2 + mix(vec3(0.8, 0.06, 0.02), vec3(1.0, 0.45, 0.1), glow) * glow;
    fragColor = vec4(colour * vertexColor.rgb * vertexColor.a * pulse, 1.0);
}
