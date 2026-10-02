#version 330

// Totality layered fire explosion (VFX Experiment 3, Fireball V2): one fire element of the explosion.
//   texCoord0.x = corner.x + 2 * (kind * 16 + aspect)   kind 0 body volume, 1 flame tongue, 2 ground lick, 3 core
//   texCoord0.y = corner.y + 2 * cells                  cells across the element (pixel quantisation)
//   vertexColor = (heat / 1.25, erosion, seed, opacity)
// Premultiplied-alpha output: hot gas adds light (rgb above alpha), cooled soot is dark and covers what is behind it.
// Temperatures are posterised into bands and the shading is quantised to a Minecraft-scale texel grid.
// Turbulence (B5): Sampler0 holds three independent tileable 4-octave value-noise fbm fields (r, g, b; lattice period
// 16), so fbm(p) is one texture fetch, texture(Sampler0, p / 16), instead of four value-noise octaves per pixel.

#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

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

// Three independent fbm fields at p (see make_fire_noise.py).
vec3 fbm3(vec2 p) {
    return texture(Sampler0, p * 0.0625).rgb;
}

// soot -> deep red -> orange -> gold -> white-hot
vec3 ramp(float h) {
    vec3 soot = vec3(0.09, 0.065, 0.055);
    vec3 red = vec3(0.56, 0.09, 0.035);
    vec3 orange = vec3(1.0, 0.38, 0.06);
    vec3 gold = vec3(1.0, 0.70, 0.18);
    vec3 white = vec3(1.0, 0.95, 0.80);
    if (h < 0.25) return mix(soot, red, h / 0.25);
    if (h < 0.50) return mix(red, orange, (h - 0.25) / 0.25);
    if (h < 0.75) return mix(orange, gold, (h - 0.50) / 0.25);
    return mix(gold, white, clamp((h - 0.75) / 0.30, 0.0, 1.0));
}

vec4 shade(float h, float dens, float opacity) {
    h = floor(clamp(h, 0.0, 1.3) * 10.0 + 0.5) / 10.0;
    vec3 col = ramp(h);
    float smoke = 1.0 - smoothstep(0.08, 0.30, h);
    float glow = 0.55 + 0.75 * h * h;
    float a = dens * opacity * mix(0.78, 0.62, smoke);
    vec3 rgb = mix(col * glow, vec3(0.16, 0.13, 0.12) * 0.9, smoke) * dens * opacity;
    return vec4(rgb, a);
}

void main() {
    float code = floor(texCoord0.x * 0.5);
    float cells = floor(texCoord0.y * 0.5);
    vec2 c = vec2(texCoord0.x - 2.0 * code, texCoord0.y - 2.0 * cells);
    float kind = floor(code / 16.0);
    float aspect = max(code - kind * 16.0, 1.0);
    vec2 grid = vec2(cells, cells * aspect);
    vec2 q = (floor(c * grid) + 0.5) / grid;

    float heat = vertexColor.r * 1.25;
    float erosion = vertexColor.g;
    float seed = vertexColor.b * 97.0;
    float opacity = vertexColor.a;
    float t = GameTime * 1200.0;

    vec4 outColor;
    if (kind < 0.5) {
        // Body volume: a billowing, domain-warped mass with flame-like edges, a hotter heart, dark soot veins, and
        // holes that grow as it burns out.
        vec2 p = q * 2.0 - 1.0;
        vec2 warp = fbm3(p * 1.7 + vec2(seed * 0.37, -t * 0.7)).rg - 0.5;
        vec2 w = p + warp * (0.55 + 0.35 * erosion);
        float r = length(w);
        float billow = abs(fbm3(w * 2.4 + vec2(seed * 1.3, -t * 1.1)).b * 2.0 - 1.0);
        float n2 = fbm3(w * 4.3 + vec2(seed * 2.1, -t * 1.6)).r;
        float edge = 0.84 + (billow - 0.5) * 0.45 + (n2 - 0.5) * 0.25;
        float dens = smoothstep(edge, edge - 0.22, r) * smoothstep(1.0, 0.86, max(abs(p.x), abs(p.y)));
        float holes = n2 * 0.55 + billow * 0.45 + (1.0 - r) * 0.25 * (1.0 - erosion);
        dens *= smoothstep(erosion * 0.95 - 0.04, erosion * 0.95 + 0.14, holes);
        float h = heat * (0.52 + 0.62 * (1.0 - r)) + (n2 - 0.5) * 0.75 * heat - (1.0 - billow) * 0.18 * heat;
        outColor = shade(h, dens, opacity);
    } else if (kind < 1.5) {
        // Flame tongue: tapering from its base outwards, wobbling, hottest at the base.
        float x = q.x * 2.0 - 1.0;
        float y = q.y;
        float wob = (vnoise(vec2(y * 3.0 - t * 3.0, seed)) - 0.5) * 0.7 * y;
        float w = pow(1.0 - y, 0.6) * (0.7 + 0.5 * vnoise(vec2(y * 5.0 - t * 4.0, seed * 1.7)));
        float dens = smoothstep(w, w * 0.4, abs(x + wob)) * smoothstep(0.0, 0.1, y);
        float n2 = fbm3(vec2(x * 2.0, y * aspect - t * 2.0) + seed).g;
        dens *= smoothstep(erosion - 0.05, erosion + 0.2, n2 + (1.0 - y) * 0.2);
        float h = heat * (1.05 - 0.45 * y) + (n2 - 0.5) * 0.3 * heat;
        outColor = shade(h, dens, opacity);
    } else if (kind < 2.5) {
        // Ground lick: separate flame tongues rising from the ground line, flickering, hottest at their roots.
        float x = q.x * 2.0 - 1.0;
        float y = q.y;
        float column = smoothstep(0.25, 0.75, vnoise(vec2(x * 2.6 + seed * 3.0, t * 0.6)));
        float tips = 0.25 + 0.7 * column * (0.6 + 0.4 * vnoise(vec2(x * 6.0 + seed, t * 3.0)));
        float sway = (vnoise(vec2(y * 3.0 - t * 3.5, seed * 2.0)) - 0.5) * 0.35 * y;
        float dens = smoothstep(tips, tips - 0.22, y) * smoothstep(1.0, 0.55, abs(x + sway)) * smoothstep(0.0, 0.06, y);
        float n2 = fbm3(vec2(x * 3.0 + seed, y * 3.0 - t * 2.2)).g;
        dens *= smoothstep(erosion - 0.05, erosion + 0.2, n2 + (1.0 - y) * 0.25);
        float h = heat * (1.1 - 0.7 * y) + (n2 - 0.5) * 0.35 * heat;
        outColor = shade(h, dens, opacity);
    } else {
        // Ignition core: a brief white-gold burst.
        vec2 p = q * 2.0 - 1.0;
        float r = length(p);
        float n = fbm3(p * 2.5 + vec2(seed, t * 2.0)).r;
        float dens = exp(-r * r * 4.5) * (0.8 + 0.2 * n);
        outColor = vec4(ramp(1.2) * dens * opacity * 2.2, dens * opacity * 0.35);
    }
    if (outColor.a < 0.004 && max(outColor.r, max(outColor.g, outColor.b)) < 0.004) discard;
    fragColor = outColor;
}
