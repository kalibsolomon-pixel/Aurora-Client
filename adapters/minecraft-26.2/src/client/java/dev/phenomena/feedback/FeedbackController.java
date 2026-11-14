package dev.phenomena.feedback;

import com.aurora.client.config.AuroraConfig;
import dev.phenomena.PhenomenaClient;
import dev.phenomena.combat.events.CombatEvent;
import dev.phenomena.combat.quality.HitQuality.Grade;
import dev.phenomena.combat.quality.PerfectReach;
import dev.phenomena.combat.tracking.ClientCapture;
import dev.phenomena.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class FeedbackController {
  /** Last visual grade; consumed only by the dormant HUD overlay. */
  public static Grade recent;

  public static long recentAt;
  private static long lastSound, lastChat, lastAttack = -1;

  private FeedbackController() {}

  public static void hit(CombatEvent event, Grade grade) {
    if (!AuroraConfig.get().pulsarEnabled || !PhenomenaClient.isInitialized()) return;
    if (event.sequence() <= lastAttack) return;
    lastAttack = event.sequence();
    var c = PhenomenaClient.config;
    long now = ClientCapture.now();
    boolean perfectReach =
        PerfectReach.eligible(event.attack(), c.perfectReachTarget, c.perfectReachTolerance);
    var style = c.feedback[perfectReach ? 0 : 2];
    if (style.visual) {
      recent = grade;
      recentAt = now;
    }
    if (c.sound
        && c.perfectReachSound
        && perfectReach
        && style.audio
        && now - lastSound > 120_000_000L
        && play(style, c.soundChannel)) lastSound = now;
    if (c.perHitChat && now - lastChat > 500_000_000L) {
      chat(
              Component.literal(
                  "[Phenomena] "
                      + (perfectReach ? "Perfect reach" : "Melee contact")
                      + " / timing is measured separately"));
      lastChat = now;
    }
  }

  /** Settings preview of one category's configured sound, ignoring the hit-sound toggle. */
  public static void preview(int grade) {
    if (!AuroraConfig.get().pulsarEnabled || !PhenomenaClient.isInitialized()) return;
    var c = PhenomenaClient.config;
    play(c.feedback[grade], c.soundChannel);
  }

  /** Local chat delivery also carries disabled-feature guidance, without enabling capture. */
  public static void chat(Component message) {
    var mc = Minecraft.getInstance();
    if (mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(message);
  }

  private static boolean play(Config.Feedback style, Config.SoundChannel channel) {
    var id = Identifier.tryParse(style.sound);
    if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) return false;
    var source =
        switch (channel) {
          case PLAYERS -> SoundSource.PLAYERS;
          case MASTER -> SoundSource.MASTER;
          default -> SoundSource.UI;
        };
    // Non-positional, relative, like vanilla UI sounds.
    Minecraft.getInstance()
        .getSoundManager()
        .play(
            new SimpleSoundInstance(
                id,
                source,
                style.volume,
                style.pitch,
                RandomSource.create(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0,
                0,
                0,
                true));
    return true;
  }
}
