#version 150
in vec3 Position;
out vec2 vUv;
void main() {
    // VulkanMod uses a negative-height viewport. Keep texture coordinates
    // top-down in every pass, including off-screen attachments and GUI blits.
    vUv = vec2(Position.x * 0.5 + 0.5, 0.5 - Position.y * 0.5);
    gl_Position = vec4(Position, 1.0);
}
