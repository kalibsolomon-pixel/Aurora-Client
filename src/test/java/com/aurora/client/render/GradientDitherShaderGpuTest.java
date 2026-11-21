package com.aurora.client.render;

import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL33.*;

/**
 * This line's real vanilla sky and sunrise shaders, patched, drawn into an 8-bit target in an
 * invisible GL context: without the define they draw exactly what vanilla draws; with it a
 * slow gradient loses its band edges, every pixel stays within one step of the exact value,
 * 8x8 tiles keep the exact average, and a colour that is already an exact step is unchanged.
 */
class GradientDitherShaderGpuTest {
    private static final int W = 512, H = 64;

    private static String expand(String source, Set<String> seen) {
        Matcher m = Pattern.compile("(?m)^[ \\t]*#(?:include|moj_import)[ \\t]*<minecraft:([^>]+)>[ \\t]*$").matcher(source);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String body = seen.add(m.group(1))
                    ? expand(GradientDitherTest.vanilla("shaders/include/" + m.group(1)), seen).replaceAll("(?m)^#version.*$", "") : "";
            m.appendReplacement(out, Matcher.quoteReplacement(body));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** A fragment source as the game compiles it: includes expanded, the pipeline define after #version. */
    private static String fragment(String source, int mode) {
        String expanded = expand(source, new HashSet<>());
        int line = expanded.indexOf('\n') + 1;
        String define = mode == 0 ? "" : "#define " + GradientDither.DEFINE + " " + mode + "\n";
        return expanded.substring(0, line) + define + expanded.substring(line);
    }

    private static int shader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
        return shader;
    }

    /** A full-screen triangle; {@code x} runs 0 to 1 across the target. */
    private static String vertex(boolean located, String outputs, String body) {
        return "#version 330\n" + (located ? "#extension GL_ARB_separate_shader_objects : require\n" : "") + outputs + """
                void main() {
                    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                    float x = p.x;
                """ + body + "}\n";
    }

    private static int program(String vertexSource, String fragmentSource) {
        int program = glCreateProgram();
        glAttachShader(program, shader(GL_VERTEX_SHADER, vertexSource));
        glAttachShader(program, shader(GL_FRAGMENT_SHADER, fragmentSource));
        glLinkProgram(program);
        assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
        return program;
    }

