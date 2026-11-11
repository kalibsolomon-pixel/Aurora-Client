package com.aurora.client.util;

import com.aurora.client.port.InputPort;
import com.aurora.client.port.WorldPort;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePortContractTest {
    @Test void heldKeysAcrossMenusDoNotProduceASecondPress() {
        class Input implements InputPort {
            boolean down, menu;
            public boolean isDown(int key) { return down; }
            public boolean suppressed() { return menu; }
        }
        var input = new Input();
        AuroraKey.install(input);
        var edge = new AuroraKey.EdgeDetector();
        try {
            assertFalse(edge.justPressed(4));
            input.down = true;
            assertTrue(edge.justPressed(4));
            input.menu = true;
            assertFalse(AuroraKey.isDown(4));
            assertFalse(edge.justPressed(4));
            input.menu = false;
            assertTrue(AuroraKey.isDown(4));
            assertFalse(edge.justPressed(4));
            input.down = false;
            assertFalse(edge.justPressed(4));
            input.down = true;
            assertTrue(edge.justPressed(4));
            assertFalse(AuroraKey.isDown(-1));
        } finally {
            AuroraKey.install(new Input());
        }
    }

    @Test void integratedWorldIdentityWinsOverTheLanAddressAndDisconnectClearsIt() {
        class World implements WorldPort {
            boolean loaded = true;
            String name = "New World", address = "Example.COM:25565";
            public boolean loaded() { return loaded; }
            public String singleplayerName() { return name; }
            public String serverAddress() { return address; }
        }
        var world = new World();
        WorldScope.install(world);
        try {
            assertEquals("sp:new world", WorldScope.current());
            world.name = null;
            assertEquals("mp:example.com_25565", WorldScope.current());
            world.loaded = false;
            assertEquals("none", WorldScope.current());
            world.loaded = true;
            world.address = null;
            assertEquals("none", WorldScope.current());
        } finally {
            world.loaded = false;
        }
    }

    @Test void clockPreservesTheNativeEpochRatherThanRebasingDeadlines() {
        try {
            FrameClock.install(() -> 123456.75);
            assertEquals(123456.75, FrameClock.seconds());
        } finally {
            FrameClock.install(() -> System.nanoTime() * 1.0e-9);
        }
    }
}
