#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Vanilla's line vertex layout and expansion (rendertype_lines), widened by one
// pixel of coverage ramp. LineWidth carries a role in steps of 1024: 0 is a
// segment vertex, 1 pushes it back along the line and 2 forward, which turns a
// zero-length "line" at an endpoint into the outward half of a round cap.
in vec3 Position;
in vec4 Color;
in vec3 Normal;
in float LineWidth;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
noperspective out vec2 linePixel;
noperspective out vec2 lineShape;

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
    float side = gl_VertexID % 2 == 0 ? 1.0 : -1.0;
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
