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
float detailHash(uint x, uint y) {
    uint h = (x * 73856093u) ^ (y * 19349663u);
    h ^= h >> 13;
    h *= 1274126177u;
    h ^= h >> 16;
    return float(h & 255u) / 255.0;
}
void main() {
    if (Geometry.w < 0.5) {
        fragColor = vec4(0.522, 0.529, 0.549, 1.0);
    } else {
        float rc = Geometry.y * vUv.y + Blur.x;
        float band = 1.0 - step(0.5, fract(rc / (16.0 * Geometry.z)));
        vec3 col = mix(vec3(0.161, 0.173, 0.200), vec3(0.800, 0.812, 0.839), band);
        float cc = Geometry.x * vUv.x;
        float accent = 1.0 - step(2.0 / 64.0, fract(cc / (64.0 * Geometry.z)));
        vec3 outc = mix(col, vec3(0.549, 0.290, 0.314), accent);
        if (Blur.y > 0.0) {
            float detail = detailHash(uint(floor(cc)), uint(floor(rc))) - 0.5;
            outc = clamp(outc + vec3(detail * 2.0 * Blur.y), 0.0, 1.0);
        }
        fragColor = vec4(outc, 1.0);
    }
}
