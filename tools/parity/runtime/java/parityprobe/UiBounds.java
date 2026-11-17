package parityprobe;

import com.aurora.client.screen.LayoutScreen;
import com.aurora.client.ui.layout.ScreenLayout;
import com.aurora.client.screen.FeatureDetailScreen;
import com.aurora.client.ui.util.AuroraFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector2f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One extraction frame: arranged bounds from the host, native ink from probe mixins. */
public final class UiBounds {
    private static Path output;
    private static Screen expected;
    private static boolean collecting;
    private static final List<Face> faces = new ArrayList<>();
    private static final List<Draw> draws = new ArrayList<>();
    public record Rect(float x, float y, float w, float h) {
        float area() { return w * h; }
        boolean contains(float px, float py) { return px >= x && px <= x+w && py >= y && py <= y+h; }
    }
    private record Face(String kind, Rect rect) {}
    private record Draw(String kind, String text, float x, Float baselineY, float y, float width,
                        float height, String source, Rect clip) {}
    private UiBounds() {}

    public static void arm(Path destination, Screen screen) {
        if (!(screen instanceof LayoutScreen))
            throw new IllegalStateException("Current main/detail/manager screen required");
        if (output != null || Files.exists(destination)) throw new IllegalStateException("Result pending/already exists");
        output = destination; expected = screen;
    }

    public static boolean active() { return collecting; }
    public static void begin(Object screen) {
        if (output == null) return;
        if (screen != expected) { cancel(); return; }
        faces.clear(); draws.clear(); collecting = true;
    }
    public static void cancel() { output = null; expected = null; collecting = false; faces.clear(); draws.clear(); }

