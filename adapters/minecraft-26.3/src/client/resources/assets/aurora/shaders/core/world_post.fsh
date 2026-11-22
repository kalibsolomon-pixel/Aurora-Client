#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InputTexture;
uniform sampler2D HistoryTexture;
layout(std140) uniform PostParams {
    vec4 Params; // x = motionBlend, y = saturation, z/w unused
};
layout(location = 0) in vec2 vUv;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 cur = texture(InputTexture, vUv);
    vec3 rgb = cur.rgb;
    if (Params.x > 0.0001) {
        vec3 hist = texture(HistoryTexture, vUv).rgb;
        rgb = mix(rgb, hist, Params.x);
    }
    if (abs(Params.y - 1.0) > 0.0001) {
        float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
        rgb = clamp(mix(vec3(luma), rgb, Params.y), 0.0, 1.0);
    }
    fragColor = vec4(rgb, cur.a);
}
