package com.aurora.client.util;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GuiLayeringTest {
    private static final Map<GuiElementRenderState, Integer> IDS = new IdentityHashMap<>();

    /** A test element as a proxy: the pipeline class differs in package between lines, so it is never named. */
    private static GuiElementRenderState element(Object pipeline, TextureSetup texture, ScreenRectangle scissor,
                                                 ScreenRectangle bounds, int id) {
        var element = (GuiElementRenderState) Proxy.newProxyInstance(GuiLayeringTest.class.getClassLoader(),
                new Class<?>[]{GuiElementRenderState.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "pipeline" -> pipeline;
                    case "textureSetup" -> texture;
                    case "scissorArea" -> scissor;
                    case "bounds" -> bounds;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "element " + id;
                    default -> null;
                });
        IDS.put(element, id);
        return element;
    }

    private static ScreenRectangle rect(Random random, boolean small) {
        int x = random.nextInt(-20, 400), y = random.nextInt(-20, 300);
        int span = small ? 12 : 220;
        return new ScreenRectangle(x, y, random.nextInt(0, span), random.nextInt(0, span));
    }

    /** What vanilla's {@code GuiRenderState.hasIntersection} computes. */
    private static boolean vanillaScan(List<GuiElementRenderState> list, ScreenRectangle query) {
        for (var e : list) if (e.bounds() != null && e.bounds().intersects(query)) return true;
        return false;
    }

    @Test void indexedLayersAnswerExactlyAsVanillaScans() {
        Random random = new Random(2026_11_20);
        for (int layer = 0; layer < 400; layer++) {
            boolean small = layer % 2 == 0;
            var indexed = new GuiAreaList<GuiElementRenderState>();
            var plain = new ArrayList<GuiElementRenderState>();
            for (int i = random.nextInt(0, 40); i > 0; i--) {
                var e = element(RenderPipelines.GUI, null, null, random.nextInt(8) == 0 ? null : rect(random, small), i);
                indexed.add(e);
                plain.add(e);
            }
            assertTrue(indexed.indexed());
            for (int q = 0; q < 60; q++) {
                ScreenRectangle query = rect(random, q % 3 == 0);
                assertEquals(vanillaScan(plain, query), indexed.intersects(query), "layer " + layer + " query " + query);
            }
        }
    }

    @Test void onlyAppendingKeepsTheIndex() {
        var e = element(RenderPipelines.GUI, null, null, new ScreenRectangle(0, 0, 10, 10), 0);
        var list = new GuiAreaList<GuiElementRenderState>();
        list.add(e);
        list.sort(Comparator.comparingInt(IDS::get));
        assertTrue(list.indexed(), "reordering keeps the same bounds");
        list.remove(0);
        assertFalse(list.indexed(), "a removed element must not answer for the list");
        list.clear();
        assertTrue(list.indexed());
        assertFalse(list.intersects(new ScreenRectangle(0, 0, 10, 10)));
        list.add(e);
        var iterator = list.iterator();
        iterator.next();
        iterator.remove();
        assertFalse(list.indexed());
        list.clear();
        list.add(e);
        list.set(0, e);
        assertFalse(list.indexed());
        list.clear();
        list.add(e);
        @SuppressWarnings("unchecked") var copy = (GuiAreaList<GuiElementRenderState>) list.clone();
        assertFalse(copy.indexed(), "a copy shares the bounds array");
        assertTrue(list.indexed());
    }

    @Test void elementOrderMatchesVanillaComparator() throws Exception {
        var field = GuiRenderer.class.getDeclaredField("ELEMENT_SORT_COMPARATOR");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var vanilla = (Comparator<GuiElementRenderState>) field.get(null);
        Object[] pipelines = {RenderPipelines.GUI, RenderPipelines.GUI_TEXTURED, RenderPipelines.GUI_TEXT,
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA};
        TextureSetup none = TextureSetup.noTexture();
        TextureSetup[] textures = {null, none, new TextureSetup(null, null, null, null, null, null)};
        ScreenRectangle shared = new ScreenRectangle(5, 5, 40, 40);
        Random random = new Random(7);
        for (int round = 0; round < 300; round++) {
            List<GuiElementRenderState> elements = new ArrayList<>();
            for (int i = random.nextInt(1, 60); i > 0; i--) {
                ScreenRectangle scissor = switch (random.nextInt(4)) {
                    case 0 -> null;
                    case 1 -> shared;
                    case 2 -> new ScreenRectangle(5, 5, 40, random.nextInt(39, 42));
                    default -> rect(random, true);
                };
                elements.add(element(pipelines[random.nextInt(pipelines.length)], textures[random.nextInt(textures.length)],
                        scissor, null, i));
            }
            for (var a : elements) {
                for (var b : elements) {
                    assertEquals(Integer.signum(vanilla.compare(a, b)), Integer.signum(GuiElementOrder.INSTANCE.compare(a, b)));
                }
            }
            var byVanilla = new ArrayList<>(elements);
            var byAurora = new ArrayList<>(elements);
            byVanilla.sort(vanilla);
            byAurora.sort(GuiElementOrder.INSTANCE);
            assertEquals(byVanilla, byAurora, "stable sort order");
        }
        IDS.clear();
    }
}
