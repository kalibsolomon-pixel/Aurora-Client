#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) noperspective in vec2 linePixel;
layout(location = 4) noperspective in vec2 lineShape;

layout(location = 0) out vec4 fragColor;

void main() {
    // Distance in screen pixels to the segment (x is 0 along its body) or to
    // the cap's endpoint; one pixel of linear ramp at the edge.
    float coverage = clamp(lineShape.x - length(linePixel) + 0.5, 0.0, 1.0) * lineShape.y;
    if (coverage <= 0.0) {
        discard;
    }
    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
