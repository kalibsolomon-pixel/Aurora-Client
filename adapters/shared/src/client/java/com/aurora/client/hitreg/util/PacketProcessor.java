package com.aurora.client.hitreg.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import com.aurora.client.hitreg.HitType;
import com.aurora.client.hitreg.Hitreg;

import java.util.*;

import static com.aurora.client.hitreg.Hitreg.alreadyAnimated;
import static com.aurora.client.hitreg.Hitreg.client;
import static com.aurora.client.hitreg.Hitreg.lastAnimation;
import static com.aurora.client.hitreg.Hitreg.lastAttacked;
import static com.aurora.client.hitreg.Hitreg.lastTarget;
import static com.aurora.client.hitreg.util.MultiVersion.*;

/**
 * From BetterHitreg by Jass (modrinth.com/mod/betterhitreg), integrated into
 * Aurora with the author's permission. Logic is upstream's, moved verbatim —
 * see {@link com.aurora.client.hitreg.BetterHitreg} for the integration notes.
 * Aurora keeps only the custom hitreg (2026-11-15): the silence, legacy-sound
 * and particle toggles upstream read here were removed, so each behaves as
 * its default (off), and the delay and swing/hit counters went with the
 * tracking.
 */
public class PacketProcessor {
    public static long dealtDamageTimestamp;
    public static long tookDamageTimestamp;

    public static boolean processDamage(ClientboundDamageEventPacket packet) {
        //on network thread?
        if (!Minecraft.getInstance().isSameThread()) {
            if (lastTarget == packet.entityId()) {
                dealtDamageTimestamp = System.currentTimeMillis();
            } else if (Hitreg.playerId == packet.entityId()) tookDamageTimestamp = System.currentTimeMillis();
        }

        //on main thread?
        else {
            if (lastTarget == packet.entityId()) {
                //if processing never ran on the network thread, we need to update the timestamp here
                if (System.currentTimeMillis() - dealtDamageTimestamp > 50) dealtDamageTimestamp = System.currentTimeMillis() - 2;

                HitTracker.add(new Animation(dealtDamageTimestamp));
                lastAnimation = dealtDamageTimestamp;
                alreadyAnimated = true;
                processDelayedSounds(true);
            }

            else if (client.player != null && client.player.getId() == packet.entityId()) {
                //if processing never ran on the network thread, we need to update the timestamp here
                if (System.currentTimeMillis() - tookDamageTimestamp > 50) tookDamageTimestamp = System.currentTimeMillis() - 2;

                lastAttacked = tookDamageTimestamp;
                processDelayedSounds(false);
            }
        }

        return true;
    }

    public static boolean processAnimation(ClientboundAnimatePacket packet) {
        if (lastTarget != getAction(packet)) return true;
        boolean withinFight = Hitreg.withinFight;

        //crit particle
        if (packet.getAction() == 4) {
            if (Hitreg.lastHitHandled && withinFight) return false;
        }

        //enchanted particle
        else if (packet.getAction() == 5) {
            if (Hitreg.lastHitHandled && withinFight) return false;
        }

        return true;
    }

    public static boolean processSound(ClientboundSoundPacket packet) {
        Sound sound = new Sound(packet);
        sound.register();

        if (sound.modern || sound.legacy) {
            boolean result = processSound(sound);
            if (!Hitreg.lastHitHandled && !Hitreg.lastSwingHandled) {
                sound.skip = true;
                return true;
            }
            return result;
        }

        return true;
    }

    private static final Queue<Sound> delayedSounds = new LinkedList<>();

    private static void processDelayedSounds(boolean fromYou) {
        if (client.level == null || client.player == null) return;
        Sound sound;
        while ((sound = delayedSounds.poll()) != null) {
            if (sound.skip) continue;
            boolean shouldPlay = !sound.wasRecent() || !fromYou || !Hitreg.lastHitHandled;
            if (shouldPlay) sound.play();
        }
    }

    private static boolean processSound(Sound sound) {
        boolean soundWithinFight = sound.withinFight();

        //if the sound happened far away, leave it alone
        if (!soundWithinFight) return true;

        //Disabling a raised shield is not a sprint/knockback hit. The server can still send the
        //knockback attack sound for it, so suppress just that sound while leaving shield sounds
        //and any other server feedback untouched.
        if (Hitreg.lastAttackWasBlocked && sound.hitType == HitType.KNOCKBACK && sound.couldBeFromYou()) return false;

        //block nodamage sounds because they don't actually register hits so we don't know who they're from
        //nodamage answers a swing rather than a registered hit, so use the swing-time decision
        if (Hitreg.lastSwingHandled && sound.sound.contains("nodamage")) return false;

        //if the sound wasn't from either of you
        boolean fromYou = sound.wasFromYou();
        boolean fromThem = sound.wasFromThem();
        if (!fromYou && !fromThem) {
            //delay knockback sounds because for some reason knockback sounds come before hit registration on most servers
            //don't delay it if its already been processed though, or it would just keep delaying indefinitely
            //if it was from either of you, no need to delay it as it came after hit registration, minemenclub sends it after
            if (!sound.processed && sound.sound.contains("knockback")) {
                sound.processed = true;
                delayedSounds.add(sound);
                return false;
            }
        }

        //block your own hit's sound when the custom hitreg already played it
        if (fromYou && Hitreg.lastHitHandled) return false;

        return true;
    }
}
