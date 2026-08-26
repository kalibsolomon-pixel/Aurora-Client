package com.aurora.client.mixin;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Click-and-drag reordering for the multiplayer server list, with animated
 * select/move/place feedback.
 *
 * <p>Three visual phases make the motion legible:
 * <ul>
 *   <li><b>LIFT</b> — when a press becomes a drag, the source row fades and a
 *       ghost card rises out of it toward the cursor (~140ms, ease-out).</li>
 *   <li><b>MOVE</b> — the ghost follows the cursor as an elevated card
 *       (shadow + accent border), with a trailing smoothing so it glides. A
 *       live drop indicator (animated insert bar) tracks the target row, and
 *       a dashed placeholder marks the slot being moved.</li>
 *   <li><b>SETTLE</b> — on release the reorder commits immediately and the
 *       ghost glides from the release point into its new slot, scaling back
 *       to normal (~220ms, ease-in-out), then disappears.</li>
 * </ul>
 *
 * <p>Normal clicks (no movement past the threshold) are passed through
 * untouched so selection / double-click-to-join keep working.
 *
 * <p>1.21.11 note: mouse handlers take a {@link MouseButtonEvent} record, and
 * {@code AbstractSelectionList#getEntryAtPosition} is protected, so rows are
 * hit-tested manually via {@code getRowTop}/{@code getRowBottom}. All motion
 * is driven by {@link System#nanoTime()} so it's frame-rate independent and
 * survives pauses / refocuses (dt is clamped).
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
    @Unique private double aurora$dragPressX;
    @Unique private double aurora$dragPressY;
    @Unique private boolean aurora$dragActive;
    @Unique private int aurora$dragDropIndex = -1;

    // ---- Animation state ----
    @Unique private int aurora$animPhase = AURORA_PHASE_NONE;
    @Unique private long aurora$animStartNs;
    @Unique private long aurora$lastFrameNs;
    /** Ghost card position (top-left of its content box). */
    @Unique private double aurora$ghostX;
    @Unique private double aurora$ghostY;
    /** Where the settle animation starts (release cursor pos). */
    @Unique private double aurora$settleFromX;
    @Unique private double aurora$settleFromY;
    /** Target row index the ghost settles into. */
    @Unique private int aurora$settleToIndex = -1;
    /** Snapshot of the dragged server captured at lift time. */
    @Unique private GhostSnapshot aurora$ghost;

    @Unique private static final int AURORA_PHASE_NONE = 0;
    @Unique private static final int AURORA_PHASE_LIFT = 1;
    @Unique private static final int AURORA_PHASE_MOVE = 2;
    @Unique private static final int AURORA_PHASE_SETTLE = 3;

    @Unique private static final double AURORA_DRAG_THRESHOLD = 5.0;
    @Unique private static final long AURORA_LIFT_MS = 140;
    @Unique private static final long AURORA_SETTLE_MS = 220;
    /** Ghost trailing time-constant in ms (smaller = snappier). */
    @Unique private static final double AURORA_GHOST_TAU_MS = 55.0;
    @Unique private static final int AURORA_ROW_HEIGHT = 36;
    @Unique private static final int AURORA_GHOST_PAD = 8;

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

    // ---- Easing ----
    @Unique
    private static double aurora$easeOutCubic(double t) {
        double x = 1.0 - t;
        return 1.0 - x * x * x;
    }

    @Unique
    private static double aurora$easeInOutCubic(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
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
    private static long aurora$nowNs() {
        return System.nanoTime();
    }

    @Unique
    private static double aurora$msSince(long startNs) {
        return (aurora$nowNs() - startNs) / 1_000_000.0;
    }

    @Unique
    private void aurora$resetAnim() {
        aurora$animPhase = AURORA_PHASE_NONE;
        aurora$ghost = null;
        aurora$settleToIndex = -1;
    }

    // ---- Hit testing ----
    @Unique
    private int aurora$serverIndexAt(double mouseX, double mouseY) {
        if (serverSelectionList == null || servers == null) return -1;
        int left = serverSelectionList.getX();
        int right = serverSelectionList.getRight();
        if (mouseX < left || mouseX > right) return -1;
        int count = servers.size();
        for (int i = 0; i < count; i++) {
            int top = serverSelectionList.getRowTop(i);
            int bottom = serverSelectionList.getRowBottom(i);
            if (mouseY >= top && mouseY < bottom) return i;
        }
        return -1;
    }

    // ---- Mouse handlers ----
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean result = super.mouseClicked(event, doubleClick);
        if (!aurora$enabled() || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return result;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        int idx = aurora$serverIndexAt(mouseX, mouseY);
        if (idx >= 0) {
            aurora$dragArmed = true;
            aurora$dragFromIndex = idx;
            aurora$dragPressX = mouseX;
            aurora$dragPressY = mouseY;
            aurora$dragActive = false;
            aurora$dragDropIndex = idx;
        } else {
            aurora$dragArmed = false;
            aurora$dragFromIndex = -1;
        }
        return result;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        boolean result = super.mouseDragged(event, deltaX, deltaY);
        if (!aurora$enabled() || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !aurora$dragArmed) {
            return result;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        if (!aurora$dragActive && Math.abs(mouseY - aurora$dragPressY) > AURORA_DRAG_THRESHOLD) {
            aurora$beginLift(mouseX, mouseY);
        }
        if (aurora$dragActive && servers != null) {
            int idx = aurora$serverIndexAt(mouseX, mouseY);
            if (idx < 0 && serverSelectionList != null && servers.size() > 0) {
                idx = (mouseY < serverSelectionList.getRowTop(0))
                        ? 0 : servers.size() - 1;
            }
            if (idx >= 0) aurora$dragDropIndex = idx;
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
            int to = aurora$serverIndexAt(event.x(), event.y());
            if (to < 0) to = drop;
            aurora$beginSettle(from, to, event.x(), event.y());
            // Swallow the release so vanilla can't misread the drag as a join.
            return true;
        }
        // Click without drag: cancel any lingering animation and defer to vanilla.
        aurora$resetAnim();
        return super.mouseReleased(event);
    }

    // ---- Phase transitions ----
    @Unique
    private void aurora$beginLift(double mouseX, double mouseY) {
        aurora$dragActive = true;
        aurora$ghost = aurora$snapshot(aurora$dragFromIndex);
        // Ghost starts at the source row's position, then rises toward cursor.
        if (serverSelectionList != null) {
            int left = serverSelectionList.getX();
            aurora$ghostX = left;
            aurora$ghostY = serverSelectionList.getRowTop(aurora$dragFromIndex);
        } else {
            aurora$ghostX = mouseX;
            aurora$ghostY = mouseY;
        }
        aurora$animPhase = AURORA_PHASE_LIFT;
        aurora$animStartNs = aurora$nowNs();
        aurora$lastFrameNs = aurora$animStartNs;
    }

    @Unique
    private void aurora$beginSettle(int from, int to, double releaseX, double releaseY) {
        // Commit the reorder immediately so the list state is correct; the
        // ghost then animates from the release point into the new slot.
        boolean moved = (to >= 0 && to < servers.size() && to != from);
        if (moved) {
            aurora$moveServer(from, to);
            aurora$settleToIndex = to;
        } else {
            aurora$settleToIndex = from;
        }
        aurora$settleFromX = aurora$ghostX;
        aurora$settleFromY = aurora$ghostY;
        // If we never lifted (e.g. released right at threshold), seed from cursor.
        if (aurora$animPhase == AURORA_PHASE_NONE || aurora$ghost == null) {
            aurora$ghost = aurora$snapshot(from);
            aurora$settleFromX = releaseX;
            aurora$settleFromY = releaseY;
        }
        aurora$animPhase = AURORA_PHASE_SETTLE;
        aurora$animStartNs = aurora$nowNs();
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
    private void aurora$updateAnim(double mouseX, double mouseY) {
        long now = aurora$nowNs();
        // Clamp dt to avoid huge jumps after a pause/refocus (max 100ms).
        double dtMs = aurora$clamp((now - aurora$lastFrameNs) / 1_000_000.0, 0.0, 100.0);
        aurora$lastFrameNs = now;

        switch (aurora$animPhase) {
            case AURORA_PHASE_LIFT: {
                double t = aurora$clamp(aurora$msSince(aurora$animStartNs) / AURORA_LIFT_MS, 0, 1);
                double e = aurora$easeOutCubic(t);
                if (serverSelectionList != null) {
                    double fromX = serverSelectionList.getX();
                    double fromY = serverSelectionList.getRowTop(aurora$dragFromIndex);
                    // Rise toward the cursor, offset so the ghost sits above the pointer.
                    double targetX = mouseX - 40;
                    double targetY = mouseY - AURORA_ROW_HEIGHT / 2.0 - 14;
                    aurora$ghostX = aurora$lerp(fromX, targetX, e);
                    aurora$ghostY = aurora$lerp(fromY, targetY, e) - (12 * e); // extra lift
                }
                if (t >= 1.0) {
                    aurora$animPhase = AURORA_PHASE_MOVE;
                    aurora$animStartNs = now;
                }
                break;
            }
            case AURORA_PHASE_MOVE: {
                // Smoothly trail the cursor (exponential smoothing, dt-aware).
                double f = 1.0 - Math.exp(-dtMs / AURORA_GHOST_TAU_MS);
                double targetX = mouseX - 40;
                double targetY = mouseY - AURORA_ROW_HEIGHT / 2.0 - 14;
                aurora$ghostX = aurora$lerp(aurora$ghostX, targetX, f);
                aurora$ghostY = aurora$lerp(aurora$ghostY, targetY, f);
                break;
            }
            case AURORA_PHASE_SETTLE: {
                double t = aurora$clamp(aurora$msSince(aurora$animStartNs) / AURORA_SETTLE_MS, 0, 1);
                double e = aurora$easeInOutCubic(t);
                double toX, toY;
                if (serverSelectionList != null && aurora$settleToIndex >= 0) {
                    toX = serverSelectionList.getX();
                    toY = serverSelectionList.getRowTop(aurora$settleToIndex);
                } else {
                    toX = aurora$settleFromX;
                    toY = aurora$settleFromY;
                }
                aurora$ghostX = aurora$lerp(aurora$settleFromX, toX, e);
                aurora$ghostY = aurora$lerp(aurora$settleFromY, toY, e);
                if (t >= 1.0) {
                    aurora$resetAnim();
                }
                break;
            }
            default:
                break;
        }
    }

    /** Scale of the ghost card: lifts up to 1.05 during move, settles back to 1.0. */
    @Unique
    private double aurora$ghostScale() {
        switch (aurora$animPhase) {
            case AURORA_PHASE_LIFT: {
                double t = aurora$clamp(aurora$msSince(aurora$animStartNs) / AURORA_LIFT_MS, 0, 1);
                return aurora$lerp(1.0, 1.05, aurora$easeOutCubic(t));
            }
            case AURORA_PHASE_MOVE:
                return 1.05;
            case AURORA_PHASE_SETTLE: {
                double t = aurora$clamp(aurora$msSince(aurora$animStartNs) / AURORA_SETTLE_MS, 0, 1);
                return aurora$lerp(1.05, 1.0, aurora$easeInOutCubic(t));
            }
            default:
                return 1.0;
        }
    }

    /** Alpha for dimming the source slot while its content is "lifted out". */
    @Unique
    private double aurora$sourceDimAlpha() {
        if (aurora$animPhase == AURORA_PHASE_LIFT) {
            double t = aurora$clamp(aurora$msSince(aurora$animStartNs) / AURORA_LIFT_MS, 0, 1);
            return aurora$easeOutCubic(t) * 0.55;
        }
        if (aurora$animPhase == AURORA_PHASE_MOVE) return 0.55;
        return 0.0;
    }

    // ---- Rendering ----
    /**
     * Draw the drag overlay on top of vanilla's render.
     *
     * <p>We {@code @Override} render directly rather than {@code @Inject}ing it,
     * because {@code JoinMultiplayerScreen} doesn't declare its own render
     * method in 1.21.11 (it inherits {@link Screen#render}) — an
     * {@code @Inject(method="render")} would silently no-op against the
     * synthetic bridge. Overriding guarantees our overlay runs every frame
     * regardless of how the target declares render.
     */
    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (!aurora$enabled() || serverSelectionList == null) {
            return;
        }
        try {
            aurora$updateAnim(mouseX, mouseY);

            int accent = AuroraConfig.get().themeOrDefault().accent | 0xFF000000;
            int left = serverSelectionList.getX();
            int right = serverSelectionList.getRight();

            // 1) Dashed placeholder over the source slot while moving.
            if (aurora$dragActive && aurora$dragFromIndex >= 0 && servers != null
                    && aurora$dragFromIndex < servers.size()) {
                int sTop = serverSelectionList.getRowTop(aurora$dragFromIndex);
                int sBottom = serverSelectionList.getRowBottom(aurora$dragFromIndex);
                double dim = aurora$sourceDimAlpha();
                if (dim > 0.0) {
                    // Dark veil so the lifted-out slot reads as empty even over
                    // light rows. Alpha scales with the lift progress.
                    int veil = (int) (dim * 140) << 24;
                    ctx.fill(left, sTop, right, sBottom, veil);
                }
                aurora$drawDashedRect(ctx, left + 2, sTop + 2, right - 2, sBottom - 2, accent);
            }

            // 2) Drop indicator (animated insert bar) during a live drag.
            if (aurora$dragActive && aurora$dragDropIndex >= 0) {
                int row = aurora$dragDropIndex;
                int top = serverSelectionList.getRowTop(row);
                int bottom = serverSelectionList.getRowBottom(row);
                int fill = (accent & 0x00FFFFFF) | 0x26000000; // ~15% highlight
                ctx.fill(left, top, right, bottom, fill);
                // Pulsing insert bar at the top edge of the target row.
                double pulse = 0.5 + 0.5 * Math.sin(aurora$nowNs() / 200_000_000.0);
                int barH = 2 + (int) (2 * pulse);
                ctx.fill(left, top - barH, right, top + barH, accent);
            }

            // 3) Ghost card (lift / move / settle).
            if (aurora$ghost != null
                    && aurora$animPhase != AURORA_PHASE_NONE) {
                aurora$drawGhost(ctx, accent, left, right);
            }
        } catch (Throwable ignored) {
            // Rendering must never crash the screen.
        }
    }

    @Unique
    private void aurora$drawGhost(GuiGraphics ctx, int accent, int listLeft, int listRight) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null || aurora$ghost == null) return;

        double scale = aurora$ghostScale();
        int w = (listRight - listLeft);
        int h = AURORA_ROW_HEIGHT;
        // Scale around the card's horizontal center for a clean "grow" effect.
        double drawW = w * scale;
        double drawH = h * scale;
        double offsetX = (drawW - w) / 2.0;
        double offsetY = (drawH - h) / 2.0;
        int x = (int) (aurora$ghostX - offsetX);
        int y = (int) (aurora$ghostY - offsetY);
        int ww = (int) drawW;
        int hh = (int) drawH;

        // Drop shadow (offset down-right, blurred look via stacked fills).
        int shadowAlpha = aurora$animPhase == AURORA_PHASE_MOVE ? 0x66 : 0x44;
        ctx.fill(x + 3, y + 5, x + ww + 3, y + hh + 5, (shadowAlpha << 24));
        ctx.fill(x + 2, y + 3, x + ww + 2, y + hh + 3, ((shadowAlpha - 0x10) << 24));

        // Card background.
        ctx.fill(x, y, x + ww, y + hh, 0xF0101014);
        // Accent left border bar.
        ctx.fill(x, y, x + 3, y + hh, accent);
        // Subtle top highlight.
        ctx.fill(x + 3, y, x + ww, y + 1, 0x22FFFFFF);

        // Content: server name + ping, scaled font positions.
        var font = client.font;
        int textX = x + AURORA_GHOST_PAD;
        int nameY = y + AURORA_GHOST_PAD - 1;

        Component name = Component.literal(aurora$ghost.name);
        ctx.drawString(font, name, textX, nameY, 0xFFFFFFFF, false);

        // Ping chip on the right.
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
        int pingW = font.width(pingLabel);
        int chipX = x + ww - pingW - AURORA_GHOST_PAD - 6;
        int chipY = nameY - 1;
        ctx.fill(chipX, chipY, chipX + pingW + 6, chipY + font.lineHeight + 1, 0x33000000);
        ctx.drawString(font, pingLabel, chipX + 3, chipY + 1, pingColor, false);

        // MOTD line (if present) below the name.
        if (aurora$ghost.motd != null) {
            int motdY = nameY + font.lineHeight + 2;
            // Truncate to card width so long MOTDs don't overflow.
            Component motd = aurora$ghost.motd;
            String plain = motd.getString();
            int maxW = ww - AURORA_GHOST_PAD * 2;
            if (font.width(plain) > maxW) {
                plain = font.plainSubstrByWidth(plain, maxW - font.width("…")) + "…";
                motd = Component.literal(plain).withStyle(motd.getStyle());
            }
            ctx.drawString(font, motd, textX, motdY, 0xFFBBBBBB, false);
        }
    }

    /**
     * Draw a dashed rectangle outline by stamping short segments along each
     * edge. Kept allocation-free for render-path safety.
     */
    @Unique
    private void aurora$drawDashedRect(GuiGraphics ctx, int x0, int y0, int x1, int y1, int color) {
        int dash = 4;
        int gap = 3;
        // Top & bottom
        for (int x = x0; x < x1; x += dash + gap) {
            int xe = Math.min(x + dash, x1);
            ctx.fill(x, y0, xe, y0 + 1, color);
            ctx.fill(x, y1 - 1, xe, y1, color);
        }
        // Left & right
        for (int y = y0; y < y1; y += dash + gap) {
            int ye = Math.min(y + dash, y1);
            ctx.fill(x0, y, x0 + 1, ye, color);
            ctx.fill(x1 - 1, y, x1, ye, color);
        }
    }
}