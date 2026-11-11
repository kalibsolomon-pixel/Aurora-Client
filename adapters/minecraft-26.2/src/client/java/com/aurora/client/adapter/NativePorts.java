package com.aurora.client.adapter;

import com.aurora.client.port.InputPort;
import com.aurora.client.port.WorldPort;
import com.aurora.client.util.AuroraKey;
import com.aurora.client.util.FrameClock;
import com.aurora.client.util.WorldScope;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Minecraft 26.2 bindings. No native type crosses into the core. */
public final class NativePorts {
    private NativePorts() {}
    public static void install() {
        FrameClock.install(() -> GLFW.glfwGetTime());
        AuroraKey.install(new InputPort() {
            public boolean isDown(int scancode) {
                Minecraft mc = Minecraft.getInstance();
                if (mc == null || mc.getWindow() == null) return false;
                try { return InputConstants.isKeyDown(mc.getWindow(), com.aurora.client.config.KeyboardCodes.toGlfw(scancode)); }
                catch (Exception e) { return false; }
            }
            public boolean suppressed() {
                Minecraft mc = Minecraft.getInstance();
                return mc != null && mc.gui.screen() != null;
            }
        });
        WorldScope.install(new WorldPort() {
            public boolean loaded() { return Minecraft.getInstance().level != null; }
            public String singleplayerName() {
                Minecraft mc = Minecraft.getInstance();
                return mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null
                        ? mc.getSingleplayerServer().getWorldData().getLevelName() : null;
            }
            public String serverAddress() {
                var server = Minecraft.getInstance().getCurrentServer();
                return server == null ? null : server.ip;
            }
        });
    }
}