    /** Coordinates include the current screen pose (open/press/rotation transforms). */
    public static Rect transform(GuiGraphicsExtractor g, float x, float y, float w, float h) {
        var p = new Vector2f();
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        for (float[] corner : new float[][]{{x,y},{x+w,y},{x,y+h},{x+w,y+h}}) {
            g.pose().transformPosition(corner[0],corner[1],p);
            minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);
        }
        return new Rect(minX,minY,maxX-minX,maxY-minY);
    }

    // The probe walks the call stack only during its single armed frame.
    private static List<StackWalker.StackFrame> callers() {
        return StackWalker.getInstance().walk(stream -> stream.filter(f -> f.getClassName().startsWith("com.aurora.client."))
                .limit(24).toList());
    }
    private static boolean has(List<StackWalker.StackFrame> stack, String name) {
        return stack.stream().anyMatch(f -> f.getClassName().endsWith(name) || f.getMethodName().equals(name));
    }
    private static String kind(List<StackWalker.StackFrame> stack) {
        if (has(stack,"drawPendingTooltip") || has(stack,"drawValueLine") || has(stack,"drawValueLineCentered")
                || has(stack,"drawSubtitle") || has(stack,"SectionFooterSetting")) return "description";
        if (has(stack,"renderCompactLabel") || has(stack,"renderResetLabel") || has(stack,"renderWrappedLabel")
                || has(stack,"renderLabelWithTooltip")) return "row-label";
        if (has(stack,"EnumSetting")) return "dropdown";
        if (has(stack,"KeybindSetting")) return "keybind";
        if (has(stack,"ToggleGroupSetting")) return "chip";
        if (has(stack,"SegmentedControl")) return "segment";
        if (has(stack,"SliderSetting") || has(stack,"Slider")) return "slider";
        if (has(stack,"Button") || has(stack,"ButtonWidget") || has(stack,"IconAction")) return "button";
        if (has(stack,"BooleanSetting") || has(stack,"ToggleSwitch")) return "chip";
        if (has(stack,"drawModules")) return "tile";
        return "row-label";
    }

    public static void face(GuiGraphicsExtractor g,float x,float y,float w,float h) {
        if (!collecting || w<=0 || h<=0) return;
        var stack=callers();
        // Exclude the window/parent and knob, whose sub-faces are the useful measurements.
        if (has(stack,"parent") || has(stack,"knob")) return;
        Rect r=transform(g,x,y,w,h);
        if (!visible(g,r)) return;
        Face face=new Face(kind(stack),r);
        if (!faces.contains(face)) faces.add(face);
    }

    private static boolean visible(GuiGraphicsExtractor g,Rect r) {
        // Test any painted pixel, not just the centre: partly scrolled rows remain visible.
        for(int y=(int)Math.floor(r.y);y<Math.ceil(r.y+r.h);y++)
            for(int x=(int)Math.floor(r.x);x<Math.ceil(r.x+r.w);x++)
                if(x>=0 && y>=0 && x<expected.width && y<expected.height && g.containsPointInScissor(x,y)) return true;
        return false;
    }
    private static Rect clip(GuiGraphicsExtractor g,Rect r) {
        int left=Integer.MAX_VALUE,top=left,right=Integer.MIN_VALUE,bottom=right;
        for(int y=Math.max(0,(int)Math.floor(r.y));y<Math.min(expected.height,Math.ceil(r.y+r.h));y++)
            for(int x=Math.max(0,(int)Math.floor(r.x));x<Math.min(expected.width,Math.ceil(r.x+r.w));x++)
                if(g.containsPointInScissor(x,y)) { left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1); }
        return right>left ? new Rect(left,top,right-left,bottom-top) : new Rect(0,0,0,0);
    }

    public static void text(GuiGraphicsExtractor g,Font font,FormattedCharSequence original,int x,int y,int color) {
        if (!collecting || (color>>>24)==0) return;
        var style=AuroraFontRenderer.getActiveStyle();
        var sequence=style==null ? original : AuroraFontRenderer.withCustomFont(original,style);
        String value=AuroraFontRenderer.getSequenceString(sequence);
        if(value.isEmpty()) return;
        var stack=callers();
        String kind=kind(stack);
        final boolean[] title={false};
        sequence.accept((i,s,cp) -> { if(s.getFont().toString().contains("_title")) title[0]=true; return true; });
        if(title[0]) kind="title";
        // The detail header descriptions are drawn directly by the screen.
        if(expected instanceof FeatureDetailScreen && stack.stream().noneMatch(f -> f.getClassName().contains(".setting.")
                || f.getClassName().contains(".component.")) && !title[0]) kind="description";
        Rect r=transform(g,x,y,font.width(sequence),font.lineHeight);
        if(!visible(g,r)) return;
        var origin=g.pose().transformPosition(x,y,new Vector2f());
        String source=stack.isEmpty() ? "native" : stack.getFirst().getClassName()+"#"+stack.getFirst().getMethodName();
        draws.add(new Draw(kind,value,origin.x,origin.y,origin.y,r.w,r.h,source,clip(g,r)));
    }

    /** The actual native icon blit, whose canvas is padded rather than stretched. */
    public static void icon(GuiGraphicsExtractor g,String glyph,int x,int y,int w,int h) {
        if(!collecting) return;
        Rect r=transform(g,x,y,w,h);
        if(!visible(g,r)) return;
        var stack=callers();
        draws.add(new Draw(kind(stack),glyph,r.x,null,r.y,r.w,r.h,"MaterialIconRenderer",clip(g,r)));
    }

    public static void end(Object screen) {
        if(!collecting || screen!=expected) return;
        collecting=false;
        try {
            var controls=new ArrayList<Map<String,Object>>();
            for(Face face:faces) {
                var entry=new LinkedHashMap<String,Object>();
                entry.put("kind",face.kind);entry.put("face",face.rect);entry.put("draws",new ArrayList<Draw>());
                controls.add(entry);
            }
            for(Draw draw:draws) {
                Map<String,Object> owner=null;float area=Float.POSITIVE_INFINITY;
                for(var entry:controls) {
                    Rect r=(Rect)entry.get("face");
                    if(r.contains(draw.x+draw.width/2,draw.y+draw.height/2) && r.area()<area) { owner=entry;area=r.area(); }
                }
                if(owner==null || draw.kind.equals("row-label") || draw.kind.equals("description") || draw.kind.equals("title")) {
                    owner=new LinkedHashMap<>();owner.put("kind",draw.kind);
                    owner.put("face",new Rect(draw.x,draw.y,draw.width,draw.height));
                    owner.put("draws",new ArrayList<Draw>());controls.add(owner);
                }
                ((List<Draw>)owner.get("draws")).add(draw);
            }
            for(var control:controls) control.put("text",((List<Draw>)control.get("draws")).stream()
                    .filter(d -> d.baselineY!=null).map(Draw::text).reduce((a,b)->a+"\n"+b).orElse(""));
            var report=new LinkedHashMap<String,Object>();
            report.put("schemaVersion",1);report.put("screen",screen.getClass().getName());
            report.put("guiWidth",expected.width);report.put("guiHeight",expected.height);
            report.put("guiScale",Minecraft.getInstance().getWindow().getGuiScale());
            LayoutScreen host = (LayoutScreen) screen;
            ScreenLayout layout = host.contentLayout();
            double scroll = host.layoutScroll();
            var layoutBoxes = new ArrayList<Map<String,Object>>();
            for (int index = 0; index < layout.size(); index++) {
                var box = layout.at(index);
                var entry = new LinkedHashMap<String,Object>();
                entry.put("layoutId", box.id());
                entry.put("face", new Rect(box.x(), box.y(scroll), box.width(), box.height()));
                entry.put("visible", layout.visible(box, scroll));
                layoutBoxes.add(entry);
            }
            for (var control : controls) {
                Rect face = (Rect) control.get("face");
                var box = layout.hitTest(face.x + face.w / 2, face.y + face.h / 2, scroll);
                if (box != null) control.put("layoutId", box.id());
            }
            report.put("controls",controls);
            report.put("layout",layoutBoxes);
            UiCacheBenchmark.write(output,report);
            System.out.println("[parity-probe] ui-bounds complete: "+output);
        } catch(Exception failure) { throw new RuntimeException("ui-bounds failed",failure); }
        finally { cancel(); }
    }
}
