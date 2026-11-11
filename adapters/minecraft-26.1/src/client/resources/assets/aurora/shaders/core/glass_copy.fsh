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
    fragColor = vec4(texture(InputTexture, mix(UvRect.xy, UvRect.zw, vUv)).rgb, 1.0);
}
