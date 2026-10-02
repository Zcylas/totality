#version 330

// Totality Heat Vision V2 beam: a camera-facing ribbon shaded from its centre line outwards.
//   texCoord0.x  -1 .. 1 across the ribbon (0 = beam axis)
//   texCoord0.y  distance along the beam in blocks (drives the energy flowing away from the eyes)
//   vertexColor  rgb = tint scale, a = beam strength (ignition ramp / fade)
// Additive output: white-hot core, orange body, deep red rim, with bands of energy travelling outwards.

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
    float across = abs(texCoord0.x);
    float along = texCoord0.y;

    // Energy flowing outwards: two noise octaves scrolling along the beam, plus a fast shimmer.
    float flow = noise1(along * 1.7 - seconds * 9.0) * 0.6 + noise1(along * 4.3 - seconds * 17.0) * 0.4;
    float shimmer = 0.92 + 0.08 * noise1(seconds * 31.0 + along * 0.5);

    // The body narrows and widens slightly with the flow, so the edge reads as moving energy.
    float width = 0.82 + 0.18 * flow;
    float x = across / width;

    float core = exp(-x * x * 26.0);
    float body = exp(-x * x * 5.5);
    float rim = clamp(1.0 - x, 0.0, 1.0);
    rim *= rim;

    vec3 white = vec3(1.0, 0.95, 0.85);
    vec3 orange = vec3(1.0, 0.42, 0.08);
    vec3 red = vec3(0.75, 0.04, 0.02);
    vec3 colour = red * rim * 0.55 + orange * body * (0.75 + 0.35 * flow) + white * core * 1.1;

    float strength = vertexColor.a * shimmer;
    fragColor = vec4(colour * vertexColor.rgb * strength, 1.0);
}
