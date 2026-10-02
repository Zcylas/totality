#version 330

// Totality Fireball V2 projectile (VFX Experiment 3, B3): the white-hot bead and its short fire streak.
//   vertexColor.b < 0.5  streak: texCoord0.x -1..1 across, texCoord0.y blocks behind the bead;
//                        vertexColor.g = streak length / 4
//   vertexColor.b >= 0.5 bead:   texCoord0 = quad corner -1..1; vertexColor.g = impact flare 0..1
//   vertexColor.r = seed, vertexColor.a = strength (growth, near-camera fade, impact fade)
// Premultiplied-alpha output like the explosion (core/vfx_fire): hot gas adds light and slightly covers what is
// behind it, so the projectile reads in daylight without washing out. Pixel-quantised to the explosion's texel scale.

#moj_import <minecraft:globals.glsl>

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

// red -> orange -> gold -> white-hot (the explosion's palette without the soot end)
vec3 ramp(float h) {
    vec3 red = vec3(0.56, 0.09, 0.035);
    vec3 orange = vec3(1.0, 0.38, 0.06);
    vec3 gold = vec3(1.0, 0.70, 0.18);
    vec3 white = vec3(1.0, 0.95, 0.80);
    if (h < 0.50) return mix(red * 0.6, orange, clamp(h / 0.5, 0.0, 1.0));
    if (h < 0.75) return mix(orange, gold, (h - 0.50) / 0.25);
    return mix(gold, white, clamp((h - 0.75) / 0.30, 0.0, 1.0));
}

void main() {
    float t = GameTime * 1200.0;
    float seed = vertexColor.r * 97.0;
    float strength = vertexColor.a;
    vec4 outColor;
    if (vertexColor.b < 0.5) {
        // Streak: tapering, energy streaming backwards from the bead, white core -> gold -> orange -> red, the tail
        // breaking into flickering tongues.
        float len = max(vertexColor.g * 4.0, 0.05);
        float vq = (floor(texCoord0.y * 10.0) + 0.5) / 10.0;
        float uq = (floor((texCoord0.x * 0.5 + 0.5) * 6.0) + 0.5) / 6.0 * 2.0 - 1.0;
        float along = clamp(vq / len, 0.0, 1.0);
        float flow = vnoise(vec2(vq * 2.4 - t * 16.0, seed));
        float flicker = vnoise(vec2(uq * 2.5 + seed, vq * 4.0 - t * 22.0));
        float w = (1.0 - along * 0.8) * (0.72 + 0.4 * flow);
        float x = abs(uq) / max(w, 0.05);
        float dens = smoothstep(1.05, 0.55, x) * smoothstep(1.0, 0.72, along + (flicker - 0.5) * 0.4);
        // White-hot only at the head, then gold, orange and red toward the tail.
        float h = 1.3 * pow(1.0 - along, 1.6) * (1.0 - 0.6 * x) + 0.15 * (1.0 - along) + (flow - 0.5) * 0.3;
        h = floor(clamp(h, 0.0, 1.3) * 10.0 + 0.5) / 10.0;
        vec3 col = ramp(h) * (0.55 + 0.6 * h * h);
        outColor = vec4(col * dens * strength, dens * strength * 0.75);
    } else {
        // Bead: a compact white-hot core inside a flickering gold-orange halo.
        float flare = vertexColor.g;
        vec2 p = (floor((texCoord0 * 0.5 + 0.5) * 9.0) + 0.5) / 9.0 * 2.0 - 1.0;
        float r = length(p);
        float n = vnoise(p * 3.0 + vec2(seed, -t * 7.0));
        float core = smoothstep(0.42, 0.26, r + (n - 0.5) * 0.12);
        float halo = exp(-r * r * 3.0) * (0.75 + 0.35 * n) * smoothstep(1.0, 0.7, r);
        float h = mix(0.45 + 0.45 * (1.0 - r), 1.3, core);
        vec3 col = ramp(h) * (0.8 + 0.6 * h * h) * (1.0 + 0.6 * flare);
        float dens = max(core, halo);
        outColor = vec4(col * dens * strength, dens * strength * 0.75);
    }
    if (outColor.a < 0.004 && max(outColor.r, max(outColor.g, outColor.b)) < 0.004) discard;
    fragColor = outColor;
}
