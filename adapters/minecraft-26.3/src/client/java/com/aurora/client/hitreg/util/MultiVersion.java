package com.aurora.client.hitreg.util;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;

import static com.aurora.client.hitreg.Hitreg.client;

/**
 * Version-gate shim from BetterHitreg by Jass (integrated with permission).
 * Upstream this file carried one commented block per supported Minecraft
 * version; Aurora targets 1.21.11 only, so the live 1.21.11 branch of each
 * helper is kept and the drawing half (only ever used by the retired
 * hand-drawn menu) plus the clickable chat formatter were removed with it.
 * The remaining helpers are gameplay logic — {@link #isMovingFast()} in
 * particular is the 1.21.2+ sweep predicate (vanilla parity: 2.5× movement
 * speed) — and are unchanged.
 */
public class MultiVersion {
    public static Vec3 getLerpedPosition(Entity entity) {
        if (client.level == null || entity == null) return Vec3.ZERO;

        //version 1.21.2+
        return entity.getPosition(client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }

    public static Vec3 getBasePosition(Entity entity) {
        if (client.level == null || entity == null) return Vec3.ZERO;

        return entity.position();
    }

    public static String getSoundPath(SoundInstance sound) {
        //version 1.21.11+
        return sound.getIdentifier() == null ? null : sound.getIdentifier().getPath();
    }

    public static boolean isOnGround(Entity entity) {
        //version 1.20+
        return entity.onGround();
    }

    public static long getLevelTime(Entity entity) {
        //version 1.20+
        return entity.level().getGameTime();
    }

    public static boolean isMovingFast() {
        //vanilla doesn't sweep when moving faster than your movement speed, 1.21.2 changed the check to compare actual movement against 2.5x

        //version 1.21.2+
        return client.player.getKnownMovement().horizontalDistanceSqr() >= Mth.square(client.player.getSpeed() * 2.5);
    }

    public static void playParticles(String type, Entity entity) {
        if (client.level == null || entity == null) return;
        Vec3 position = getLerpedPosition(entity);
        for (int i = 0; i < 20; i++) {
            double x = Math.random() - 0.5;
            double y = Math.random() - 0.5;
            double z = Math.random() - 0.5;
            Vec3 direction = new Vec3(x, y, z).normalize();

            SimpleParticleType particle = ParticleTypes.ASH;

            if (type.equals("CRIT")) particle = ParticleTypes.CRIT;
            else if (type.equals("ENCHANTED_HIT")) particle = ParticleTypes.ENCHANTED_HIT;

            client.level.addParticle(
            particle,
            position.x + x,
            position.y + (entity.getBbHeight() / 2) + y,
            position.z + z,
            direction.x * 0.5,
            direction.y * 0.5,
            direction.z * 0.5);
        }
    }

    public static int getAction(ClientboundAnimatePacket packet) {
        return packet.getId();
    }

    public static boolean hasSharpness() {
        if (client.player.getMainHandItem().isEnchanted()) {
            //version 1.20.5+
            for (Holder<Enchantment> enchantment : client.player.getMainHandItem().getEnchantments().keySet()) {
                if (enchantment.getRegisteredName().equalsIgnoreCase("minecraft:sharpness")) {
                    return true;
                }
            }
        }

        return false;
    }
}
