#version 330

// Totality emissive glow: one step up the blur chain. Reads the smaller level and is ADDED (blend) onto the larger
// level, which already holds that level's own downsample. Eight taps in a ring give a smooth, round falloff.

uniform sampler2D InSampler;

layout(std140) uniform GlowPass {
    vec2 InTexel;
    float Intensity;
    float Spread;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 d = InTexel * Spread;
    vec3 sum = texture(InSampler, texCoord + vec2(-2.0 * d.x, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(2.0 * d.x, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(0.0, -2.0 * d.y)).rgb;
    sum += texture(InSampler, texCoord + vec2(0.0, 2.0 * d.y)).rgb;
    sum += texture(InSampler, texCoord + vec2(-d.x, -d.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(d.x, -d.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(-d.x, d.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(d.x, d.y)).rgb * 2.0;
    fragColor = vec4(sum * (Intensity / 12.0), 1.0);
}
