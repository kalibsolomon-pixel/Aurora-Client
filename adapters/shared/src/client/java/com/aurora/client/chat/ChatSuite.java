package com.aurora.client.chat;

import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.AuroraKey;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Shared runtime coordinator for Aurora's Chat Suite: duplicate metadata,
 * inline player heads, Chat Peek state, and async per-server chat history.
 *
 * <p>All methods short-circuit immediately when {@link AuroraConfig#chatEnabled}
 * is {@code false}. Never sends chat messages or network packets.
 */
public final class ChatSuite {
    public static final int TIMESTAMP_COLOR = 0xFF9EA6B2;
    public static final int DUPLICATE_COLOR = 0xFF9EA6B2;
    public static final int HISTORY_DIVIDER_COLOR = 0xFF7D8590;
    public static final int HISTORY_TEXT_COLOR = 0xFFB0B8C4;

    public record MessageMeta(String canonicalText, int repeatCount, String copyText) {}

    private static final Map<GuiMessage, MessageMeta> MESSAGE_META =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private static final ChatHistorySession SESSION = new ChatHistorySession();
    private static volatile CompletableFuture<Void> lastDisconnectFlush = CompletableFuture.completedFuture(null);
    private static boolean replayingHistory;

    private ChatSuite() {}

    private static boolean lifecycleRegistered;

    /** Registers Fabric connection/lifecycle hooks for per-server chat history. */
    public static void registerLifecycle() {
        if (lifecycleRegistered) return;
        lifecycleRegistered = true;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) ->
                mc.execute(() -> onWorldJoin(mc)));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) ->
                mc.execute(ChatSuite::onDisconnect));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            onDisconnect();
            try {
                lastDisconnectFlush.get(500L, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {}
            ChatHistoryStore.flush(1500L);
        });
    }

    public static boolean isEnabled() {
        return AuroraConfig.get().chatEnabled;
    }

    /** True while holding the Chat Peek key in-world with no screen open. */
    public static boolean isPeeking() {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.chatEnabled || !cfg.chatPeekEnabled) return false;
        if (cfg.chatPeekKey == AuroraKey.UNBOUND) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || NativeUi.screen(mc) != null) return false;
        return AuroraKey.isDown(cfg.chatPeekKey);
    }

    public static boolean isReplayingHistory() {
        return replayingHistory;
    }

    public static void putMeta(GuiMessage message, MessageMeta meta) {
        if (message == null || meta == null) return;
        synchronized (MESSAGE_META) {
            int maxEntries = Math.max(256, AuroraConfig.get().chatHistoryLimit + 64);
            if (MESSAGE_META.size() > maxEntries) MESSAGE_META.clear();
            MESSAGE_META.put(message, meta);
        }
    }

    public static MessageMeta meta(GuiMessage message) {
        return message == null ? null : MESSAGE_META.get(message);
    }

    public static void removeMeta(GuiMessage message) {
        if (message != null) MESSAGE_META.remove(message);
    }

    public static void clearMeta() {
        MESSAGE_META.clear();
    }

    /** Clean clipboard text for a chat message (uses original pre-decorated text when tracked). */
    public static String copyText(GuiMessage message) {
        if (message == null) return "";
        MessageMeta m = MESSAGE_META.get(message);
        if (m != null && m.copyText() != null && !m.copyText().isEmpty()) {
            return m.copyText();
        }
        return ChatFormatting.extractCopyText(message.content().getString());
    }

    /**
     * Resolves an online player's {@link GameProfile} for an incoming chat
     * message so {@link PlayerSprite} can draw their head inline.
     */
    public static GameProfile resolveSenderProfile(Minecraft mc, String rawPlainText) {
        if (mc == null || mc.getConnection() == null || rawPlainText == null || rawPlainText.isBlank()) {
            return null;
        }
        var online = mc.getConnection().getOnlinePlayers();
        if (online == null || online.isEmpty()) return null;

        List<String> names = new ArrayList<>(online.size());
        for (PlayerInfo info : online) {
            if (info != null && info.getProfile() != null && info.getProfile().name() != null) {
                names.add(info.getProfile().name());
            }
        }
        String matched = ChatFormatting.extractSenderName(rawPlainText, names);
        if (matched == null) return null;
        for (PlayerInfo info : online) {
            if (info != null && info.getProfile() != null && matched.equalsIgnoreCase(info.getProfile().name())) {
                return info.getProfile();
            }
        }
        return null;
    }

    /** Builds the decorated chat message with optional timestamp, inline head, and duplicate badge. */
    public static Component decorate(Minecraft mc, Component original, int repeatCount) {
        AuroraConfig cfg = AuroraConfig.get();
        boolean addTimestamp = cfg.chatTimestampsEnabled && !replayingHistory;
        boolean addBadge = cfg.chatCompactDuplicates && repeatCount > 1;
        GameProfile sender = (cfg.chatHeadsEnabled && !replayingHistory)
                ? resolveSenderProfile(mc, original.getString())
                : null;

        if (!addTimestamp && !addBadge && sender == null) {
            return original;
        }

        MutableComponent out = Component.empty();
        if (addTimestamp) {
            String ts = ChatFormatting.formatTimestamp(LocalTime.now(), cfg.chatTimestampFormat);
            out.append(Component.literal(ts).withColor(TIMESTAMP_COLOR));
        }
        if (sender != null) {
            out.append(Component.object(new PlayerSprite(ResolvableProfile.createResolved(sender), true), Component.empty()));
            out.append(Component.literal(" "));
        }
        out.append(original);
        if (addBadge) {
            out.append(Component.literal(ChatFormatting.duplicateBadge(repeatCount)).withColor(DUPLICATE_COLOR));
        }
        return out;
    }

    /** Computes the current server or singleplayer save-directory scope key. */
    public static String currentScope(Minecraft mc) {
        if (mc == null) return null;
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "mp:" + server.ip.trim();
        }
        var sp = mc.getSingleplayerServer();
        if (sp != null) {
            Path worldRoot = sp.getWorldPath(LevelResource.ROOT);
            String saveScope = ChatFormatting.singleplayerScope(worldRoot);
            if (saveScope != null) return saveScope;
            if (sp.getWorldData() != null) {
                String name = sp.getWorldData().getLevelName();
                if (name != null && !name.isBlank()) return "sp:" + name;
            }
        }
        return null;
    }

    /** Records an incoming live chat message into the per-server history buffer. */
    public static void recordIncoming(Minecraft mc, String plainText) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.chatEnabled || !cfg.chatPerServerHistory || replayingHistory) return;
        if (plainText == null || plainText.isBlank()) return;
        String scope = currentScope(mc);
        if (scope == null) return;
        SESSION.recordIncoming(scope, plainText, System.currentTimeMillis(), true, cfg.chatPerServerMaxSaved);
    }

    /** Loads and replays saved per-server chat history off the main thread on world/server join. */
    public static void onWorldJoin(Minecraft mc) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.chatEnabled || !cfg.chatPerServerHistory || mc == null) return;
        String scope = currentScope(mc);
        if (scope == null) return;
        int limit = ChatHistorySession.clampLimit(cfg.chatPerServerMaxSaved);

        var sp = mc.getSingleplayerServer();
        Path spWorldRoot = sp != null ? sp.getWorldPath(LevelResource.ROOT) : null;
        String spLevelName = (sp != null && sp.getWorldData() != null) ? sp.getWorldData().getLevelName() : null;
        String legacyScope = (spLevelName != null && !spLevelName.isBlank()) ? "sp:" + spLevelName : null;

        CompletableFuture<List<ChatHistoryStore.Entry>> loadFuture = CompletableFuture.supplyAsync(() -> {
            if (legacyScope != null && spWorldRoot != null) {
                boolean unambiguous = isLegacySingleplayerOwnerUnambiguous(spWorldRoot, spLevelName);
                return ChatHistoryStore.loadOrMigrateLegacy(scope, legacyScope, unambiguous, limit);
            }
            return ChatHistoryStore.load(scope, limit);
        });

        long generation = SESSION.beginSession(scope, true, limit, loadFuture);
        if (generation < 0L) return; // Same session/dimension respawn — don't duplicate divider.

        loadFuture.thenAccept(entries -> mc.execute(() -> {
            if (!SESSION.completeLoad(generation, entries, System.currentTimeMillis())) return;
            if (entries.isEmpty()) return;
            if (!AuroraConfig.get().chatEnabled || !AuroraConfig.get().chatPerServerHistory) return;
            if (!scope.equals(currentScope(mc))) return;
            ChatComponent chat = NativeUi.chat(mc);
            if (chat == null) return;
            replayingHistory = true;
            try {
                for (ChatHistoryStore.Entry e : entries) {
                    chat.addClientSystemMessage(Component.literal(e.text()).withColor(HISTORY_TEXT_COLOR));
                }
                chat.addClientSystemMessage(Component.literal("\u2500\u2500 Previous session \u2500\u2500")
                        .withColor(HISTORY_DIVIDER_COLOR));
            } finally {
                replayingHistory = false;
            }
        }));
    }

    /**
     * Checks whether {@code worldRoot} is the sole save in its parent saves
     * directory carrying {@code displayName} (case-insensitively, matching the
     * legacy scope hash). Runs off the main thread during world join.
     */
    private static boolean isLegacySingleplayerOwnerUnambiguous(Path worldRoot, String displayName) {
        if (worldRoot == null || displayName == null || displayName.isBlank()) return false;
        Path normRoot = worldRoot.toAbsolutePath().normalize();
        Path savesDir = normRoot.getParent();
        if (savesDir == null || !Files.isDirectory(savesDir)) return false;
        int matches = 0;
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(savesDir)) {
            for (Path candidate : dirs) {
                if (!Files.isDirectory(candidate)) continue;
                Path normCandidate = candidate.toAbsolutePath().normalize();
                if (normCandidate.equals(normRoot)) {
                    matches++;
                    continue;
                }
                Path levelDat = normCandidate.resolve("level.dat");
                if (!Files.isRegularFile(levelDat)) continue;
                String otherName = readLevelName(levelDat);
                if (otherName == null) return false; // Unreadable sibling => cannot prove unique ownership.
                if (displayName.equalsIgnoreCase(otherName)) {
                    matches++;
                    if (matches > 1) return false;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return matches == 1;
    }

    private static String readLevelName(Path levelDat) {
        try {
            CompoundTag root = NbtIo.readCompressed(levelDat, NbtAccounter.unlimitedHeap());
            CompoundTag data = root.getCompoundOrEmpty("Data");
            String name = data.getStringOr("LevelName", "");
            return name.isBlank() ? null : name;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Flushes any unsaved per-server history on disconnect or shutdown. */
    public static void onDisconnect() {
        lastDisconnectFlush = SESSION.disconnect();
        clearMeta();
    }
}
