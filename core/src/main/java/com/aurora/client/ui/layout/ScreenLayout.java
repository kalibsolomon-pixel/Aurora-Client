package com.aurora.client.ui.layout;

import com.aurora.client.ui.util.ClipBand;
import com.aurora.client.ui.util.OverlayLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class ScreenLayout {
    public static final class Box {
        private Object key;
        private final String id;
        private int order = -1;
        private float x, top, width, height;

        private Box(String id) { this.id = id; }

        public String id() { return id; }
        public Object key() { return key; }
        public float x() { return x; }
        public float top() { return top; }
        public float y(double scroll) { return (float) (top - scroll); }
        public float width() { return width; }
        public float height() { return height; }
        public int order() { return order; }

        public boolean contains(double px, double py, double scroll) {
            float y = y(scroll);
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private final Map<Object, Box> boxes = new HashMap<>();
    private final Map<String, Box> ids = new HashMap<>();
    private final ArrayList<Box> arranged = new ArrayList<>();
    private final ArrayList<Box> focusOrder = new ArrayList<>();
    private ClipBand viewport;
    private int extent, scrollHeight;
    private long nextId;

    public void begin(ClipBand viewport) {
        for (int index = 0; index < arranged.size(); index++) arranged.get(index).order = -1;
        arranged.clear();
        focusOrder.clear();
        this.viewport = viewport;
        extent = 0;
        scrollHeight = 0;
    }

    public Box place(Object key, String id, float x, float top, float width, float height, boolean focusable) {
        Box box = boxes.get(key);
        if (box == null) {
            box = id == null ? null : ids.get(id);
            if (box == null) {
                box = new Box(id == null ? "row/" + nextId++ : id);
                if (ids.putIfAbsent(box.id, box) != null) throw new IllegalArgumentException("Duplicate layout ID: " + box.id);
            } else {
                if (box.order >= 0) throw new IllegalArgumentException("Duplicate layout ID: " + id);
                boxes.remove(box.key);
            }
            box.key = key;
            boxes.put(key, box);
        }
        if (box.order >= 0) throw new IllegalArgumentException("Duplicate layout key: " + key);
        box.x = x;
        box.top = top;
        box.width = width;
        box.height = height;
        box.order = arranged.size();
        arranged.add(box);
        if (focusable) focusOrder.add(box);
        return box;
    }

    public Box cell(Object key, String id, int slot, int columns, float left, float top,
                    float width, float height, int columnGap, int rowGap) {
        return place(key, id, left + slot % columns * (width + columnGap),
                top + slot / columns * (height + rowGap), width, height, true);
    }

    public int stack(Object key, String id, float left, int top, int width, int height, int gap, boolean focusable) {
        place(key, id, left, top, width, height, focusable);
        return top + height + gap;
    }

    public void finish(ClipBand viewport, int extent) {
        finish(viewport, extent, viewport.height);
    }

    public void finish(ClipBand viewport, int extent, int scrollHeight) {
        this.viewport = viewport;
        this.extent = Math.max(0, extent);
        this.scrollHeight = scrollHeight;
    }

    public void forgetInactive() {
        boxes.values().removeIf(box -> {
            if (box.order >= 0) return false;
            ids.remove(box.id);
            return true;
        });
    }

    public ClipBand viewport() { return viewport; }
    public int extent() { return extent; }
    public double maxScroll() { return Math.max(0, extent - scrollHeight); }
    public int size() { return arranged.size(); }
    public Box at(int index) { return arranged.get(index); }
    public int focusCount() { return focusOrder.size(); }
    public Box focusAt(int index) { return focusOrder.get(index); }

    public Box bounds(Object key) { return active(boxes.get(key)); }
    public Box byId(String id) { return active(ids.get(id)); }

    private static Box active(Box box) { return box != null && box.order >= 0 ? box : null; }

    public boolean visible(Box box, double scroll) {
        return box.order >= 0 && viewport.intersects(box.x, box.y(scroll), box.width, box.height);
    }

    public boolean contains(Box box, double x, double y, double scroll) {
        return box.order >= 0 && viewport.contains(x, y) && box.contains(x, y, scroll);
    }

    public Box hitTest(double x, double y, double scroll) {
        if (!viewport.contains(x, y)) return null;
        for (int index = arranged.size() - 1; index >= 0; index--) {
            Box box = arranged.get(index);
            if (box.contains(x, y, scroll)) return box;
        }
        return null;
    }

    public double reveal(Box box, double target, int margin) {
        return reveal(box.top, box.top + box.height, target, margin);
    }

    public double reveal(double top, double bottom, double target, int margin) {
        return OverlayLayout.scrollToReveal(target, maxScroll(), top, bottom, viewport.y, viewport.yEnd(), margin);
    }
}
