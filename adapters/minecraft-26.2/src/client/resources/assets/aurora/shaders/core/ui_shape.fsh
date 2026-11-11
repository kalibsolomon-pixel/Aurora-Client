#version 330
layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; vec4 ColorModulator; vec3 ModelOffset; mat4 TextureMat; };
in vec2 localPos;
in vec4 vertexColor;
flat in ivec4 shapeParams;
out vec4 fragColor;

float shapeDistance(vec2 p, vec2 size, float radius, bool squircle) {
    vec2 q = abs(p - size * 0.5) - (size * 0.5 - radius);
    vec2 corner = max(q, 0.0);
    float norm = squircle ? pow(pow(corner.x, 5.0) + pow(corner.y, 5.0), 0.2) : length(corner);
    return norm + min(max(q.x, q.y), 0.0) - radius;
}
void main() {
    vec2 size = vec2(shapeParams.xy) / 16.0;
    float radius = float(abs(shapeParams.z)) / 16.0;
    float thickness = float(shapeParams.w) / 16.0;
    bool squircle = shapeParams.z < 0;
    float d = shapeDistance(localPos, size, radius, squircle);
    float coverage = clamp(0.5 - d / max(fwidth(d), 0.0001), 0.0, 1.0);
    if (thickness > 0.0) {
        vec2 innerSize = size - 2.0 * thickness;
        float innerRadius = max(radius - thickness, 0.0);
        float inside = shapeDistance(localPos - thickness, innerSize, innerRadius, squircle);
        coverage *= clamp(0.5 + inside / max(fwidth(inside), 0.0001), 0.0, 1.0);
    }
    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 0.0) discard;
    fragColor = color;
}
