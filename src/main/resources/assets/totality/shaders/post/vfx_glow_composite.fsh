#version 330

// Totality emissive glow: adds the blurred glow (the first, half-resolution chain level) to the main image.
// A soft shoulder (1 - e^-x) keeps strong, overlapping glows from flattening into pure white.

uniform sampler2D InSampler;

layout(std140) uniform GlowPass {
    vec2 InTexel;
    float Intensity;
    float Spread;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 glow = texture(InSampler, texCoord).rgb * Intensity;
    fragColor = vec4(vec3(1.0) - exp(-glow), 0.0);
}
