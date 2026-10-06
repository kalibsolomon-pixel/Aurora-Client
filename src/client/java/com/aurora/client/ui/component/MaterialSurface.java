package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraShapes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import com.aurora.client.ui.util.UiLayerCache;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Material treatments for surfaces that do not need another live-backdrop
 * glass sample.
 *
 * <p>The unified pilot reads separate neutral/action/selection roles here.
 * Its parent captures pre-dim, then the host paints one cached neutral face
 * and boundary above dim. Embedded children never capture; floating faces
 * retain independent opaque backing. Legacy recipes below remain outside
 * the two opt-in hosts (UNIFIED_THEME_PILOT.md).
 *
 * <p>A child already enclosed by window/panel glass uses
 * {@link #embeddedControl}: its parent owns blur and the outer rim, while
 * the child contributes only a restrained local tint. Selected/primary
 * children may still use the established stained tint because that tint
 * communicates state; neutral children never add another opacity-driven
 * {@code WINDOW_FILL} layer. This is Phase E's central nested-material rule.
 *
 * <p>A transient surface drawn above ordinary content uses
 * {@link #floating}: a stable semantic {@code SURFACE} backing, one
 * low-emphasis edge and the existing shadow. Neither path invokes the blur
 * renderer, allocates per frame, or creates another rim owner.
 */
public final class MaterialSurface {

    private MaterialSurface() {}

    /** Local neutral tint over a parent material; not a second opacity control. */
    static final int EMBEDDED_ALPHA = 0x24;

    /** Screen-owned pilot boundary; never persisted or applied to another screen. */
    public static boolean unified() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.screen instanceof ThemedScreen screen && screen.unifiedThemePilot();
    }

    /** Compatibility mapping used only by pilot painters. */
    public static int color(ThemeToken token) {
        if (!unified()) return ThemeManager.color(token);
        ThemeToken role = switch (token) {
            case BACKGROUND -> ThemeToken.MATERIAL_CANVAS;
            case SURFACE, WINDOW_FILL -> ThemeToken.MATERIAL_PANEL;
            case SURFACE_VARIANT -> ThemeToken.MATERIAL_RAISED;
            case SURFACE_INSET -> ThemeToken.MATERIAL_SUNKEN;
            case BORDER, WINDOW_OUTLINE -> ThemeToken.MATERIAL_BORDER;
            case BORDER_HOVER -> ThemeToken.MATERIAL_BORDER_STRONG;
            case ON_BACKGROUND, ON_SURFACE -> ThemeToken.MATERIAL_TEXT;
            case ON_BACKGROUND_SECONDARY -> ThemeToken.MATERIAL_TEXT_SECONDARY;
            case ON_BACKGROUND_MUTED, ON_BACKGROUND_FAINT -> ThemeToken.MATERIAL_TEXT_MUTED;
            case ACCENT -> ThemeToken.ACCENT_MARKER;
            default -> token;
        };
        int result = ThemeManager.color(role);
        return token == ThemeToken.WINDOW_FILL
                ? ThemeManager.withAlpha(result, ThemeManager.color(ThemeToken.WINDOW_FILL) >>> 24) : result;
    }

    /** Outside focus follows the corner family: Square must remain exactly square. */
    public static float outsetRadius(float radius,float outset) {
        return radius==0 ? 0 : radius+outset;
    }

    public static int selectedFill() { return ThemeManager.color(ThemeToken.SELECTION_SOFT); }

    /** Same role geometry and backing for success/decline. Child roles never sample. */
    public static void selection(GuiGraphics g, float x, float y, float w, float h,
                                 float radius, boolean selected, float hover) {
        int neutral = AuroraAnim.lerpArgb(color(ThemeToken.SURFACE_VARIANT),
                ThemeManager.color(ThemeToken.MATERIAL_HOVER), hover);
        if (hover == 0 || hover == 1) {
            cached(g,x,y,w,h,radius,neutral,0,(selected ? 2 : 1)+(hover==1 ? 100 : 0),false);
            return;
        }
        RenderUtil.drawRoundedRectAA(g,x,y,w,h,radius,neutral);
        if (selected) {
            RenderUtil.drawRoundedRectAA(g,x,y,w,h,radius,selectedFill());
            // Compact leading marker carries persistent state, no full perimeter.
            RenderUtil.drawRoundedRectAA(g,x+2,y+(h-Math.min(16,Math.max(2,h-10)))/2,2,Math.min(16,Math.max(2,h-10)),0,color(ThemeToken.ACCENT));
        }
    }

    // Primitive packed geometry key: no record/boxing/lambda allocation per control per frame.
    private static final Long2ObjectOpenHashMap<UiLayerCache> TEMPLATES = new Long2ObjectOpenHashMap<>();

    /** Called from the pilot hosts' existing screen-removal boundary, between frames. */
    public static void disposeTemplates() {
        for(UiLayerCache cache:TEMPLATES.values()) cache.dispose();
        TEMPLATES.clear();
    }

    /** Rasterize elevation/fill/edge once per size and role, then one blit.
     * Theme reload reuploads in place; no texture is destroyed mid-frame. */
    public static void cached(GuiGraphics g,float x,float y,float w,float h,float radius,
                              int fill,int border,int kind,boolean shadow) {
        cached(g,x,y,w,h,radius,fill,border,kind,shadow,1);
    }

    private static void cached(GuiGraphics g,float x,float y,float w,float h,float radius,
                               int fill,int border,int kind,boolean shadow,float opacity) {
        var mc=Minecraft.getInstance();
        int scale=Math.max(1,(int)mc.getWindow().getGuiScale());
        int pad=shadow ? 10 : 2;
        int width=Math.round(w), height=Math.round(h), round=Math.round(radius);
        long key=((long)width<<48) | ((long)height<<32) | ((long)round<<16) | ((long)kind<<1) | (shadow ? 1 : 0);
        var cache=TEMPLATES.get(key);
        if(cache==null) { cache=new UiLayerCache(); TEMPLATES.put(key,cache); }
        long version=ThemeManager.generation()*65537L+scale;
        if(!cache.isCurrent(version)) {
            cache.ensureSize((width+pad*2)*scale,(height+pad*2)*scale); cache.clear();
            var previous=RenderUtil.beginCapture(cache.sink());
            try {
                if(shadow) AuroraShapes.dropShadow(g,pad,pad+3,width,height,round,.25f);
                if(fill!=0) RenderUtil.drawRoundedRectAA(g,pad,pad,w,h,radius,fill);
                if(border!=0) RenderUtil.drawRoundedOutlineAA(g,pad,pad,w,h,radius,1,border);
                if(kind==2 || kind==102) {
                    RenderUtil.drawRoundedRectAA(g,pad,pad,w,h,radius,selectedFill());
                    RenderUtil.drawRoundedRectAA(g,pad+2,pad+(h-Math.min(16,Math.max(2,h-10)))/2,2,Math.min(16,Math.max(2,h-10)),0,color(ThemeToken.ACCENT));
                }
            } finally { RenderUtil.endCapture(previous); }
            cache.commit(version);
        }
        cache.blitAt(g,Math.round(x)-pad,Math.round(y)-pad,width+pad*2,height+pad*2,
                (Math.round(opacity*255)<<24)|0xffffff);
    }

    public static void embeddedControl(GuiGraphics g, float x, float y, float w, float h,
                                       float radius, boolean stained) {
        int fill = stained
                ? ThemeManager.stainedTint()
                : ThemeManager.withAlpha(ThemeManager.color(ThemeToken.SURFACE_VARIANT), EMBEDDED_ALPHA);
        RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, fill);
    }

    public static void floating(GuiGraphics g, int x, int y, int w, int h,
                                int radius, float fade) {
        float t = Math.max(0f, Math.min(1f, fade));
        if (unified()) {
            // Uniform texture-alpha fade; the opaque face/soft shadow are
            // rasterized once, including throughout the transition.
            cached(g,x,y,w,h,radius,color(ThemeToken.SURFACE_VARIANT),
                    color(ThemeToken.BORDER_HOVER),4,true,t);
            return;
        }
        AuroraShapes.dropShadow(g, x, y, w, h, radius, t);
        AuroraShapes.panel(g, x, y, w, h,
                AuroraAnim.scaleAlpha(ThemeManager.color(ThemeToken.SURFACE), t), radius);
        AuroraShapes.outline(g, x, y, w, h,
                AuroraAnim.scaleAlpha(ThemeManager.color(ThemeToken.BORDER), 0.55f * t), radius);
    }
}
