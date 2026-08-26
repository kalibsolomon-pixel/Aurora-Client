package you.jass.betterhitreg.utility;

import you.jass.betterhitreg.hitreg.Hit;
import you.jass.betterhitreg.hitreg.HitType;
import you.jass.betterhitreg.hitreg.Hitreg;
import you.jass.betterhitreg.settings.Toggle;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.*;

import static you.jass.betterhitreg.hitreg.Hitreg.client;
import static you.jass.betterhitreg.utility.MultiVersion.message;

public class HitTracker {
    public static final Deque<Sound> sounds = new ArrayDeque<>();
    public static final Deque<Hit> hits = new ArrayDeque<>();
    public static final Deque<Animation> animations = new ArrayDeque<>();

    private static final DecimalFormat df = new DecimalFormat("#.##");

    private static boolean cleared;

    public static void add(Object type) {
        if (type instanceof Sound sound) sounds.addLast(sound);
        else if (type instanceof Hit hit) hits.addLast(hit);
        else if (type instanceof Animation animation) animations.addLast(animation);
        cleared = false;
    }

    public static void process() {
        long now = System.currentTimeMillis();
        if (hits.isEmpty()) return;
        Hit oldestHit = hits.peekFirst();
        if (oldestHit == null) return;

        //remove all sounds older than the oldest hit
        while (!sounds.isEmpty() && sounds.peekFirst().timestamp < oldestHit.timestamp) sounds.pollFirst();

        while (!hits.isEmpty()) {
            Hit hit = hits.peekFirst();
            if (hit == null) break;

            //only process hits older than 500ms
            if (hit.timestamp > now - 500) break;

            long maximumTimestamp = hit.timestamp + 500;
            long animationTimestamp = 0;

            //remove all animations older than the hit
            while (!animations.isEmpty() && animations.peekFirst().timestamp < hit.timestamp) animations.pollFirst();

            //is the oldest animation within 500ms of the hit, if so, mark it and remove it
            if (!animations.isEmpty() && animations.peekFirst().timestamp <= maximumTimestamp) {
                hit.wasAnimated = true;
                animationTimestamp = animations.peekFirst().timestamp;
                animations.pollFirst();
            }

            //only check for server inconsistencies if the hit didn't ghost
            if (hit.wasAnimated) {
                for (Sound sound : sounds) {
                    //is the sound too early
                    if (sound.timestamp < hit.timestamp) continue;

                    //is the sound too late
                    if (sound.timestamp > maximumTimestamp) break;

                    //is it within 50ms of the hits animation
                    if (hit.wasAnimated && sound.distanceFromTimestamp(animationTimestamp) <= 50) hit.potentialServerTypes.add(sound.hitType);
                }

                //was the server right based on the sound they played
                if (!hit.potentialServerTypes.isEmpty()) {
                    for (HitType type : hit.potentialServerTypes) {
                        if (type == hit.type) {
                            hit.wasServerRight = true;
                            break;
                        }
                    }
                }
            } else {
                //the hit ghosted so just exempt
                hit.wasServerRight = true;
            }

            //alert ghosts
            if (!hit.wasNewTarget && !hit.wasHitByAnother && !hit.wasInvisible && !hit.wasBlocked && !Hitreg.wasGhosted) {
                Hitreg.last100Regs.addGhost(!hit.wasAnimated);
                if (Toggle.ALERT_GHOSTS.toggled() && !hit.wasAnimated) {
                    message("§7server §rghosted §7your §f" + hit.type.toString().toLowerCase() + " §7hit (cooldown: " + df.format(hit.cooldown) + ")", "/hitreg alertGhosts");
                    if (hit.hadShield) message("§7this may have been due to shield desync", "hitreg alertGhosts");
                }
            }


            //alert inconsistencies
            if (hit.type == HitType.KNOCKBACK || hit.type == HitType.CRITICAL) {
                Hitreg.last100Regs.addInconsistency(!hit.wasServerRight);
                if (Toggle.ALERT_INCONSISTENCIES.toggled() && !hit.wasServerRight) message("§7server §rmisplaced §7your §f" + hit.type.toString().toLowerCase() + " §7hit (cooldown: " + df.format(hit.cooldown) + ")", "/hitreg alertInconsistencies");
            }

            //for safe regs
            Hitreg.wasGhosted = !hit.wasAnimated;
            if (hit.wasAnimated) Hitreg.lastNonGhost = hit.timestamp;

            //finished processing this hit, remove it
            hits.pollFirst();
        }
    }

    public static void clear() {
        if (cleared) return;
        if (!hits.isEmpty()) hits.clear();
        if (!animations.isEmpty()) animations.clear();
        if (!sounds.isEmpty()) sounds.clear();
        cleared = true;
    }
}