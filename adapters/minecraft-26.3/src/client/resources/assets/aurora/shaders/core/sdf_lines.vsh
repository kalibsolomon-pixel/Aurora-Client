#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// Vanilla's line vertex layout and expansion (rendertype_lines), widened by one
// pixel of coverage ramp. LineWidth carries a role in steps of 1024: 0 is a
// segment vertex, 1 pushes it back along the line and 2 forward, which turns a
// zero-length "line" at an endpoint into the outward half of a round cap.
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;
layout(location = 3) in float LineWidth;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) noperspective out vec2 linePixel;
layout(location = 4) noperspective out vec2 lineShape;

const float VIEW_SHRINK = 1.0 - (1.0 / 256.0);
const mat4 VIEW_SCALE = mat4(
    VIEW_SHRINK, 0.0, 0.0, 0.0,
    0.0, VIEW_SHRINK, 0.0, 0.0,
    0.0, 0.0, VIEW_SHRINK, 0.0,
    0.0, 0.0, 0.0, 1.0
);

void main() {
    float role = floor(LineWidth / 1024.0);
    float width = LineWidth - role * 1024.0;

    vec4 linePosStart = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position, 1.0);
    vec4 linePosEnd = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position + Normal, 1.0);

    vec3 ndc1 = linePosStart.xyz / linePosStart.w;
    vec3 ndc2 = linePosEnd.xyz / linePosEnd.w;

    vec2 direction = normalize((ndc2.xy - ndc1.xy) * ScreenSize);
    vec2 across = vec2(-direction.y, direction.x);
    if (across.x < 0.0) {
        across *= -1.0;
    }

    // Sub-pixel lines draw one pixel wide and fade instead of thinning away.
    float halfWidth = max(width * 0.5, 0.5);
    float extent = halfWidth + 1.0;
    float side = gl_VertexIndex % 2 == 0 ? 1.0 : -1.0;
    float along = role > 0.5 ? extent : 0.0;
    vec2 alongDirection = role > 1.5 ? direction : -direction;

    vec2 offsetPixels = across * (side * extent) + alongDirection * along;
    vec2 offsetNdc = offsetPixels * 2.0 / ScreenSize;
    gl_Position = vec4((ndc1 + vec3(offsetNdc, 0.0)) * linePosStart.w, linePosStart.w);

    linePixel = vec2(along, side * extent);
    lineShape = vec2(halfWidth, min(width, 1.0));
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color;
}
