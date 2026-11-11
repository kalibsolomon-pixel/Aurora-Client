#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InputTexture;
layout(std140) uniform GlassParams {
    vec4 UvRect;
    vec4 Blur;
    vec4 Geometry;
    vec4 Lighting;
    vec4 Filter;
    vec4 Light;
};
layout(location = 0) in vec2 vUv;
layout(location = 0) out vec4 fragColor;
void main() {
    fragColor = vec4(texture(InputTexture, mix(UvRect.xy, UvRect.zw, vUv)).rgb, 1.0);
}
