package com.aurora.client.render;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Invisible OpenGL 3.3 core context for GPU shader tests on GLFW lines (26.1, 26.2). */
final class HiddenGlContext implements AutoCloseable {
    private final long window;

    private HiddenGlContext(long window) {
        this.window = window;
    }

    static HiddenGlContext open(String title) {
        assumeTrue(GLFW.glfwInit(), "No GLFW display available");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(16, 16, title, 0, 0);
        if (window == 0) {
            GLFW.glfwTerminate();
            assumeTrue(false, "No OpenGL context available");
        }
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        return new HiddenGlContext(window);
    }

    @Override
    public void close() {
        GL.setCapabilities(null);
        if (window != 0) GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }
}
