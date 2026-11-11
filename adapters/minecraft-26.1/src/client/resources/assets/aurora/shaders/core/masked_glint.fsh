#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * ColorModulator;
    float fade = (1.0 - total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd)) * GlintAlpha;
    // Alpha carries pattern coverage and intensity. RGB may be pure black.
    float coverage = clamp(color.a * fade, 0.0, 1.0);
    if (coverage <= 0.0) discard;
    fragColor = vec4(color.rgb, coverage);
}
