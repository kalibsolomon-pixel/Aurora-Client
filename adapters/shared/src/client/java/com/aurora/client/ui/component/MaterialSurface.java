package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraShapes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import com.aurora.client.theme.MaterialLighting.Profile;
import com.aurora.client.theme.UnifiedPalette;
import com.aurora.client.theme.WindowBevel;

/** Shared cached material for parent faces, embedded controls and opaque floating layers.
 * The parent alone captures the backdrop before dim; its neutral face/boundary
 * is painted after dim. Selection is a soft wash and one accent edge, primary is a readable
 * strong action. No child allocates another blur output. Control lighting is baked into opaque templates.
 */
public final class MaterialSurface {

    private MaterialSurface() {}

    /** Local neutral tint over a parent material; not a second opacity control. */
    static final int EMBEDDED_ALPHA = 0x24;

    /** Production screen context; includes the explicitly Aurora-styled vanilla selection screens. */
    public static boolean unified() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && (com.aurora.client.adapter.NativeUi.screen(mc) instanceof ThemedScreen screen && screen.unifiedTheme()
                || com.aurora.client.config.AuroraConfig.get().customTitleScreen &&
                   (com.aurora.client.adapter.NativeUi.screen(mc) instanceof net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
                    || com.aurora.client.adapter.NativeUi.screen(mc) instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen));
    }

    /** Compatibility mapping for material-aware painters. */
    public static int color(ThemeToken token) {
        if (!unified()) return ThemeManager.color(token);
        ThemeToken role = switch (token) {
            case BACKGROUND -> ThemeToken.MATERIAL_CANVAS;
            case SURFACE, WINDOW_FILL -> ThemeToken.MATERIAL_PANEL;
            case SURFACE_VARIANT -> ThemeToken.MATERIAL_RAISED;
            case SURFACE_INSET -> ThemeToken.MATERIAL_SUNKEN;
            case BORDER -> ThemeToken.MATERIAL_BORDER;
            // A window's edge is one step stronger than an inner border (2026-11-14), so its
            // silhouette survives a night scene at the factory 33% Background Opacity.
            case WINDOW_OUTLINE -> ThemeToken.MATERIAL_BORDER_STRONG;
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

    /** Band cap for a window whose content keeps the shared frame room ({@code UiDensity.FRAME_EDGE}). */
    public static float frameInset(float radius) {
        return WindowBevel.frameInset(radius);
    }

    public static float windowCornerClearance(float radius, float horizontal, float vertical) {
        return WindowBevel.cornerClearance(radius, horizontal, vertical);
    }

    /** Parent finish, after the dim and before content, including when glass capture declined. */
    public static void parent(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float contentInset) {
        windowFinish(g, x, y, w, h, radius, contentInset, 1, true);
    }

    private static final int WINDOW_PIXEL_BUDGET = 1024 * 1024;

    public static void embeddedFrame(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius,
                                     float contentInset) {
        windowFinish(g, x, y, w, h, radius, contentInset, 1, false);
    }

    private static void windowFinish(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius,
                                     float contentInset, float visibility, boolean tintBody) {
        if (w <= 0 || h <= 0 || visibility <= 0) return;
        var theme = ThemeManager.current();
        int windowFill = theme.color(ThemeToken.WINDOW_FILL);
        int fill = tintBody ? windowFill : 0;
        int guiScale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        int outline = theme.color(ThemeToken.MATERIAL_BORDER_STRONG);
        int pad = WindowBevel.SHADOW_PAD;
        int logicalW = Math.round(w) + 2 * pad, logicalH = Math.round(h) + 2 * pad;
        double scale = Math.min(guiScale, Math.sqrt((double) WINDOW_PIXEL_BUDGET / ((double) logicalW * logicalH)));
        int width = Math.max(1, (int) (logicalW * scale)), height = Math.max(1, (int) (logicalH * scale));
        double pixelSize = Math.max((double) logicalW / width, (double) logicalH / height);
        float band = (float) WindowBevel.rasterBandWidth(Math.min(w, h), radius, contentInset, pixelSize);
        // The next glass pass bends the backdrop across exactly this band (windows with their own capture).
        if (tintBody) WindowBevel.rememberBand(x, y, w, h, band);
        long version = ThemeManager.generation() * 65537L + guiScale;
        int recipe = Float.floatToIntBits(band) | Integer.MIN_VALUE;
        var cache = TEMPLATES.get(version, width, height, w, h, radius, recipe, true, fill, outline);
        if (!cache.isCurrent(version)) {
            cache.ensureSize(width, height);
            var finish = WindowBevel.finish(windowFill, outline, theme.blurRadius());
            var raster = new WindowBevel.Raster(w, h, radius, band, pixelSize, finish);
            cache.windowFace(raster, fill, pad, logicalW, logicalH);
            cache.commit(version);
        }
        cache.blitAt(g, Math.round(x) - pad, Math.round(y) - pad, logicalW, logicalH,
                (Math.round(visibility * 255) << 24) | 0xffffff);
    }

    /** Fixed outline attenuation; independent of Background Opacity. */
    public static final float WINDOW_OUTLINE_ALPHA = .55f;
    public static int windowOutline() {
        int token = color(ThemeToken.WINDOW_OUTLINE);
        return ThemeManager.withAlpha(token, Math.round((token >>> 24) * WINDOW_OUTLINE_ALPHA));
    }

    /** Depth tuning, owner ruling 2026-11-17: HSL points, cached curvature and directional shadows. */
    private static final float PEER_HOVER_ALPHA = .16f, SWITCH_HOVER_ALPHA = .08f;

    private enum Light {
        FLAT(0,0,Profile.FLAT), RAISED(5,-3,Profile.RAISED),
        HOVER(5,-3,Profile.RAISED), PRESSED(-3,2,Profile.PRESSED),
        INSET(1,5,Profile.INSET), TRACK(3,7,Profile.INSET),
        PRIMARY(6,-4,Profile.RAISED), PRIMARY_LIGHT(3,-4,Profile.RAISED),
        MODULE_ON(11,3,Profile.RAISED), MODULE_OFF(-5,-5,Profile.FLAT),
        MODULE_ON_HOVER(13,5,Profile.RAISED), MODULE_OFF_HOVER(-3,-3,Profile.FLAT),
        KNOB(0,0,Profile.DOME,1,.23f), OVERLAY(5,-3,Profile.RAISED,2,.28f),
        MODULE_SHADOW(0,0,Profile.FLAT,2,.26f), CONTROL_SHADOW(0,0,Profile.FLAT,1,.18f);
        final float top, bottom, elevation, shadowAlpha;
        final Profile profile;
        Light(float top,float bottom,Profile profile) { this(top,bottom,profile,0,0); }
        Light(float top,float bottom,Profile profile,float elevation,float shadowAlpha) {
            this.top=top;this.bottom=bottom;this.profile=profile;
            this.elevation=elevation;this.shadowAlpha=shadowAlpha;
        }
    }

    /** Stable endpoint templates; transitions blend textures rather than issuing live face fills. */
    public static void control(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,
                               boolean selected,boolean disabled,boolean primary,float hover,float press) {
        int base=ThemeManager.color(disabled ? ThemeToken.MATERIAL_PANEL : primary ? ThemeToken.ACTION_STRONG : ThemeToken.MATERIAL_RAISED);
        int over=ThemeManager.color(primary ? ThemeToken.ACTION_HOVER : ThemeToken.MATERIAL_HOVER);
        int down=ThemeManager.color(primary ? ThemeToken.ACTION_PRESSED : ThemeToken.MATERIAL_PRESSED);
        controlColors(g,x,y,w,h,radius,base,over,down,selected,disabled,primary,hover,press,200);
    }

    /** Colour endpoints also support the existing destructive button behaviour. */
    public static void controlColors(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,
                                     int base,int over,int down,boolean selected,boolean disabled,boolean primary,
                                     float hover,float press,int kind) {
        if(!disabled && press<1) cached(g,x,y,w,h,radius,0,0,80,false,1-press,Light.CONTROL_SHADOW);
        // contrast against black = (luminance + .05)/.05; >11 means ON_ACTION luminance >.5.
        Light actionLight=UnifiedPalette.contrast(ThemeManager.color(ThemeToken.ON_ACTION),0xff000000)>11
                ? Light.PRIMARY_LIGHT : Light.PRIMARY;
        int edge=selected && !disabled ? ThemeManager.color(ThemeToken.ACCENT_MARKER) : 0;
        int state=kind+(selected ? 8 : 0);
        // A settled state needs one face blit; stacking both endpoints would double AA coverage.
        // Disabled keeps its raised form in the panel tone, without the shadow (2026-11-17): a flat
        // panel-tone face vanished into the window, so an unavailable "+" read as a bare glyph.
        if(disabled || hover<1 && press<1)
            depth(g,x,y,w,h,radius,base,edge,state,primary && !disabled ? actionLight : Light.RAISED,1);
        if(disabled) return;
        if(hover>0 && press<1) depth(g,x,y,w,h,radius,over,edge,state+1,primary ? actionLight : Light.HOVER,Math.min(1,hover));
        if(press>0) depth(g,x,y,w,h,radius,down,edge,state+2,Light.PRESSED,Math.min(1,press));
    }

    public static void inset(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,boolean disabled) {
        depth(g,x,y,w,h,radius,ThemeManager.color(disabled ? ThemeToken.MATERIAL_PANEL : ThemeToken.MATERIAL_SUNKEN),
                0,240,disabled ? Light.FLAT : Light.INSET,1);
    }

    /** Slider wells use the brighter +3 -> +7 recipe; accent fill remains the caller's. */
    public static void sliderTrack(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,boolean disabled) {
        depth(g,x,y,w,h,radius,ThemeManager.color(disabled ? ThemeToken.MATERIAL_PANEL : ThemeToken.MATERIAL_SUNKEN),
                0,241,disabled ? Light.FLAT : Light.TRACK,1);
    }

    /** OFF inset and the existing flat accent ON fill, with cached hover wash endpoints. */
    public static void switchTrack(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,
                                   float on,float hover,boolean disabled) {
        if(disabled || on<1) inset(g,x,y,w,h,radius,disabled);
        if(disabled) return;
        // ON is the same recessed well filled with the accent (2026-11-17): a flat accent over
        // a dished OFF track read as two different materials.
        if(on>0) depth(g,x,y,w,h,radius,color(ThemeToken.ACCENT),0,280,Light.INSET,on);
        if(hover>0) depth(g,x,y,w,h,radius,color(ThemeToken.ON_BACKGROUND),0,281,Light.FLAT,SWITCH_HOVER_ALPHA*hover);
    }

    /** Same-size dome: circular SDF at capsule radius, machined square at radius zero. */
    public static void knob(GuiGraphicsExtractor g,float x,float y,float size,float radius,int fill,boolean disabled) {
        depth(g,x,y,size,size,radius,fill,0,250,disabled ? Light.FLAT : Light.KNOB,1);
    }

    public static void module(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,boolean on,float hover) {
        if(on) cached(g,x,y,w,h,radius,0,0,262,false,1,Light.MODULE_SHADOW);
        int face=ThemeManager.withAlpha(ThemeManager.color(ThemeToken.MATERIAL_RAISED),255);
        if(hover<1) depth(g,x,y,w,h,radius,face,0,260,on ? Light.MODULE_ON : Light.MODULE_OFF,1);
        if(hover>0) depth(g,x,y,w,h,radius,face,0,261,on ? Light.MODULE_ON_HOVER : Light.MODULE_OFF_HOVER,Math.min(1,hover));
    }

    /** Text-only peer hover, independent of the sidebar's unchanged treatment. */
    public static void peerHover(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,float hover) {
        if(hover>0) depth(g,x,y,w,h,radius,ThemeManager.color(ThemeToken.MATERIAL_HOVER),0,270,Light.FLAT,PEER_HOVER_ALPHA*Math.min(1,hover));
    }

    private static void depth(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,
                              int fill,int edge,int kind,Light light,float opacity) {
        cached(g,x,y,w,h,radius,ThemeManager.withAlpha(fill,255),edge,kind,false,opacity,light);
    }

    public static int selectedFill() { return ThemeManager.color(ThemeToken.SELECTION_SOFT); }

    /** Same role geometry and backing for success/decline. Child roles never sample. */
    public static void selection(GuiGraphicsExtractor g, float x, float y, float w, float h,
                                 float radius, boolean selected, float hover) {
        selection(g,x,y,w,h,radius,selected,hover,false);
    }

    /**
     * {@code flatRest}: an unselected peer has no resting face and only gains the neutral
     * face while hovered (sidebar items inside an already-material window, §11.11).
     */
    public static void selection(GuiGraphicsExtractor g, float x, float y, float w, float h,
                                 float radius, boolean selected, float hover, boolean flatRest) {
        if(!flatRest) {
            control(g,x,y,w,h,radius,selected,false,false,hover,0);
            return;
        }
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
            RenderUtil.drawRoundedOutlineAA(g,x,y,w,h,radius,selectedEdge(),ThemeManager.color(ThemeToken.ACCENT_MARKER));
        }
    }

    /**
     * Stroke of the selected peer's accent edge, in GUI px: about half the
     * 1 GUI px border weight, never under one screen pixel (owner ruling
     * 2026-11-12: selected outlines read thinner). 2 screen px at GUI
     * scale 3 and 4, 1 at scale 2.
     */
    static float selectedEdge() {
        int scale=Math.max(1,(int)Minecraft.getInstance().getWindow().getGuiScale());
        return Math.max(1,Math.round(scale*.5f))/(float)scale;
    }

    /**
     * The one face for trailing field controls in setting rows — dropdown, keybind and row
     * action button share it so a settings column never mixes three treatments. Opaque
     * raised tone (legible at any window opacity), no outline; hover lifts the tone.
     */
    public static void field(GuiGraphicsExtractor g, float x, float y, float w, float h,
                             float radius, float hover, boolean disabled) {
        control(g,x,y,w,h,radius,false,disabled,false,hover,0);
    }

    private static final MaterialTemplates TEMPLATES = new MaterialTemplates();
    private static int templateScale, templateRadius;
    private static long templateGeneration=Long.MIN_VALUE;

    /** Keep geometry across screens/resizes; the LRU bounds it. Theme, scale and
     * roundness changes invalidate every template at an extraction boundary, and
     * returning to gameplay releases them: no HUD draws these faces, so nothing is
     * held while playing (2026-11-17). */
    public static void beginFrame() {
        Minecraft mc=Minecraft.getInstance();
        if(mc==null) return;
        if(com.aurora.client.adapter.NativeUi.screen(mc)==null) {
            if(TEMPLATES.size()>0) disposeTemplates();
            return;
        }
        var window=mc.getWindow();
        int scale=Math.max(1,(int)window.getGuiScale());
        int radius=ThemeManager.current().roundness().radius();
        long generation=ThemeManager.generation();
        if(templateScale!=scale || templateRadius!=radius || templateGeneration!=generation) {
            disposeTemplates();
            templateScale=scale;templateRadius=radius;templateGeneration=generation;
        }
    }

    /** Explicit invalidation only. Screen removal retains reusable templates.
     * Disposal is queued, including if invalidation is requested during extraction. */
    public static void disposeTemplates() {
        TEMPLATES.clear();
    }

    /** Rasterize elevation/fill/edge once per size and role, then one blit.
     * Changed identities get new rasters; no texture is mutated/destroyed mid-frame. */
    public static void cached(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,
                              int fill,int border,int kind,boolean shadow) {
        cached(g,x,y,w,h,radius,fill,border,kind,shadow,1);
    }

    private static void cached(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,
                               int fill,int border,int kind,boolean shadow,float opacity) {
        cached(g,x,y,w,h,radius,fill,border,kind,shadow,opacity,Light.FLAT);
    }

    private static void cached(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,
                               int fill,int border,int kind,boolean shadow,float opacity,Light light) {
        var mc=Minecraft.getInstance();
        int scale=Math.max(1,(int)mc.getWindow().getGuiScale());
        // No accent halos (Spxctra simplification, 2026-10-30): selection is the wash and
        // one accent edge; a primary action is its strong fill. Glow added no information.
        int pad=shadow ? 10 : light.elevation>0 ? (light==Light.MODULE_SHADOW ? 5 : 3) : 2;
        int width=Math.round(w), height=Math.round(h), round=Math.round(radius);
        kind += light.ordinal()*1024;
        long version=((ThemeManager.generation()*65537L+scale)*31+fill)*31+border;
        var cache=TEMPLATES.get(version,(width+pad*2)*scale,(height+pad*2)*scale,w,h,radius,kind,shadow,fill,border);
        if(!cache.isCurrent(version)) {
            cache.ensureSize((width+pad*2)*scale,(height+pad*2)*scale); cache.clear();
            var previous=RenderUtil.beginCapture(cache.sink());
            try {
                if(shadow) AuroraShapes.dropShadow(g,pad,pad+3,width,height,round,.25f);
                if(fill!=0) {
                    RenderUtil.drawRoundedRectAA(g,pad,pad,w,h,radius,fill);
                    // Preserve pre-pass owner-themed cards and tooltips; window bodies stay flat.
                    if(shadow && kind!=120 && light==Light.FLAT)
                        cache.shadeVertical(pad*scale,pad*scale,width*scale,height*scale,3);
                    if (light != Light.FLAT)
                        cache.lightFace(pad*scale,pad*scale,w*scale,h*scale,radius*scale,scale,
                                light.top,light.bottom,light.profile);
                }

                if(kind==2 || kind==102 || border!=0 && light!=Light.FLAT) {
                    RenderUtil.drawRoundedRectAA(g,pad,pad,w,h,radius,selectedFill());
                }
                // Selected peers (kind 2) take the thinner selection edge; other borders stay 1 GUI px.
                if(border!=0) RenderUtil.drawRoundedOutlineAA(g,pad,pad,w,h,radius,
                        kind==2 || kind==102 || light!=Light.FLAT ? selectedEdge() : 1,border);
                if(light.elevation>0)
                    cache.softShadow(pad*scale,pad*scale,w*scale,h*scale,radius*scale,
                            light.elevation*scale,2*light.elevation*scale,light.shadowAlpha);
            } finally { RenderUtil.endCapture(previous); }
            cache.commit(version);
        }
        cache.blitAt(g,Math.round(x)-pad,Math.round(y)-pad,width+pad*2,height+pad*2,
                (Math.round(opacity*255)<<24)|0xffffff);
    }

    public static void embeddedControl(GuiGraphicsExtractor g, float x, float y, float w, float h,
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
    public static void floatingPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius) {
        if (!unified()) { floating(g, x, y, w, h, radius, 1); return; }
        cached(g,x,y,w,h,radius,ThemeManager.color(ThemeToken.MATERIAL_PANEL),
                color(ThemeToken.BORDER_HOVER),5,true,1);
    }

    public static void overlayFace(GuiGraphicsExtractor g, int x, int y, int w, int h, float radius,
                                   float contentInset, float visibility) {
        windowFinish(g, x, y, w, h, radius, contentInset, visibility, true);
    }

    public static void floatingTooltip(GuiGraphicsExtractor g, int x, int y, int w, int h, int legacyRadius,
                                       float textInsetX, float textInsetY) {
        float radius = Math.min(ThemeManager.current().roundness().radiusSmall(), Math.min(w, h) * .5f);
        windowFinish(g, x, y, w, h, radius, windowCornerClearance(radius, textInsetX, textInsetY), 1, true);
    }

    public static void floating(GuiGraphicsExtractor g, int x, int y, int w, int h,
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
