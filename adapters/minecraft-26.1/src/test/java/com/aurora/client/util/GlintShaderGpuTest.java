package com.aurora.client.util;

import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL33.*;

/** Exercises the actual fragment shader and blend equation in an invisible GL context. */
class GlintShaderGpuTest {
    private static String resource(String path) throws Exception {
        try (var stream = GlintShaderGpuTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String fragment() throws Exception {
        String source = resource("assets/aurora/shaders/core/masked_glint.fsh");
        var matcher = Pattern.compile("#moj_import <minecraft:([^>]+)>").matcher(source);
        StringBuilder expanded = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(expanded, java.util.regex.Matcher.quoteReplacement(
                resource("assets/minecraft/shaders/include/" + matcher.group(1)).replace("#version 330", "")));
        matcher.appendTail(expanded);
        return expanded.toString();
    }

    private static int shader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source); glCompileShader(shader);
        assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
        return shader;
    }

    private static void block(int program, String name, int binding, String[] uniforms, float[] values) {
        int index = glGetUniformBlockIndex(program, name);
        ByteBuffer bytes = BufferUtils.createByteBuffer(glGetActiveUniformBlocki(program, index, GL_UNIFORM_BLOCK_DATA_SIZE));
        for (int i = 0; i < uniforms.length; i++) {
            int uniform = glGetUniformIndices(program, uniforms[i]);
            int offset = glGetActiveUniformsi(program, uniform, GL_UNIFORM_OFFSET);
            bytes.putFloat(offset, values[i]);
        }
        int buffer = glGenBuffers(); glBindBuffer(GL_UNIFORM_BUFFER, buffer);
        glBufferData(GL_UNIFORM_BUFFER, bytes, GL_STATIC_DRAW);
        glBindBufferBase(GL_UNIFORM_BUFFER, binding, buffer); glUniformBlockBinding(program, index, binding);
    }

    @Test void realShaderMakesBlackShadowsAndWhiteHighlightsAtEqualStrength() throws Exception {
        assumeTrue(GLFW.glfwInit(), "No GLFW display available");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(16, 16, "Glint shader test", 0, 0);
        try {
            assumeTrue(window != 0, "No OpenGL context available");
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            int vertex = shader(GL_VERTEX_SHADER, """
                    #version 330
                    out float sphericalVertexDistance;
                    out float cylindricalVertexDistance;
                    out vec2 texCoord0;
                    void main() {
                        vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                        gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                        texCoord0 = p; sphericalVertexDistance = 0.0; cylindricalVertexDistance = 0.0;
                    }
                    """);
            int fragment = shader(GL_FRAGMENT_SHADER, fragment());
            int program = glCreateProgram(); glAttachShader(program, vertex); glAttachShader(program, fragment);
            glLinkProgram(program);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
            glUseProgram(program);
            block(program, "Globals", 0, new String[]{"GlintAlpha"}, new float[]{1});
            block(program, "Fog", 1, new String[]{"FogEnvironmentalStart", "FogEnvironmentalEnd", "FogRenderDistanceStart", "FogRenderDistanceEnd"}, new float[]{100, 200, 100, 200});
            int dynamicIndex = glGetUniformBlockIndex(program, "DynamicTransforms");
            ByteBuffer dynamic = BufferUtils.createByteBuffer(glGetActiveUniformBlocki(program, dynamicIndex, GL_UNIFORM_BLOCK_DATA_SIZE));
            int offset = glGetActiveUniformsi(program, glGetUniformIndices(program, "ColorModulator"), GL_UNIFORM_OFFSET);
            for (int i = 0; i < 4; i++) dynamic.putFloat(offset + i * 4, 1);
            int buffer = glGenBuffers(); glBindBuffer(GL_UNIFORM_BUFFER, buffer); glBufferData(GL_UNIFORM_BUFFER, dynamic, GL_STATIC_DRAW);
            glBindBufferBase(GL_UNIFORM_BUFFER, 2, buffer); glUniformBlockBinding(program, dynamicIndex, 2);
            int texture = glGenTextures(); glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);
            glBindVertexArray(glGenVertexArrays()); glViewport(0, 0, 16, 16);
            glEnable(GL_BLEND); glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
            for (int selected : new int[]{0xff000000, 0xffffffff}) for (int intensity : new int[]{0, 50, 100}) {
                int masked = GlintMask.colorize(0xffffffff, selected, intensity);
                ByteBuffer texel = BufferUtils.createByteBuffer(4);
                texel.put((byte)(masked >>> 16)).put((byte)(masked >>> 8)).put((byte)masked).put((byte)(masked >>> 24)).flip();
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, texel);
                glClearColor(0.5f, 0.5f, 0.5f, 1); glClear(GL_COLOR_BUFFER_BIT);
                glDrawArrays(GL_TRIANGLES, 0, 3);
                ByteBuffer pixel = BufferUtils.createByteBuffer(4); glReadPixels(8, 8, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
                int actual = pixel.get(0) & 255;
                double alpha = (masked >>> 24) / 255.0;
                int expected = (int)Math.round(128 * (1-alpha) + (selected & 255) * alpha);
                assertEquals(expected, actual, 2, "selected=" + Integer.toHexString(selected) + " intensity=" + intensity);
                assertEquals(255, pixel.get(3) & 255, "Destination alpha must survive");
            }
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            GL.setCapabilities(null);
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
