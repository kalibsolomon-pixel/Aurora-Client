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
    // The distance is in texels, so the edge's antialiasing width is texels per pixel.
    // Take it from the interpolated UVs, not the sampled distance's derivative: on a quad's
    // edge the GPU's 2x2 derivative block also samples beyond the glyph's cell, where
    // the atlas holds a neighbour or nothing, and that showed as specks under glyphs.
    vec2 footprint = fwidth(texCoord0 * vec2(textureSize(Sampler0, 0)));
    float gradient = max(0.5 * (footprint.x + footprint.y), 0.0001);
    float coverage = clamp(0.5 + (signedDistance - 0.30) / gradient, 0.0, 1.0);

    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 0.0) discard;
    fragColor = color;
}
