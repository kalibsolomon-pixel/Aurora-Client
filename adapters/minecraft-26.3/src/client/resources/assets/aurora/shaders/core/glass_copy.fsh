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
    vec2 uv = mix(UvRect.xy, UvRect.zw, vUv);
    vec3 color;
    // A single bilinear tap is a box only at exact 2:1. At rounded odd sizes
    // it point-samples alternating detail into low-frequency moving bands.
    if (Blur.x == 0.0 && Blur.y == 0.0) {
        color = texture(InputTexture, uv).rgb;
    } else if (Blur.y == 0.0) {
        vec2 d = vec2(Blur.x, 0.0);
        color = (texture(InputTexture, uv - d).rgb + texture(InputTexture, uv + d).rgb) * 0.5;
    } else if (Blur.x == 0.0) {
        vec2 d = vec2(0.0, Blur.y);
        color = (texture(InputTexture, uv - d).rgb + texture(InputTexture, uv + d).rgb) * 0.5;
    } else {
        color = (texture(InputTexture, uv - Blur.xy).rgb + texture(InputTexture, uv + Blur.xy).rgb
                + texture(InputTexture, uv + vec2(Blur.x, -Blur.y)).rgb
                + texture(InputTexture, uv + vec2(-Blur.x, Blur.y)).rgb) * 0.25;
    }
    fragColor = vec4(color, 1.0);
}
