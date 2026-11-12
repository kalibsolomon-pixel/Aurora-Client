package parityprobe;

import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.NativeShapeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Fixed visual cases for manual CPU/shader comparison in a disposable client. */
final class ShapeTestScreen extends Screen {
    private boolean recorded;
    ShapeTestScreen() { super(Component.literal("Shape comparison")); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xFF20242B);
        g.text(font, "Filled / outline / squircle / circle", 12, 10, 0xFFFFFFFF, false);
        float[] radii = {1, 2, 4, 8, 16};
        for (int i = 0; i < radii.length; i++) {
            float y = 30 + i * 30, r = radii[i];
            RenderUtil.drawRoundedRectAA(g, 12.25f, y + .25f, 75, 20, r, 0xB0A8CEF8);
            RenderUtil.drawRoundedOutlineAA(g, 100.25f, y + .25f, 75, 20, r, 1, 0xD0F0B070);
            RenderUtil.drawSquircle(g, 188.25f, y + .25f, 75, 20, r, 0xB0A8CEF8);
            RenderUtil.drawSquircleOutline(g, 276.25f, y + .25f, 75, 20, r, 1, 0xD0F0B070);
            RenderUtil.drawCircleAA(g, 364.25f, y + .25f, 20, 20, 0xB0A8CEF8);
        }
        g.text(font, "Scissor / transformed", 12, 190, 0xFFFFFFFF, false);
        g.enableScissor(12, 210, 65, 230);
        RenderUtil.drawRoundedRectAA(g, 0, 205, 90, 30, 10, 0xFF70C090);
        g.disableScissor();
        g.pose().pushMatrix();
        g.pose().translate(120, 210);
        g.pose().rotate(.25f);
        RenderUtil.drawRoundedRectAA(g, 0, 0, 70, 20, 8, 0xFF70C090);
        g.pose().popMatrix();
        g.pose().pushMatrix();
        g.pose().translate(240, 210);
        g.pose().scale(.25f, .25f);
        RenderUtil.drawRoundedRectAA(g, 0, 0, 280, 80, 32, 0xFF70C090);
        g.pose().popMatrix();
        // A wide shape must still submit one quad when clipped to the viewport.
        long before = NativeShapeRenderer.quadsSubmitted();
        long fillsBefore = RenderUtil.fillsSubmitted();
        g.enableScissor(320, 210, 400, 230);
        RenderUtil.drawRoundedRectAA(g, 320, 210,
                2600f / Minecraft.getInstance().getWindow().getGuiScale(), 20, 8, 0xFF70C090);
        g.disableScissor();
        if (Boolean.getBoolean("aurora.shapes.expectFallback")) {
            if (NativeShapeRenderer.quadsSubmitted() != before || RenderUtil.fillsSubmitted() <= fillsBefore)
                throw new AssertionError("Broken shader did not fall back to CPU fills");
        } else if (!Boolean.getBoolean("aurora.shapes.cpu") && NativeShapeRenderer.quadsSubmitted() != before + 1) {
            throw new AssertionError("A 2600-pixel shape did not use one shader quad");
        }
        if (!recorded) {
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("aurora.parity.probe"))
                        .resolve("shape-cases-rendered.txt"), "OK\n");
                recorded = true;
            } catch (java.io.IOException failure) {
                throw new RuntimeException("Could not record shape fixture rendering", failure);
            }
        }
    }
}
