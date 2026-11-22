#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec3 Position;
layout(location = 0) out vec2 vUv;
void main() {
    vUv = Position.xy * 0.5 + 0.5;
    gl_Position = vec4(Position, 1.0);
}
