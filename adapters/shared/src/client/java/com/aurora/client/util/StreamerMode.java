package com.aurora.client.util;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Collection;
import java.util.UUID;

/**
 * Shared Minecraft-side runtime helper for Streamer Mode.
 *
 * <p>Every method short-circuits immediately when
 * {@link AuroraConfig#streamerModeEnabled} is {@code false}, ensuring zero
 * overhead in factory defaults.
 */
public final class StreamerMode {
    private StreamerMode() {}

    public static boolean isActive() {
        return AuroraConfig.get().streamerModeEnabled;
    }

    public static boolean hideOwnName() {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.streamerModeEnabled && cfg.streamerHideOwnName;
    }

    public static boolean hideOwnSkin() {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.streamerModeEnabled && cfg.streamerHideOwnSkin;
    }

    public static boolean hideServerAddress() {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.streamerModeEnabled && cfg.streamerHideServerAddress;
    }

    public static boolean hideCoords() {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.streamerModeEnabled && cfg.streamerHideCoords;
    }

    public static boolean hideOtherNames() {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.streamerModeEnabled && cfg.streamerHideOtherNames;
    }

    public static String ownAlias() {
        return StreamerPrivacy.sanitizeAlias(
                AuroraConfig.get().streamerSelfAlias,
                StreamerPrivacy.DEFAULT_SELF_ALIAS);
    }

    public static String ownDisplayName(String realName) {
        return hideOwnName() ? ownAlias() : realName;
    }

