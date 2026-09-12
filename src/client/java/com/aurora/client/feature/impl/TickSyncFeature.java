package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TickSyncFeature implements Feature {
    public static final String ID = "tick_sync";
    public static TickSyncFeature INSTANCE;

    boolean isTickRateChangedLastTick;
    boolean isPacketReceivedThisTick;
    boolean isPacketRangeUpdatedThisTick;

    int afterLazyPacketCooldown;
    public int packetRange;
    public int instantPacketRange;
    public int avgPacketDelay;

    long lastSyncTime = System.currentTimeMillis();
    long lastServerPacketTime = System.currentTimeMillis();
    long lastPacketRangeUpdatedTime = System.currentTimeMillis();

    public float clientTPS = 20;
    public float serverTPS = 20;

    static final int TICK_BUFFER_SIZE = 10;
    static final int RANGE_BUFFER_SIZE = 40;
    static final int FAST_RANGE_BUFFER_SIZE = 10;

    static final int OUTLIER_LIMIT = 8;
    static final int SYNC_THRESHOLD_OFFSET = 4;

    public static final int SAMPLING_RANGE = 55;

    List<Integer> packetDelayBuffer = new ArrayList<>(Collections.nCopies(1, 12));
    List<Integer> packetRangeBuffer = new ArrayList<>(Collections.nCopies(1, 0));
    List<Integer> fastPacketRangeBuffer = new ArrayList<>(Collections.nCopies(1, 0));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onRegister() {
        INSTANCE = this;
        ClientTickEvents.START_CLIENT_TICK.register(client -> this.onClientTickStart());
        
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            setTickRate(20);
            serverTPS = 20;
            clientTPS = 20;
        });
        
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            serverTPS = 20;
            clientTPS = 20;
            setTickRate(20);

            isTickRateChangedLastTick = false;
            afterLazyPacketCooldown = 0;
            lastServerPacketTime = System.currentTimeMillis();
            lastSyncTime = System.currentTimeMillis();
            packetDelayBuffer.clear();
            packetRangeBuffer.clear();
            fastPacketRangeBuffer.clear();
        });
    }

    @Override
    public void onTick(Minecraft client) {
        // Restore a vanilla tick rate the moment the toggle goes off mid-game —
        // otherwise the last Aurora-adjusted rate stays applied to the level
        // until reconnect (the disconnect handler is the only other restore point).
        if (!AuroraConfig.get().tickSyncEnabled) {
            isTickRateChangedLastTick = false;
            if (clientTPS != 20f) {
                setTickRate(20);
            }
            return;
        }
        if (isTickRateChangedLastTick) {
            isTickRateChangedLastTick = false;
            setTickRate(Math.min(20, serverTPS));
        }
        final long now = System.currentTimeMillis();

        if (AuroraConfig.get().tickSyncEnabled && isPlayingInGame() && canSync() && (now - lastSyncTime) > 1000) {
            if (isWeirdSyncOccurred()) {
                fixWeirdSync();
            }
            if (isTickSyncRequired()) {
                matchTickSync();
            }
        }
    }

    public void onEntityPacket() {
        final long now = System.currentTimeMillis();
        
        AuroraConfig config = AuroraConfig.get();
        if (Minecraft.getInstance().isSameThread()) {
            if (!config.tickSyncUseNettyCriteria) {
                lastServerPacketTime = now;
                isPacketReceivedThisTick = true;
            }
        } else {
            if (config.tickSyncUseNettyCriteria) {
                lastServerPacketTime = now;
                isPacketReceivedThisTick = true;
            }

            if (!isPacketRangeUpdatedThisTick) {
                final int delta = (int)(now - lastPacketRangeUpdatedTime);

                if (0 < delta) {
                    final int halfDuration = (int)applyRatio(25);
                    final int normalDeviation = (delta % getTickDuration() + halfDuration) % getTickDuration() - halfDuration;
                    addToPacketRangeBuffer(normalDeviation);
                }
                if (getTickDuration() * 2 <= delta) {
                    afterLazyPacketCooldown = 20;
                }
                lastPacketRangeUpdatedTime = now;
                isPacketRangeUpdatedThisTick = true;
            }
        }
    }

    int getTickDuration() { return (int)(1000 / serverTPS); }
    float applyRatio(float x) { return x * (20 / serverTPS); }

    boolean isPlayingInGame() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.player != null && !client.isPaused();
    }

    public boolean canSync() {
        if (AuroraConfig.get().tickSyncUseAutoMargin) return serverTPS <= 20 && getCurrentFPS() > 40 && instantPacketRange < applyRatio(25);
        else return serverTPS <= 20 && getCurrentFPS() > 40;
    }

    int getCurrentFPS() {
        return Math.max(1, Minecraft.getInstance().getFps());
    }

    public void onClientTickStart() {
        if (isPlayingInGame()) {
            final long now = System.currentTimeMillis();
            final int packetDelay = (int)(now - lastServerPacketTime);

            if (0 < packetDelay && packetDelay < applyRatio(SAMPLING_RANGE)) {
                addToTickDeltaBuffer(packetDelay);
            } else {
                addToTickDeltaBuffer(avgPacketDelay);
            }

            if (afterLazyPacketCooldown > 0) afterLazyPacketCooldown--;

            avgPacketDelay = calculateMean(packetDelayBuffer);
            packetRange = calculateRange(packetRangeBuffer, RANGE_BUFFER_SIZE);
            instantPacketRange = calculateRange(fastPacketRangeBuffer, FAST_RANGE_BUFFER_SIZE);
        }
        isPacketRangeUpdatedThisTick = false;
        isPacketReceivedThisTick = false;
    }

    void addToTickDeltaBuffer(int newDelta) {
        packetDelayBuffer.add(newDelta);
        if (packetDelayBuffer.size() > TICK_BUFFER_SIZE) {
            packetDelayBuffer.remove(0);
        }
    }

    void addToPacketRangeBuffer(int newDelta) {
        if (Math.abs(newDelta) <= OUTLIER_LIMIT) packetRangeBuffer.add(newDelta);
        if (packetRangeBuffer.size() > RANGE_BUFFER_SIZE) {
            packetRangeBuffer.remove(0);
        }

        fastPacketRangeBuffer.add(newDelta);
        if (fastPacketRangeBuffer.size() > FAST_RANGE_BUFFER_SIZE) {
            fastPacketRangeBuffer.remove(0);
        }
    }

    int calculateMean(List<Integer> list) {
        final int size = list.size();
        if (size == 0) return 10;
        int sum = 0;
        for (int v : list) {
            sum += v;
        }
        return sum / size;
    }

    int calculateRange(List<Integer> list, int requireBufferSize) {
        if (list == null || list.isEmpty()) return 12;
        if (list.size() < requireBufferSize) return 12;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        List<Integer> snapshot = new ArrayList<>(list);

        for (Integer i : snapshot) {
            if (i == null) continue;
            if (i < min) min = i;
            if (i > max) max = i;
        }

        return max - min;
    }

    boolean isWeirdSyncOccurred() {
        if (packetDelayBuffer.isEmpty()) return false;
        final int range = calculateRange(packetDelayBuffer, TICK_BUFFER_SIZE);
        return (packetDelayBuffer.size() >= TICK_BUFFER_SIZE) && range > (applyRatio(35));
    }

    void fixWeirdSync() {
        if (packetDelayBuffer.isEmpty()) return;

        lastSyncTime = System.currentTimeMillis();

        final float min = Math.min(getPacketMargin() / 2.0f, Collections.min(packetDelayBuffer));
        final int tickToPush = quantizeToFrame(applyRatio((getPacketMargin() - min)));
        shiftNextTickDuration(tickToPush);
    }

    int getPacketMargin() {
        final int max = OUTLIER_LIMIT * 2;
        final int min = afterLazyPacketCooldown > 0 ? 8 : 6;
        return AuroraConfig.get().tickSyncUseAutoMargin ? Mth.clamp(packetRange, min, max) : 10;
    }

    boolean isTickSyncRequired() {
        return getThreshold() < avgPacketDelay;
    }

    int getThreshold() {
        final int threshold = (int)applyRatio(getPacketMargin()) + SYNC_THRESHOLD_OFFSET;

        if (threshold < getFrameDuration()) {
            return (int)(getFrameDuration() * 1.5f);
        } else {
            return threshold;
        }
    }

    float getFrameDuration() { return 1000f / getCurrentFPS(); }

    void matchTickSync() {
        lastSyncTime = System.currentTimeMillis();
        int tickToPush = (getTickDuration() - avgPacketDelay) + quantizeToFrame(applyRatio(getPacketMargin() + getMatchSyncOffset()));

        if (AuroraConfig.get().tickSyncUseFastSync && tickToPush > getTickDuration() / 2)
            tickToPush -= getTickDuration(); 

        shiftNextTickDuration(tickToPush);
    }

    int quantizeToFrame(float margin) {
        if (margin < getFrameDuration()) return (int)getFrameDuration();
        if (AuroraConfig.get().tickSyncUseAutoMargin) return (int)(Math.floor(margin / getFrameDuration()) * getFrameDuration());
        else return (int)(Math.round(margin / getFrameDuration()) * getFrameDuration());
    }

    int getMatchSyncOffset() {
        if (!AuroraConfig.get().tickSyncUseAutoMargin) return 0;
        if (getCurrentFPS() > 240) return 0;
        if (getCurrentFPS() > 120) return 1;
        return 2;
    }

    void shiftNextTickDuration(int term) {
        final int MIN_TICK_DURATION = 5;
        final int nextTickDuration = Math.max(MIN_TICK_DURATION, term + getTickDuration());
        final float tickRate = 1000f / nextTickDuration;

        setTickRate(tickRate);

        packetDelayBuffer.clear();
        addToTickDeltaBuffer(getPacketMargin());

        avgPacketDelay = getPacketMargin();
        isTickRateChangedLastTick = true;
    }

    void setTickRate(float tickRate) {
        clientTPS = tickRate;
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world != null) {
            world.tickRateManager().setTickRate(tickRate);
        }
    }
}