    private static void block(int program, String name, int binding, String[] fields, float[][] values) {
        int index = glGetUniformBlockIndex(program, name);
        if (index == GL_INVALID_INDEX) return;
        ByteBuffer bytes = BufferUtils.createByteBuffer(glGetActiveUniformBlocki(program, index, GL_UNIFORM_BLOCK_DATA_SIZE));
        for (int i = 0; i < fields.length; i++) {
            int uniform = glGetUniformIndices(program, fields[i]);
            if (uniform == GL_INVALID_INDEX) continue;
            int offset = glGetActiveUniformsi(program, uniform, GL_UNIFORM_OFFSET);
            for (int c = 0; c < values[i].length; c++) bytes.putFloat(offset + c * 4, values[i][c]);
        }
        int buffer = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, buffer);
        glBufferData(GL_UNIFORM_BUFFER, bytes, GL_STATIC_DRAW);
        glBindBufferBase(GL_UNIFORM_BUFFER, binding, buffer);
        glUniformBlockBinding(program, index, binding);
    }

    private static float[] grey(double v, double a) {
        return new float[]{(float) v, (float) v, (float) v, (float) a};
    }

    /** Draws once and returns the target's RGBA bytes. */
    private static byte[] draw(int program, float[] modulator, float[] fog, int clear) {
        glUseProgram(program);
        block(program, "Fog", 0, new String[]{"FogColor", "FogEnvironmentalStart", "FogEnvironmentalEnd",
                        "FogRenderDistanceStart", "FogRenderDistanceEnd", "FogSkyEnd", "FogCloudsEnd"},
                new float[][]{fog, {1e9f}, {1e9f}, {1e9f}, {1e9f}, {100}, {1e9f}});
        block(program, "DynamicTransforms", 1, new String[]{"ColorModulator"}, new float[][]{modulator});
        glClearColor(clear / 255f, clear / 255f, clear / 255f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        ByteBuffer pixels = BufferUtils.createByteBuffer(W * H * 4);
        glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] out = new byte[W * H * 4];
        pixels.get(out);
        return out;
    }

    private static int red(byte[] rgba, int x, int y) { return rgba[(y * W + x) * 4] & 255; }

    /**
     * The mean of each 8-pixel-wide block over the full height, in 8-bit steps: the gradient as
     * a viewer sees it from a little away, where single pixels blur into their 8x8 cell.
     */
    private static double[] blockMeans(byte[] rgba, int width) {
        double[] means = new double[width / 8];
        for (int x = 0; x < width / 8 * 8; x++) for (int y = 0; y < H; y++) means[x / 8] += red(rgba, x, y) / (8.0 * H);
        return means;
    }

    /**
     * Band edges: neighbouring blocks whose means differ by half a step or more. These ramps
     * rise far less than half a step per block, so every such jump is a band edge.
     */
    private static int bandEdges(byte[] rgba, int width) {
        double[] means = blockMeans(rgba, width);
        int edges = 0;
        for (int i = 1; i < means.length; i++) if (Math.abs(means[i] - means[i - 1]) >= 0.5) edges++;
        return edges;
    }

    /** The largest difference between a block's mean and the exact gradient's, in steps. */
    private static double worstBlockError(byte[] rgba, int width, java.util.function.DoubleUnaryOperator exact) {
        double[] means = blockMeans(rgba, width);
        double worst = 0;
        for (int i = 0; i < means.length; i++) {
            double want = 0;
            for (int x = i * 8; x < i * 8 + 8; x++) want += exact.applyAsDouble((x + 0.5) / W) * 255 / 8;
            worst = Math.max(worst, Math.abs(means[i] - want));
        }
        return worst;
    }

    /**
     * Checks a dithered draw against the exact value at each pixel centre, in 8-bit steps, over
     * the first {@code width} pixels. {@code slack} is how far past one step a blended pixel may
     * land: the blend rounds the result itself, and AMD's driver exports the source through a
     * truncated half float.
     */
    private static void assertDithered(byte[] rgba, byte[] vanilla, java.util.function.DoubleUnaryOperator exact,
                                       int width, double slack, String what) {
        double worstTile = 0, total = 0;
        for (int ty = 0; ty < H; ty += 8) for (int tx = 0; tx + 8 <= width; tx += 8) {
            double error = 0;
            for (int y = ty; y < ty + 8; y++) for (int x = tx; x < tx + 8; x++) {
                double want = exact.applyAsDouble((x + 0.5) / W) * 255;
                double got = red(rgba, x, y);
                assertTrue(Math.abs(got - want) < 1.0 + slack, what + ": pixel " + x + "," + y + " is " + got + ", exact " + want);
                error += got - want;
            }
            worstTile = Math.max(worstTile, Math.abs(error / 64));
            total += error;
        }
        int checked = width / 8 * 8 * H;
        double vanillaTotal = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < width / 8 * 8; x++) vanillaTotal += red(vanilla, x, y) - exact.applyAsDouble((x + 0.5) / W) * 255;
        System.out.println(String.format("[gradient-dither] %s: band edges %d -> %d, worst 8-px block error %.3f -> %.3f steps,"
                        + " mean error %.4f -> %.4f steps", what, bandEdges(vanilla, width), bandEdges(rgba, width),
                worstBlockError(vanilla, width, exact), worstBlockError(rgba, width, exact), vanillaTotal / checked, total / checked));
        assertTrue(worstTile < 0.1, what + ": an 8x8 tile's mean is off by " + worstTile + " steps");
        assertTrue(Math.abs(total / checked) < 0.02 + slack / 4, what + ": the mean is off by " + total / checked + " steps");
        assertEquals(0, bandEdges(rgba, width), what + ": band edges left");
        assertTrue(worstBlockError(rgba, width, exact) < 0.1, what + ": the blocks still step");
    }

    @Test void theRealSkyAndSunriseShadersLoseTheirBandsAndKeepTheirAverage() {
        try (var ignored = HiddenGlContext.open("Gradient dither test")) {
            int target = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, target);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            int framebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
            glViewport(0, 0, W, H);
            glBindVertexArray(glGenVertexArrays());

            // The sky disc: opaque, ColorModulator fading into FogColor across the target.
            String sky = GradientDitherTest.vanilla("shaders/core/sky.fsh");
            boolean located = sky.contains("layout(location");
            String loc0 = located ? "layout(location = 0) " : "", loc1 = located ? "layout(location = 1) " : "";
            String skyVertex = vertex(located, loc0 + "out float sphericalVertexDistance;\n" + loc1 + "out float cylindricalVertexDistance;\n",
                    "    sphericalVertexDistance = x * 100.0;\n    cylindricalVertexDistance = 0.0;\n");
            int vanillaSky = program(skyVertex, fragment(sky, 0));
            int plainSky = program(skyVertex, fragment(GradientDither.patch(sky), 0));
            int ditheredSky = program(skyVertex, fragment(GradientDither.patch(sky), GradientDither.Mode.OPAQUE.value));
            float[] low = grey(40 / 255.0, 1), high = grey(52 / 255.0, 1);
            byte[] vanilla = draw(vanillaSky, low, high, 0);
            assertArrayEquals(vanilla, draw(plainSky, low, high, 0), "without the define the patched shader is vanilla");
            assertTrue(bandEdges(vanilla, W) >= 12, "vanilla's 40-52 ramp has a band edge per step");
            assertDithered(draw(ditheredSky, low, high, 0), vanilla, x -> (40 + 12 * x) / 255, W, 0, "sky");
            for (int step : new int[]{0, 1, 37, 128, 254, 255}) {
                float[] flat = grey(step / 255.0, 1);
                byte[] out = draw(ditheredSky, flat, flat, 0);
                for (int i = 0; i < out.length; i += 4) assertEquals(step, out[i] & 255, "an exact step " + step + " is never changed");
            }

            // The sunrise fan: straight alpha over the sky's colour, alpha falling slowly to 0.
            String fan = GradientDitherTest.vanilla("shaders/core/position_color.fsh");
            String fanVertex = vertex(located, loc0 + "out vec4 vertexColor;\n",
                    "    vertexColor = vec4(0.6, 0.6, 0.6, 0.12 * (1.0 - x));\n");
            int plainFan = program(fanVertex, fragment(GradientDither.patch(fan), 0));
            int vanillaFan = program(fanVertex, fragment(fan, 0));
            int ditheredFan = program(fanVertex, fragment(GradientDither.patch(fan), GradientDither.Mode.STRAIGHT_ALPHA.value));
            glEnable(GL_BLEND);
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            float[] white = grey(1, 1);
            byte[] vanillaBlend = draw(vanillaFan, white, white, 44);
            assertArrayEquals(vanillaBlend, draw(plainFan, white, white, 44), "without the define the patched shader is vanilla");
            assertTrue(bandEdges(vanillaBlend, W) >= 12, "vanilla's fan bands");
            byte[] ditheredBlend = draw(ditheredFan, white, white, 44);
            // Below alpha 1 / (510 * min(c, 1 - c)) the full offset would push the source out of
            // [0, 1], so the dither fades out over the fan's last few transparent pixels.
            int headroom = (int) (W * (1 - 1 / (0.12 * 510 * 0.4)));
            assertDithered(ditheredBlend, vanillaBlend, x -> 0.6 * 0.12 * (1 - x) + 44 / 255.0 * (1 - 0.12 * (1 - x)), headroom, 0.125, "sunrise fan");
            for (int i = 3; i < ditheredBlend.length; i += 4) assertEquals(vanillaBlend[i], ditheredBlend[i], "alpha is never dithered");
            glDisable(GL_BLEND);

            // Every other patched vanilla shader still compiles in all three variants.
            for (String path : GradientDitherTest.vanillaShaders()) {
                String patched = GradientDither.patch(GradientDitherTest.vanilla(path));
                for (int mode = 0; mode <= 2; mode++) glDeleteShader(shader(GL_FRAGMENT_SHADER, fragment(patched, mode)));
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }
}
