#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 0) out vec4 fragColor;

void main() {
    float encoded = texture(Sampler0, texCoord0).r * 255.0;
    float signedDistance = (encoded - 128.0) * (8.0 / 128.0);
    float gradient = max(fwidth(signedDistance), 0.0001);
    float coverage = clamp(0.5 + (signedDistance - 0.30) / gradient, 0.0, 1.0);

    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 0.0) discard;
    fragColor = color;
}
