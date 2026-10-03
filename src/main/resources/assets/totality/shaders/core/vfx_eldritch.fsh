#version 330

// Totality Eldritch Blast V2: every element of the spell in one premultiplied-alpha draw (vertex stage: core/vfx_fire).
//   vertexColor.b = (mode * 2 + palette + 0.5) / 16   mode: 0 beam, 1 residue, 2 head, 3 cast flare, 4 impact, 5 spark
//                                                     palette: 0 violet (the spell icon's colours), 1 teal (BG3-like, default)
//   vertexColor.r = seed, vertexColor.g = mode parameter (see below), vertexColor.a = strength (fades, near-camera fade)
//   beam parameter: below 0.5 the release flash (0 = full), from 0.5 the collapse (1 = gone)
//   ribbons (beam, residue, spark): texCoord0.x -1..1 across, texCoord0.y blocks along from the ribbon's start
//   sprites (head, flare, impact):  texCoord0 = quad corner -1..1
// Premultiplied output: light adds and slightly covers what is behind; the dark residue and the flare's dark wisps
// output black with alpha, so they darken the scene (the "eldritch" read in daylight). Pixel-quantised across ribbons.

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

// h: 0 outer (dark) .. 1 inner .. 1.3 white core
vec3 ramp(float h, int pal) {
    vec3 outer = pal == 0 ? vec3(0.30, 0.04, 0.55) : vec3(0.02, 0.32, 0.27);
    vec3 mid = pal == 0 ? vec3(0.56, 0.16, 1.00) : vec3(0.10, 0.86, 0.72);
    vec3 inner = pal == 0 ? vec3(0.87, 0.64, 1.00) : vec3(0.60, 1.00, 0.90);
    vec3 core = pal == 0 ? vec3(1.00, 0.95, 1.00) : vec3(0.95, 1.00, 0.97);
    if (h < 0.5) return mix(outer, mid, h / 0.5);
    if (h < 1.0) return mix(mid, inner, (h - 0.5) / 0.5);
    return mix(inner, core, clamp((h - 1.0) / 0.3, 0.0, 1.0));
}

vec4 light(vec3 col, float dens, float strength) {
    return vec4(col * dens * strength, dens * strength * 0.7);
}

