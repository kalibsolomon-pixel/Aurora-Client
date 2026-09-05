package com.aurora.client.ui.util;

import com.aurora.client.mixin.ItemStackRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Crisp flat-sprite item icon rendering for UI lists (the Item Scale
 * configurator's added-item tabs).
 *
 * <p>{@link GuiGraphics#renderItem} submits the item through the 3D model
 * pipeline (perspective transform, directional light) — correct for hotbars
 * and tooltips, but soft and inconsistently lit in dense UI lists. This
 * renderer instead resolves the item's <b>flat GUI sprite</b> through the
 * same {@code ItemModelResolver} pass vanilla itself uses
 * ({@code updateForTopItem} with {@link ItemDisplayContext#GUI}), then blits
 * the sprite straight from the block atlas with exact UVs — the same crisp,
 * pixel-aligned path effect icons ({@code PotionModule}) and module PNG
 * icons use.
 *
 * <p>Safety gate: only single-layer models whose quads are all untinted
 * take the sprite path (tools, weapons, ingots — most plain items).
 * Tinted items (grass, leather, potions) and multi-layer or special models
 * (bows, tridents) would mis-color or mis-composite as a flat sprite, so
 * they fall back to the standard {@link GuiGraphics#renderItem}. The
 * outcome is cached per item id for the session — resolution is a full
 * model-graph walk, and the result is stable until a resource reload
 * rebuilds the atlas.
 */
public final class ItemSpriteRenderer {

    /** Resolution outcome per item id; {@code null} sprite = 3D fallback. */
    private record Resolution(TextureAtlasSprite sprite) {}

    private static final Map<Identifier, Resolution> CACHE = new HashMap<>();

    private ItemSpriteRenderer() {}

    /**
     * Draws {@code stack}'s icon at {@code (x, y)} in crisp flat-sprite form
     * when the item qualifies, otherwise through the standard 3D
     * {@link GuiGraphics#renderItem} path.
     */
    public static void renderIcon(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        if (stack == null || stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            g.renderItem(stack, x, y);
            return;
        }

        TextureAtlasSprite sprite = resolve(mc, stack);
        if (sprite == null) {
            g.renderItem(stack, x, y);
            return;
        }

        // Blit the sprite straight from its atlas — pixel-aligned at any GUI
        // scale, no model pass, no lighting (the effect-icon path).
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, size, size);
    }

    /** Convenience overload for the standard 16px list icon. */
    public static void renderIcon(GuiGraphics g, ItemStack stack, int x, int y) {
        renderIcon(g, stack, x, y, 16);
    }

    private static TextureAtlasSprite resolve(Minecraft mc, ItemStack stack) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return null;
        Resolution r = CACHE.get(id);
        if (r == null) {
            r = resolveUncached(mc, stack);
            CACHE.put(id, r);
        }
        return r.sprite();
    }

    /**
     * One resolution pass. The state is read-only here — the quads the
     * resolver baked are inspected, never submitted, so a fresh local
     * instance per call is safe and cheap (once per item id, cached).
     */
    private static Resolution resolveUncached(Minecraft mc, ItemStack stack) {
        try {
            ItemStackRenderState state = new ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(state, stack,
                    ItemDisplayContext.GUI, mc.level, mc.player, 0);

            ItemStackRenderState.LayerRenderState[] layers =
                    ((ItemStackRenderStateAccessor) state).aurora$layers();
            int count = ((ItemStackRenderStateAccessor) state).aurora$activeLayerCount();
            if (layers == null || count != 1 || layers[0] == null) {
                return new Resolution(null); // empty or multi-layer model
            }

            List<BakedQuad> quads = layers[0].prepareQuadList();
            if (quads == null || quads.isEmpty()) return new Resolution(null);

            TextureAtlasSprite sprite = null;
            for (BakedQuad q : quads) {
                if (q.isTinted()) return new Resolution(null); // flat blit can't tint
                TextureAtlasSprite s = q.sprite();
                if (s == null) return new Resolution(null);
                if (sprite == null) sprite = s;
                else if (sprite != s) return new Resolution(null); // mixed sprites ≠ flat item
            }
            return new Resolution(sprite);
        } catch (Throwable t) {
            return new Resolution(null); // any resolver surprise → 3D fallback, never crash
        }
    }
}
