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

/** Shared cached material for parent faces, embedded controls and opaque floating layers.
 * The parent alone captures the backdrop before dim; its neutral face/boundary
 * is painted after dim. Selection is a soft wash and one accent edge, primary is a readable
 * strong action. No child allocates another blur output or owns a glossy rim.
 */
public final class MaterialSurface {

    private MaterialSurface() {}

    /** Local neutral tint over a parent material; not a second opacity control. */
    static final int EMBEDDED_ALPHA = 0x24;

    /** Production screen context; includes the explicitly Aurora-styled vanilla selection screens. */
    public static boolean unified() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && (mc.screen instanceof ThemedScreen screen && screen.unifiedTheme()
                || com.aurora.client.config.AuroraConfig.get().customTitleScreen &&
                   (mc.screen instanceof net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
                    || mc.screen instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen));
    }

    /** Compatibility mapping for material-aware painters. */
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
        return token == ThemeToken.WINDOW_FILL ? ThemeManager.color(token) : result;
    }

    /** Outside focus follows the corner family: Square must remain exactly square. */
    public static float outsetRadius(float radius,float outset) {
        return radius==0 ? 0 : radius+outset;
    }

    /** Cached parent face, drawn once above the screen dim even when capture declines. */
    public static void parent(GuiGraphics g, float x, float y, float w, float h, float radius) {
        cached(g,x,y,w,h,radius,ThemeManager.color(ThemeToken.WINDOW_FILL),
                color(ThemeToken.BORDER),120,true);
    }

    public static int selectedFill() { return ThemeManager.color(ThemeToken.SELECTION_SOFT); }

    /** Same role geometry and backing for success/decline. Child roles never sample. */
    public static void selection(GuiGraphics g, float x, float y, float w, float h,
                                 float radius, boolean selected, float hover) {
        selection(g,x,y,w,h,radius,selected,hover,false);
    }

    /**
     * {@code flatRest}: an unselected peer has no resting face and only gains the neutral
     * face while hovered (sidebar items inside an already-material window, §11.11).
     */
    public static void selection(GuiGraphics g, float x, float y, float w, float h,
                                 float radius, boolean selected, float hover, boolean flatRest) {
        if(flatRest && !selected) {
            if(hover<=0) return;
            int face = ThemeManager.withAlpha(ThemeManager.color(ThemeToken.MATERIAL_HOVER),
                    Math.round((ThemeManager.color(ThemeToken.WINDOW_FILL) >>> 24) * Math.min(1f,hover)));
            RenderUtil.drawRoundedRectAA(g,x,y,w,h,radius,face);
            return;
        }
        int neutral = AuroraAnim.lerpArgb(color(ThemeToken.SURFACE_VARIANT),
                ThemeManager.color(ThemeToken.MATERIAL_HOVER), hover);
        neutral = ThemeManager.withAlpha(neutral, ThemeManager.color(ThemeToken.WINDOW_FILL) >>> 24);
        if (hover == 0 || hover == 1) {
            cached(g,x,y,w,h,radius,neutral,selected ? ThemeManager.color(ThemeToken.ACCENT_MARKER) : 0,(selected ? 2 : 1)+(hover==1 ? 100 : 0),false);
            return;
        }
        RenderUtil.drawRoundedRectAA(g,x,y,w,h,radius,neutral);
        if (selected) {
            RenderUtil.drawRoundedRectAA(g,x,y,w,h,radius,selectedFill());
            RenderUtil.drawRoundedOutlineAA(g,x,y,w,h,radius,1,ThemeManager.color(ThemeToken.ACCENT_MARKER));
        }
    }

    /**
     * The one face for trailing field controls in setting rows — dropdown, keybind and row
     * action button share it so a settings column never mixes three treatments. Opaque
     * raised tone (legible at any window opacity), no outline; hover lifts the tone.
     */
    public static void field(GuiGraphics g, float x, float y, float w, float h,
                             float radius, float hover, boolean disabled) {
        int face = disabled ? ThemeManager.color(ThemeToken.MATERIAL_PANEL) : AuroraAnim.lerpArgb(
                ThemeManager.color(ThemeToken.MATERIAL_RAISED), ThemeManager.color(ThemeToken.MATERIAL_HOVER), hover);
        if (disabled || hover == 0 || hover == 1)
            cached(g,x,y,w,h,radius,face,0,FIELD_KIND+(disabled ? 2 : hover==1 ? 1 : 0),false);
        else RenderUtil.drawRoundedRectAA(g,x,y,w,h,radius,face);
    }

    /** Cache kind family reserved for {@link #field}. */
    static final int FIELD_KIND = 112;

    // Primitive packed geometry key: no record/boxing/lambda allocation per control per frame.
    private static final Long2ObjectOpenHashMap<UiLayerCache> TEMPLATES = new Long2ObjectOpenHashMap<>();
    private static net.minecraft.client.gui.screens.Screen templateOwner;
    private static int templateWidth, templateHeight, templateScale;

    /** Release stale screen/resize geometry before this frame records any GUI draws.
     * Also covers Aurora-styled vanilla screens, which have no custom removed hook. */
    public static void beginFrame() {
        Minecraft mc=Minecraft.getInstance();
        if(mc==null) return;
        var window=mc.getWindow();
        int width=window.getGuiScaledWidth(),height=window.getGuiScaledHeight();
        int scale=Math.max(1,(int)window.getGuiScale());
        if(templateOwner!=mc.screen || templateWidth!=width || templateHeight!=height || templateScale!=scale) {
            disposeTemplates();
            templateOwner=mc.screen;
            templateWidth=width;templateHeight=height;templateScale=scale;
        }
    }

    /** Called at each production screen's removal boundary, between frames. */
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
        // No accent halos (Spxctra simplification, 2026-10-30): selection is the wash and
        // one accent edge; a primary action is its strong fill. Glow added no information.
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

                if(kind==2 || kind==102) {
                    RenderUtil.drawRoundedRectAA(g,pad,pad,w,h,radius,selectedFill());
                }
                if(border!=0) RenderUtil.drawRoundedOutlineAA(g,pad,pad,w,h,radius,1,border);
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

    /**
     * Opaque panel that HOSTS controls (map toolbar/status bar, the waypoint editor dialog):
     * the window's panel tone, so raised controls sit one step above it exactly as they do
     * on every translucent window. {@link #floating} stays the raised tone for tooltips and
     * popups, which host no controls of their own.
     */
    public static void floatingPanel(GuiGraphics g, int x, int y, int w, int h, int radius) {
        if (!unified()) { floating(g, x, y, w, h, radius, 1); return; }
        cached(g,x,y,w,h,radius,ThemeManager.color(ThemeToken.MATERIAL_PANEL),
                color(ThemeToken.BORDER_HOVER),5,true,1);
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