void main() {
    float t = GameTime * 1200.0;
    int code = int(floor(vertexColor.b * 16.0));
    int mode = code / 2;
    int pal = code - mode * 2;
    float seed = vertexColor.r * 97.0;
    float param = vertexColor.g;
    float strength = vertexColor.a;
    vec4 outColor;
    if (mode == 0) {
        // Beam: a focused discharge. A clearly defined white core inside a thin, translucent teal glow; faint pulses
        // and two faint twisting filaments; a slight dark edge. The release flash widens and whitens the core and
        // brightens everything for a few frames; the collapse narrows the glow first, then the core.
        float flash = param < 0.5 ? 1.0 - param * 2.0 : 0.0;
        float collapse = param >= 0.5 ? (param - 0.5) * 2.0 : 0.0;
        float uq = (floor((texCoord0.x * 0.5 + 0.5) * 12.0) + 0.5) / 12.0 * 2.0 - 1.0;
        float v = floor(texCoord0.y * 8.0) / 8.0;
        float x = abs(uq);
        float squeeze = 1.0 - collapse;
        float flick = floor(t * 20.0);
        float pulse = smoothstep(0.6, 1.0, sin(v * 2.4 - t * 38.0 + seed) * 0.5 + 0.5);
        float coreW = mix(0.28, 0.5, flash) * (collapse > 0.6 ? (1.0 - collapse) / 0.4 : 1.0);
        float core = exp(-pow(x / max(coreW, 0.02), 2.0));
        float glow = exp(-pow(x / max(0.72 * squeeze, 0.05), 2.0)) * squeeze;
        float s1 = 0.35 * squeeze * sin(v * 2.1 - t * 26.0 + seed);
        float s2 = 0.35 * squeeze * sin(v * 2.1 - t * 26.0 + seed + 3.14159);
        float crackle = 0.6 + 0.8 * vnoise(vec2(v * 3.0, flick + seed));
        float strands = (exp(-pow((uq - s1) / 0.07, 2.0)) + exp(-pow((uq - s2) / 0.07, 2.0))) * crackle * squeeze * 0.25;
        float glowA = glow * mix(0.45 + 0.15 * pulse, 0.85, flash);
        vec3 col = ramp(mix(0.75, 1.1, flash), pal) * glowA * (0.9 + 1.2 * flash);
        float alpha = glowA;
        col += ramp(1.05, pal) * strands;
        alpha = max(alpha, strands * 0.6);
        col = ramp(1.3, pal) * (1.15 + 0.6 * flash) * core + col * (1.0 - core * 0.85);
        alpha = max(alpha, core * mix(0.65, 0.9, flash));
        outColor = vec4(col * strength, alpha * strength);
        float fringe = exp(-pow((x - 0.92) / 0.08, 2.0)) * 0.25 * squeeze * (1.0 - flash);
        outColor.a = max(outColor.a, fringe * strength);
    } else if (mode == 1) {
        // Residue: a black, thorny fracture suspended along the path. An angular zig-zag crack of irregular thickness
        // with sharp tapered thorns branching off both sides; it fades by breaking up unevenly, with almost no smoke.
        float v = texCoord0.y;
        float u = (floor((texCoord0.x * 0.5 + 0.5) * 40.0) + 0.5) / 40.0 * 2.0 - 1.0;
        float seg = 0.32;
        float k0 = floor(v / seg);
        float f = fract(v / seg);
        float a0 = (hash(vec2(k0, seed)) - 0.5) * 0.55;
        float a1 = (hash(vec2(k0 + 1.0, seed)) - 0.5) * 0.55;
        float centre = mix(a0, a1, f) + (vnoise(vec2(v * 6.0, seed + 5.0)) - 0.5) * 0.12;
        float thick = 0.09 + 0.16 * pow(vnoise(vec2(v * 2.2, seed + 2.0)), 2.0);
        float dens = 1.0 - smoothstep(thick * 0.55, thick, abs(u - centre));
        // Thorns: up to two per 0.5-block cell, each leaving the crack at an angle and tapering to a point.
        for (int c = -1; c <= 1; c++) {
            for (int n = 0; n < 2; n++) {
                float cell = floor(v * 2.0) + float(c);
                vec2 h = vec2(cell, seed + float(n) * 13.0);
                if (hash(h + 0.31) > 0.62) continue;
                float base = (cell + hash(h + 0.73)) * 0.5;
                float lenV = 0.12 + 0.28 * hash(h + 0.17);
                float q = (v - base) / lenV;
                if (q < 0.0 || q > 1.0) continue;
                float side = hash(h + 0.91) < 0.5 ? -1.0 : 1.0;
                float reach = 0.35 + 0.6 * hash(h + 0.47);
                float kb = floor(base / seg);
                float cb = mix((hash(vec2(kb, seed)) - 0.5) * 0.55, (hash(vec2(kb + 1.0, seed)) - 0.5) * 0.55, fract(base / seg));
                float thornU = cb + side * reach * q;
                float w = 0.12 * (1.0 - q) + 0.02;
                dens = max(dens, 1.0 - smoothstep(w * 0.5, w, abs(u - thornU)));
            }
        }
        // Uneven fade: pieces drop out where noise exceeds the remaining strength.
        float breakup = vnoise(vec2(v * 3.5, u * 2.0 + seed));
        float keep = smoothstep(breakup - 0.25, breakup, strength * 1.25);
        float smoke = 0.06 * exp(-u * u * 2.0) * vnoise(vec2(v * 0.8 - t * 0.6, seed + 11.0)) * strength;
        float d = max(dens * keep, smoke);
        vec3 tint = ramp(0.0, pal) * 0.12 * dens;
        outColor = vec4(tint * d, d * 0.92);
    } else if (mode == 5) {
        // Spark: a hot head (v = 0) fading into a coloured tail.
        float x = abs(texCoord0.x);
        float along = clamp(texCoord0.y / max(param * 1.2, 0.05), 0.0, 1.0);
        float dens = exp(-pow(x / 0.55, 2.0)) * (1.0 - along);
        vec3 col = ramp(1.3 - along * 0.9, pal) * 1.2;
        outColor = light(col, dens, strength);
    } else {
        vec2 p = (floor((texCoord0 * 0.5 + 0.5) * 16.0) + 0.5) / 16.0 * 2.0 - 1.0;
        float r = length(p);
        float a = atan(p.y, p.x);
        if (mode == 2) {
            // Head: a compact white core, a coloured halo and a slowly turning four-point glint.
            float core = smoothstep(0.34, 0.18, r);
            float halo = exp(-r * r * 4.0) * smoothstep(1.0, 0.7, r);
            float star = pow(abs(cos(2.0 * a + t * 3.0 + seed)), 24.0) * exp(-r * 2.5) * smoothstep(1.0, 0.6, r);
            float h = mix(0.6 + 0.4 * (1.0 - r), 1.3, max(core, star));
            float dens = clamp(max(max(core, halo * 0.8), star), 0.0, 1.0);
            outColor = light(ramp(h, pal) * (0.9 + 0.5 * core), dens, strength);
        } else if (mode == 3) {
            // Cast flare, param = progress 0..1 over its life: arcs of light coil in, then a ring bursts outward
            // with dark wisps around it; a white flash at the hand fading out.
            float g = param;
            float R = g < 0.22 ? mix(0.75, 0.22, g / 0.22) : mix(0.22, 0.95, (g - 0.22) / 0.78);
            float arcs = smoothstep(0.2, 0.9, sin(3.0 * a + t * 14.0 + r * 7.0 + seed) * 0.5 + 0.5);
            float ring = exp(-pow((r - R) / (0.08 + 0.06 * g), 2.0)) * mix(arcs, 1.0, 0.45);
            float flash = exp(-r * r * 8.0) * pow(1.0 - g, 1.3);
            float fade = 1.0 - smoothstep(0.55, 1.0, g);
            float h = mix(0.7, 1.3, clamp(flash * 1.5, 0.0, 1.0));
            float dens = clamp(max(ring * fade, flash), 0.0, 1.0);
            outColor = light(ramp(h, pal) * 1.1, dens, strength);
            float wisp = exp(-pow((r - R * 1.18) / 0.09, 2.0)) * smoothstep(0.35, 0.75, vnoise(vec2(a * 2.5 + seed, t * 6.0)));
            float dark = exp(-pow((r - R * 1.1) / 0.16, 2.0)) * (1.0 - smoothstep(0.1, 0.5, g));
            outColor.a = max(outColor.a, max(wisp * 0.8, dark * 0.7) * fade * strength);
        } else {
            // Impact, param = progress 0..1: a concentrated white-hot flash, long sharp directional spikes and a faint
            // secondary ring; fast.
            float g = param;
            float flash = exp(-r * r * 16.0) * pow(1.0 - g, 1.6);
            float R = 0.18 + 0.78 * (1.0 - pow(1.0 - g, 2.0));
            float ring = exp(-pow((r - R) / (0.04 + 0.05 * g), 2.0)) * (1.0 - g) * 0.35;
            float spikes = pow(abs(cos(4.0 * a + seed)), 70.0) * exp(-r * 0.9) * smoothstep(1.0, 0.75, r)
                    + 0.6 * pow(abs(cos(3.0 * a + seed * 2.0)), 50.0) * exp(-r * 1.3) * smoothstep(1.0, 0.6, r);
            float reach = smoothstep(0.0, 0.25, g);
            spikes *= pow(1.0 - g, 1.2) * step(r, 0.25 + 0.9 * reach);
            float h = mix(0.8, 1.3, clamp(flash * 1.4 + spikes * 0.7, 0.0, 1.0));
            float dens = clamp(flash + ring + spikes, 0.0, 1.0);
            outColor = light(ramp(h, pal) * 1.15, dens, strength);
        }
    }
    if (outColor.a < 0.004 && max(outColor.r, max(outColor.g, outColor.b)) < 0.004) discard;
    fragColor = outColor;
}
