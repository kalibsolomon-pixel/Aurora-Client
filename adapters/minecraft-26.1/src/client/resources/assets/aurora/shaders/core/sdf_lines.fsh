#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
noperspective in vec2 linePixel;
noperspective in vec2 lineShape;

out vec4 fragColor;

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
