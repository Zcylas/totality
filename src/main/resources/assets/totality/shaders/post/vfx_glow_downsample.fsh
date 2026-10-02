#version 330

// Totality emissive glow: one step down the blur chain (output is half the input size).
// Centre sample plus four diagonal samples placed on texel corners, so bilinear filtering averages 4x4 input texels.

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
    vec3 sum = texture(InSampler, texCoord).rgb * 4.0;
    sum += texture(InSampler, texCoord + vec2(-d.x, -d.y)).rgb;
    sum += texture(InSampler, texCoord + vec2(d.x, -d.y)).rgb;
    sum += texture(InSampler, texCoord + vec2(-d.x, d.y)).rgb;
    sum += texture(InSampler, texCoord + vec2(d.x, d.y)).rgb;
    fragColor = vec4(sum * 0.125, 1.0);
}
