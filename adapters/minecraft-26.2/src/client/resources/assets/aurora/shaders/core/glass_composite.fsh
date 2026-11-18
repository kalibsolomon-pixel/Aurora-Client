#version 150
uniform sampler2D InputTexture;
layout(std140) uniform GlassParams {
    vec4 UvRect;
    vec4 Blur;
    vec4 Geometry;
    vec4 Lighting;
    vec4 Filter;
    vec4 Light;
};
in vec2 vUv;
out vec4 fragColor;
void main() {
    vec2 p = (vUv - 0.5) * 2.0 * Geometry.xy;
    vec2 q = abs(p) - (Geometry.xy - Geometry.z);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - Geometry.z;
    // Refractive edge (Filter.z = largest bend, Filter.w = frame band, device px): inside the band
    // the backdrop is sampled inward along the rounded-rect normal, strongest at the outer edge,
    // like the edge of a glass slab. Planar facets at radius 0. Clamped to this window's own rect.
    vec2 uv = vUv;
    if (Filter.z > 0.0 && Filter.w > 0.0 && d > -Filter.w) {
        vec2 n = (q.x > 0.0 && q.y > 0.0) ? normalize(q) : (q.x > q.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
        n *= sign(p);
        float t = clamp(-d / Filter.w, 0.0, 1.0);
        float bend = Filter.z * (1.0 - t) * (1.0 - t);
        uv = clamp((p - n * bend) / (2.0 * Geometry.xy) + 0.5, 0.0, 1.0);
    }
    vec3 blurred = texture(InputTexture, mix(UvRect.xy, UvRect.zw, uv)).rgb;
    float luma = dot(blurred, vec3(0.213, 0.715, 0.072));
    blurred = clamp(mix(vec3(luma), blurred, Filter.x), 0.0, 1.0) * Filter.y;
    float coverage = clamp(0.5 - d, 0.0, 1.0);
    float t = clamp(dot(p / Geometry.xy, Light.xy) * 0.5 + 0.5, 0.0, 1.0);
    float g = (mix(t, 1.0 - t, Lighting.z) - 0.5) * 2.0;
    vec3 lit = blurred + vec3(g * Lighting.y);
    float band = smoothstep(-Geometry.w, 0.0, d) * coverage;
    float plen = length(p);
    float facing = plen > 0.5 ? dot(p, Light.xy) / plen : 0.0;
    float edgeAmt = smoothstep(-0.35, 0.55, facing);
    vec3 litBase = lit;
    if (Lighting.z < 0.5) {
        vec3 hi = clamp(litBase + vec3(Lighting.x), 0.0, 1.0);
        lit = mix(litBase, mix(hi, litBase, Lighting.w), band * edgeAmt);
    } else {
        vec3 lo = clamp(litBase - vec3(Lighting.x), 0.0, 1.0);
        vec3 far = clamp(litBase + vec3(Lighting.x * 0.45), 0.0, 1.0);
        lit = mix(litBase, mix(lo, litBase, Lighting.w), band * edgeAmt);
        lit = mix(lit, mix(far, litBase, Lighting.w), band * (1.0 - edgeAmt));
    }
    fragColor = vec4(clamp(lit, 0.0, 1.0), coverage);
}
