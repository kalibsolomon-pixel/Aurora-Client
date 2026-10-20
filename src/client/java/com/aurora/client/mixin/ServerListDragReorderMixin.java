package com.aurora.client.mixin;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraShapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Click-and-drag reordering for the multiplayer server list.
 *
 * <p>Motion follows the premium-design rules (refined 2026-10-19):
 * <ul>
 *   <li><b>Direct manipulation</b> — the lifted card tracks the pointer 1:1,
 *       vertically only, keeping the exact point where it was grabbed (no jump
 *       to a cursor offset, no trailing lag).</li>
 *   <li><b>Lift</b> — 150 ms ease-out: the card scales to 1.02 and gains a
 *       soft shadow and the accent selection edge; its slot underneath turns
 *       into a quiet empty well.</li>
 *   <li><b>Target</b> — a single accent insertion line glides (interruptible
 *       exponential approach) to the gap the card will land in; it hides
 *       when the card would stay put.</li>
 *   <li><b>Settle</b> — on release the reorder commits immediately and the
 *       card glides into its new slot (200 ms ease-out) while scale and
 *       shadow relax. Esc cancels a drag and settles the card back home.</li>
 *   <li>Near the list's top/bottom edge the list auto-scrolls, faster the
 *       closer the pointer gets.</li>
 * </ul>
 *
 * <p>Normal clicks (no movement past the threshold) are passed through
 * untouched so selection / double-click-to-join keep working. All motion is
 * driven by {@link System#nanoTime()} (dt clamped), so it is frame-rate
 * independent.
 */
@Mixin(value = JoinMultiplayerScreen.class, priority = 1500)
public abstract class ServerListDragReorderMixin extends Screen {

    @Shadow
    protected ServerSelectionList serverSelectionList;

    @Shadow
    private ServerList servers;

    // ---- Drag state ----
    @Unique private boolean aurora$dragArmed;
    @Unique private int aurora$dragFromIndex = -1;
    @Unique private double aurora$dragPressY;
    @Unique private boolean aurora$dragActive;
    @Unique private int aurora$dragDropIndex = -1;
    /** Pointer offset from the grabbed row's top, kept for the whole drag. */
    @Unique private double aurora$grabDy;

    // ---- Animation state ----
    @Unique private int aurora$animPhase = AURORA_PHASE_NONE;
    @Unique private long aurora$animStartNs;
    @Unique private long aurora$lastFrameNs;
    /** Card top (GUI px); x is always the row column. */
    @Unique private double aurora$ghostY;
    @Unique private double aurora$settleFromY;
    @Unique private int aurora$settleToIndex = -1;
    /** Lift progress 0..1 when the settle started (an early release settles from there). */
    @Unique private double aurora$settleFromLift;
    /** Insertion line y, eased toward its target; NaN = not shown yet. */
    @Unique private double aurora$lineY = Double.NaN;
    @Unique private GhostSnapshot aurora$ghost;

    @Unique private static final int AURORA_PHASE_NONE = 0;
    @Unique private static final int AURORA_PHASE_LIFT = 1;
    @Unique private static final int AURORA_PHASE_MOVE = 2;
    @Unique private static final int AURORA_PHASE_SETTLE = 3;

    @Unique private static final double AURORA_DRAG_THRESHOLD = 4.0;
    @Unique private static final long AURORA_LIFT_MS = 150;
    @Unique private static final long AURORA_SETTLE_MS = 200;
    @Unique private static final double AURORA_LIFT_SCALE = 1.02;
    /** Insertion-line glide time constant (ms): quick, but never a teleport. */
    @Unique private static final double AURORA_LINE_TAU_MS = 35.0;
    /** Auto-scroll band at the list's edges (GUI px) and top speed (GUI px/s). */
    @Unique private static final double AURORA_EDGE_BAND = 24.0, AURORA_EDGE_SPEED = 420.0;
    @Unique private static final int AURORA_GHOST_PAD = 8;
    @Unique private static final float AURORA_LINE_H = 2f;

    protected ServerListDragReorderMixin(Component title) {
        super(title);
    }

    @Unique
    private boolean aurora$enabled() {
        return AuroraConfig.get().serverListDragReorder;
    }

    /** Lightweight immutable snapshot of the dragged server for ghost rendering. */
    @Unique
    private static final class GhostSnapshot {
        final String name;
        final long ping;
        final Component motd;        // may be null

        GhostSnapshot(ServerData d) {
            this.name = (d != null && d.name != null && !d.name.isBlank())
                    ? d.name : "Server";
            this.ping = d != null ? d.ping : 0L;
            this.motd = d != null ? d.motd : null;
        }
    }

    @Unique
    private GhostSnapshot aurora$snapshot(int index) {
        if (servers == null || index < 0 || index >= servers.size()) return null;
        return new GhostSnapshot(servers.get(index));
    }

    // ---- Easing (fast ease-out: immediate response, quick settling) ----
    @Unique
    private static double aurora$easeOut(double t) {
        double x = 1.0 - t;
        return 1.0 - x * x * x;
    }

    @Unique
    private static double aurora$clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    @Unique
    private static double aurora$lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    @Unique
    private static double aurora$msSince(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000.0;
    }

    @Unique
    private void aurora$resetAnim() {
        aurora$animPhase = AURORA_PHASE_NONE;
        aurora$ghost = null;
        aurora$settleToIndex = -1;
        aurora$lineY = Double.NaN;
    }

    @Unique
    private int aurora$rowHeight() {
        return serverSelectionList.getRowBottom(0) - serverSelectionList.getRowTop(0);
    }

    // ---- Hit testing ----
    @Unique
    private int aurora$serverIndexAt(double mouseX, double mouseY) {
        if (serverSelectionList == null || servers == null) return -1;
        if (mouseX < serverSelectionList.getX() || mouseX > serverSelectionList.getRight()) return -1;
        int count = servers.size();
        for (int i = 0; i < count; i++) {
            if (mouseY >= serverSelectionList.getRowTop(i) && mouseY < serverSelectionList.getRowBottom(i)) return i;
        }
        return -1;
    }

    /** Drop slot for the card's centre: the row it overlaps most, clamped to the list. */
    @Unique
    private int aurora$dropIndexFor(double cardTop) {
        int count = servers.size();
        if (count == 0) return -1;
        double centre = cardTop + aurora$rowHeight() / 2.0;
        for (int i = 0; i < count; i++) {
            if (centre < serverSelectionList.getRowBottom(i)) return i;
        }
        return count - 1;
    }

    // ---- Mouse / key handlers ----
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean result = super.mouseClicked(event, doubleClick);
        if (!aurora$enabled() || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return result;
        }
        int idx = aurora$serverIndexAt(event.x(), event.y());
        aurora$dragArmed = idx >= 0;
        aurora$dragFromIndex = idx;
        aurora$dragPressY = event.y();
        aurora$dragActive = false;
        aurora$dragDropIndex = idx;
        return result;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        boolean result = super.mouseDragged(event, deltaX, deltaY);
        if (!aurora$enabled() || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !aurora$dragArmed) {
            return result;
        }
        if (!aurora$dragActive && Math.abs(event.y() - aurora$dragPressY) > AURORA_DRAG_THRESHOLD) {
            aurora$beginLift();
        }
        return result;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasActive = aurora$dragActive;
        int from = aurora$dragFromIndex;
        int drop = aurora$dragDropIndex;
        aurora$dragArmed = false;
        aurora$dragActive = false;
        aurora$dragFromIndex = -1;
        aurora$dragDropIndex = -1;

        if (wasActive && aurora$enabled()
                && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && from >= 0 && servers != null && serverSelectionList != null) {
            aurora$beginSettle(from, drop);
            // Swallow the release so vanilla can't misread the drag as a join.
            return true;
        }
        if (aurora$animPhase != AURORA_PHASE_SETTLE) aurora$resetAnim();
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (aurora$dragActive && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            // Cancel: the card glides back to where it came from, nothing moves.
            int from = aurora$dragFromIndex;
            aurora$dragArmed = false;
            aurora$dragActive = false;
            aurora$dragFromIndex = -1;
            aurora$dragDropIndex = -1;
            aurora$beginSettle(from, from);
            return true;
        }
        return super.keyPressed(event);
    }

    // ---- Phase transitions ----
    @Unique
    private void aurora$beginLift() {
        aurora$dragActive = true;
        aurora$ghost = aurora$snapshot(aurora$dragFromIndex);
        int rowTop = serverSelectionList != null ? serverSelectionList.getRowTop(aurora$dragFromIndex) : (int) aurora$dragPressY;
        // The card starts exactly where the row is and keeps the grab point.
        aurora$grabDy = aurora$dragPressY - rowTop;
        aurora$ghostY = rowTop;
        aurora$lineY = Double.NaN;
        aurora$animPhase = AURORA_PHASE_LIFT;
        aurora$animStartNs = System.nanoTime();
        aurora$lastFrameNs = aurora$animStartNs;
    }

    @Unique
    private void aurora$beginSettle(int from, int to) {
        boolean moved = to >= 0 && to < servers.size() && to != from;
        // Commit the reorder immediately so list state is correct; the card
        // then glides into its new slot.
        if (moved) aurora$moveServer(from, to);
        aurora$settleToIndex = moved ? to : from;
        aurora$settleFromLift = aurora$liftProgress();
        if (aurora$ghost == null) {
            aurora$ghost = aurora$snapshot(aurora$settleToIndex);
            aurora$ghostY = serverSelectionList.getRowTop(aurora$settleToIndex);
        }
        aurora$settleFromY = aurora$ghostY;
        aurora$animPhase = AURORA_PHASE_SETTLE;
        aurora$animStartNs = System.nanoTime();
    }

    @Unique
    private void aurora$moveServer(int from, int to) {
        try {
            if (from < to) {
                for (int i = from; i < to; i++) servers.swap(i, i + 1);
            } else {
                for (int i = from; i > to; i--) servers.swap(i, i - 1);
            }
            servers.save();
            serverSelectionList.updateOnlineServers(servers);
            var children = serverSelectionList.children();
            if (to >= 0 && to < children.size()
                    && children.get(to) instanceof ServerSelectionList.OnlineServerEntry moved) {
                serverSelectionList.setSelected(moved);
            }
        } catch (Throwable t) {
            AuroraClient.LOGGER.error("Aurora: failed to reorder server list", t);
        }
    }

    // ---- Per-frame update (called from render) ----
    @Unique
    private void aurora$updateAnim(double mouseY) {
        long now = System.nanoTime();
        double dtMs = aurora$clamp((now - aurora$lastFrameNs) / 1_000_000.0, 0.0, 100.0);
        aurora$lastFrameNs = now;

        if (aurora$animPhase == AURORA_PHASE_LIFT || aurora$animPhase == AURORA_PHASE_MOVE) {
            if (aurora$animPhase == AURORA_PHASE_LIFT && aurora$msSince(aurora$animStartNs) >= AURORA_LIFT_MS) {
                aurora$animPhase = AURORA_PHASE_MOVE;
            }
            // Auto-scroll while the pointer sits in the list's edge bands.
            double top = serverSelectionList.getY(), bottom = serverSelectionList.getBottom();
            double depth = mouseY < top + AURORA_EDGE_BAND ? -(top + AURORA_EDGE_BAND - mouseY)
                    : mouseY > bottom - AURORA_EDGE_BAND ? mouseY - (bottom - AURORA_EDGE_BAND) : 0;
            if (depth != 0) {
                double speed = AURORA_EDGE_SPEED * aurora$clamp(depth / AURORA_EDGE_BAND, -1, 1);
                serverSelectionList.setScrollAmount(serverSelectionList.scrollAmount() + speed * dtMs / 1000.0);
            }
            // Direct manipulation: 1:1 with the pointer, clamped to the list.
            int h = aurora$rowHeight();
            int first = serverSelectionList.getRowTop(0), last = serverSelectionList.getRowTop(Math.max(0, servers.size() - 1));
            aurora$ghostY = aurora$clamp(mouseY - aurora$grabDy, Math.max(first, top - h / 2.0),
                    Math.min(last, bottom - h / 2.0));
            aurora$dragDropIndex = aurora$dropIndexFor(aurora$ghostY);

            // Insertion line: the gap the card lands in (below the target when
            // moving down, above it when moving up), eased so it glides.
            int from = aurora$dragFromIndex, to = aurora$dragDropIndex;
            if (to >= 0 && to != from) {
                double target = to > from ? serverSelectionList.getRowBottom(to) + 1 : serverSelectionList.getRowTop(to) - 2;
                aurora$lineY = Double.isNaN(aurora$lineY) ? target
                        : aurora$lerp(aurora$lineY, target, 1.0 - Math.exp(-dtMs / AURORA_LINE_TAU_MS));
            } else {
                aurora$lineY = Double.NaN;
            }
        } else if (aurora$animPhase == AURORA_PHASE_SETTLE) {
            double t = aurora$clamp(aurora$msSince(aurora$animStartNs) / AURORA_SETTLE_MS, 0, 1);
            int toY = aurora$settleToIndex >= 0 ? serverSelectionList.getRowTop(aurora$settleToIndex) : (int) aurora$settleFromY;
            aurora$ghostY = aurora$lerp(aurora$settleFromY, toY, aurora$easeOut(t));
            if (t >= 1.0) aurora$resetAnim();
        }
    }

    /** Lift progress 0..1: rises over the lift, holds while moving, relaxes while settling. */
    @Unique
    private double aurora$liftProgress() {
        return switch (aurora$animPhase) {
            case AURORA_PHASE_LIFT -> aurora$easeOut(aurora$clamp(aurora$msSince(aurora$animStartNs) / AURORA_LIFT_MS, 0, 1));
            case AURORA_PHASE_MOVE -> 1.0;
            case AURORA_PHASE_SETTLE -> aurora$settleFromLift
                    * (1.0 - aurora$easeOut(aurora$clamp(aurora$msSince(aurora$animStartNs) / AURORA_SETTLE_MS, 0, 1)));
            default -> 0.0;
        };
    }

    // ---- Rendering ----
    /**
     * Draw the drag overlay on top of vanilla's render.
     *
     * <p>We {@code @Override} render directly rather than {@code @Inject}ing it,
     * because {@code JoinMultiplayerScreen} doesn't declare its own render
     * method in 1.21.11 (it inherits {@link Screen#render}) — an
     * {@code @Inject(method="render")} would silently no-op against the
     * synthetic bridge.
     */
    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (!aurora$enabled() || serverSelectionList == null || servers == null) {
            return;
        }
        try {
            if (aurora$animPhase == AURORA_PHASE_NONE) return;
            aurora$updateAnim(mouseY);
            if (aurora$animPhase == AURORA_PHASE_NONE) return;

            int accent = ThemeManager.color(ThemeToken.ACCENT_MARKER);
            int left = serverSelectionList.getRowLeft() - 2;
            int width = serverSelectionList.getRowWidth() + 4;
            float radius = ThemeManager.current().roundness().radiusSmall();
            double lift = aurora$liftProgress();
            ctx.enableScissor(serverSelectionList.getX(), serverSelectionList.getY(),
                    serverSelectionList.getRight(), serverSelectionList.getBottom());
            try {
                // 1) The lifted card's slot reads as an empty well while it is away.
                if (aurora$dragActive && aurora$dragFromIndex >= 0 && aurora$dragFromIndex < servers.size()) {
                    int sTop = serverSelectionList.getRowTop(aurora$dragFromIndex);
                    int sh = serverSelectionList.getRowBottom(aurora$dragFromIndex) - sTop;
                    RenderUtil.drawRoundedRectAA(ctx, left, sTop, width, sh, radius,
                            ThemeManager.withAlpha(ThemeManager.color(ThemeToken.MATERIAL_SUNKEN), (int) Math.round(0xC8 * lift)));
                    RenderUtil.drawRoundedOutlineAA(ctx, left, sTop, width, sh, radius, 1,
                            ThemeManager.withAlpha(MaterialSurface.color(ThemeToken.BORDER), (int) Math.round(0xFF * lift)));
                }
                // 2) One insertion line where the card will land.
                if (aurora$dragActive && !Double.isNaN(aurora$lineY)) {
                    // A capsule: its radius is half its thickness.
                    RenderUtil.drawRoundedRectAA(ctx, left + 4, (float) aurora$lineY - AURORA_LINE_H / 2f, width - 8,
                            AURORA_LINE_H, AURORA_LINE_H / 2f, accent);
                }
                // 3) The card itself.
                if (aurora$ghost != null) aurora$drawGhost(ctx, accent, left, width, radius, lift);
            } finally {
                ctx.disableScissor();
            }
        } catch (Throwable ignored) {
            // Rendering must never crash the screen.
        }
    }

    @Unique
    private void aurora$drawGhost(GuiGraphics ctx, int accent, int left, int width, float radius, double lift) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null || aurora$ghost == null) return;
        int h = aurora$rowHeight();
        float y = (float) aurora$ghostY;
        float scale = (float) aurora$lerp(1.0, AURORA_LIFT_SCALE, lift);

        ctx.pose().pushMatrix();
        // Scale around the card's centre (transform only; geometry stays put).
        ctx.pose().translate(left + width / 2f, y + h / 2f);
        ctx.pose().scale(scale, scale);
        ctx.pose().translate(-width / 2f, -h / 2f);
        AuroraShapes.dropShadow(ctx, 0, 3, width, h, Math.round(radius), (float) (0.55 * lift));
        RenderUtil.drawRoundedRectAA(ctx, 0, 0, width, h, radius,
                ThemeManager.withAlpha(ThemeManager.color(ThemeToken.MATERIAL_RAISED), 0xF4));
        RenderUtil.drawRoundedOutlineAA(ctx, 0, 0, width, h, radius, 1,
                com.aurora.client.util.AuroraAnim.lerpArgb(MaterialSurface.color(ThemeToken.BORDER), accent, (float) lift));

        var font = client.font;
        int nameY = AURORA_GHOST_PAD - 1;
        ctx.drawString(font, Component.literal(aurora$ghost.name), AURORA_GHOST_PAD, nameY,
                ThemeManager.color(ThemeToken.ON_BACKGROUND), false);

        String pingLabel;
        int pingColor;
        long ping = aurora$ghost.ping;
        if (ping == -2L) {
            pingLabel = "..."; pingColor = 0xFFAAAAAA;
        } else if (ping <= 0) {
            pingLabel = "?"; pingColor = 0xFF888888;
        } else {
            pingLabel = ping + "ms";
            pingColor = ping < 50 ? 0xFF55FF55 : ping < 150 ? 0xFFFFFF55 : 0xFFFF5555;
        }
        ctx.drawString(font, pingLabel, width - font.width(pingLabel) - AURORA_GHOST_PAD, nameY, pingColor, false);

        if (aurora$ghost.motd != null) {
            Component motd = aurora$ghost.motd;
            String plain = motd.getString();
            int maxW = width - AURORA_GHOST_PAD * 2;
            if (font.width(plain) > maxW) {
                plain = font.plainSubstrByWidth(plain, maxW - font.width("…")) + "…";
                motd = Component.literal(plain).withStyle(motd.getStyle());
            }
            ctx.drawString(font, motd, AURORA_GHOST_PAD, nameY + font.lineHeight + 2,
                    ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY), false);
        }
        ctx.pose().popMatrix();
    }
}
