#version 150
uniform sampler2D InputTexture;
layout(std140) uniform GlassParams {
    vec4 UvRect;
    vec4 Blur;
    vec4 Geometry;
    vec4 Lighting;
    vec4 Filter;
    vec4 Light;
};
in vec2 vUv;
out vec4 fragColor;
void main() {
    vec3 sum = vec3(0.0);
    float weights = 0.0;
    for (int t = -4; t <= 4; ++t) {
        float weight = exp(-0.5 * float(t * t) / (Blur.z * Blur.z));
        sum += texture(InputTexture, vUv + Blur.xy * float(t)).rgb * weight;
        weights += weight;
    }
    fragColor = vec4(sum / weights, 1.0);
}