    public static boolean isLocalPlayer(UUID uuid) {
        if (uuid == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        if (mc.player != null && uuid.equals(mc.player.getUUID())) return true;
        return mc.getUser() != null && uuid.equals(mc.getUser().getProfileId());
    }

    public static String opponentDisplayName(UUID uuid) {
        AuroraConfig cfg = AuroraConfig.get();
        return StreamerPrivacy.opponentTag(uuid, cfg.streamerOpponentLabel, cfg.streamerOpponentNumbered);
    }

    public static MutableComponent opponentComponent(UUID uuid) {
        AuroraConfig cfg = AuroraConfig.get();
        MutableComponent comp = Component.literal(opponentDisplayName(uuid));
        if (cfg.streamerOpponentColorEnabled) {
            comp.withColor(cfg.streamerOpponentColor & 0x00FFFFFF);
        }
        return comp;
    }

    /**
     * Redacts server addresses from a component (used on ConnectScreen status
     * banners and chat when {@link #hideServerAddress()} is enabled).
     */
    public static Component redactServerComponent(Component input) {
        if (input == null || !hideServerAddress()) return input;
        Minecraft mc = Minecraft.getInstance();
        String serverIp = activeServerIp(mc);
        return redactTree(input, null, null, null, false, null, serverIp, true);
    }

    /**
     * Redacts the local player's username, online opponents' usernames, and
     * server addresses inside a chat/tab component while preserving formatting,
     * colors, and click/hover events.
     */
    public static Component redactComponent(Component input) {
        if (input == null || !isActive()) return input;
        boolean hideSelf = hideOwnName();
        boolean hideOthers = hideOtherNames();
        boolean hideServer = hideServerAddress();
        if (!hideSelf && !hideOthers && !hideServer) return input;

        Minecraft mc = Minecraft.getInstance();
        String selfName = null;
        String profileName = null;
        UUID selfUuid = null;
        if (mc != null) {
            if (mc.getUser() != null) {
                selfName = mc.getUser().getName();
                selfUuid = mc.getUser().getProfileId();
            }
            if (mc.player != null) {
                profileName = mc.player.getGameProfile().name();
                selfUuid = mc.player.getUUID();
            }
        }
        Collection<PlayerInfo> online = (hideOthers && mc != null && mc.getConnection() != null)
                ? mc.getConnection().getOnlinePlayers()
                : null;
        String serverIp = hideServer ? activeServerIp(mc) : null;
        String alias = hideSelf ? ownAlias() : null;

        return redactTree(input, selfName, profileName, alias, hideOthers, online, serverIp, hideServer, selfUuid);
    }

    private static String activeServerIp(Minecraft mc) {
        if (mc == null) return null;
        ServerData data = mc.getCurrentServer();
        return data != null ? data.ip : null;
    }

    private static Component redactTree(Component input, String selfName, String profileName, String alias,
                                        boolean hideOthers, Collection<PlayerInfo> online,
                                        String serverIp, boolean hideServer) {
        return redactTree(input, selfName, profileName, alias, hideOthers, online, serverIp, hideServer, null);
    }

    private static Component redactTree(Component input, String selfName, String profileName, String alias,
                                        boolean hideOthers, Collection<PlayerInfo> online,
                                        String serverIp, boolean hideServer, UUID selfUuid) {
        boolean changed = false;
        MutableComponent rebuilt = null;

        if (input.getContents() instanceof PlainTextContents plain) {
            String raw = plain.text();
            String redacted = redactString(raw, selfName, profileName, alias, hideOthers, online, serverIp, hideServer, selfUuid);
            if (redacted != raw) {
                rebuilt = Component.literal(redacted).setStyle(input.getStyle());
                changed = true;
            }
        } else if (input.getContents() instanceof TranslatableContents tr) {
            Object[] origArgs = tr.getArgs();
            Object[] newArgs = null;
            for (int i = 0; i < origArgs.length; i++) {
                Object arg = origArgs[i];
                if (arg instanceof Component c) {
                    Component rc = redactTree(c, selfName, profileName, alias, hideOthers, online, serverIp, hideServer, selfUuid);
                    if (rc != c) {
                        if (newArgs == null) newArgs = origArgs.clone();
                        newArgs[i] = rc;
                    }
                } else if (arg instanceof String s) {
                    String rs = redactString(s, selfName, profileName, alias, hideOthers, online, serverIp, hideServer, selfUuid);
                    if (rs != s) {
                        if (newArgs == null) newArgs = origArgs.clone();
                        newArgs[i] = rs;
                    }
                }
            }
            if (newArgs != null) {
                rebuilt = Component.translatableWithFallback(tr.getKey(), tr.getFallback(), newArgs)
                        .setStyle(input.getStyle());
                changed = true;
            }
        }

        var siblings = input.getSiblings();
        if (!siblings.isEmpty()) {
            Component[] newSiblings = null;
            for (int i = 0; i < siblings.size(); i++) {
                Component sib = siblings.get(i);
                Component rsib = redactTree(sib, selfName, profileName, alias, hideOthers, online, serverIp, hideServer, selfUuid);
                if (rsib != sib) {
                    if (newSiblings == null) {
                        newSiblings = new Component[siblings.size()];
                        for (int j = 0; j < i; j++) newSiblings[j] = siblings.get(j);
                    }
                    newSiblings[i] = rsib;
                } else if (newSiblings != null) {
                    newSiblings[i] = sib;
                }
            }
            if (newSiblings != null) {
                if (rebuilt == null) {
                    rebuilt = input.plainCopy().setStyle(input.getStyle());
                }
                for (Component s : newSiblings) rebuilt.append(s);
                return rebuilt;
            }
        }

        if (changed) {
            for (Component sib : siblings) rebuilt.append(sib);
            return rebuilt;
        }
        return input;
    }

    private static String redactString(String raw, String selfName, String profileName, String alias,
                                       boolean hideOthers, Collection<PlayerInfo> online,
                                       String serverIp, boolean hideServer, UUID selfUuid) {
        if (raw == null || raw.isEmpty()) return raw;
        String out = raw;
        if (alias != null) {
            if (selfName != null && !selfName.isEmpty()) {
                out = StreamerPrivacy.replaceName(out, selfName, alias);
            }
            if (profileName != null && !profileName.isEmpty() && !profileName.equalsIgnoreCase(selfName)) {
                out = StreamerPrivacy.replaceName(out, profileName, alias);
            }
        }
        if (hideOthers && online != null) {
            for (PlayerInfo info : online) {
                if (info == null || info.getProfile() == null) continue;
                UUID id = info.getProfile().id();
                if (selfUuid != null && selfUuid.equals(id)) continue;
                String name = info.getProfile().name();
                if (name != null && !name.isEmpty()) {
                    out = StreamerPrivacy.replaceName(out, name, opponentDisplayName(id));
                }
            }
        }
        if (hideServer) {
            out = StreamerPrivacy.redactServerAddresses(out, serverIp);
        }
        return out;
    }
}
