#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec3 Position;
layout(location = 0) out vec2 vUv;
void main() {
    // Native framebuffer sampling uses top-down texture coordinates. Keep texture coordinates
    // top-down in every pass, including off-screen attachments and GUI blits.
    vUv = vec2(Position.x * 0.5 + 0.5, 0.5 - Position.y * 0.5);
    gl_Position = vec4(Position, 1.0);
}
