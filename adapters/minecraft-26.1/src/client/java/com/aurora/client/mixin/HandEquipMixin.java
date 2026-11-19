package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Animations, first-person equip motion (26.1): No Use Dip, No Cooldown Dip
 * and Slot-Only Re-equip. Only the drawn hand height and which stack is drawn
 * change; inventory, use results and the attack cooldown itself are vanilla's.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class HandEquipMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private ItemStack mainHandItem;

    /** The hotbar slot whose stack is drawn in the main hand; -1 before the first tick. */
    @Unique private int aurora$visibleSlot = -1;

    /** Vanilla drops the hand to the bottom after a successful use, then raises it again. */
    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void aurora$noUseDip(InteractionHand hand, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.smoothAnimationsEnabled && cfg.animNoUseDip) ci.cancel();
    }

    /** The main hand's resting height follows the attack cooldown; read it as recovered. */
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
    private float aurora$noCooldownDip(float swapScale) {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.smoothAnimationsEnabled && cfg.animNoCooldownDip ? 1.0F : swapScale;
    }

    /**
     * Main hand only (the first call): a hotbar slot change re-equips, even
     * between two identical stacks; a change to the same slot's stack that
     * keeps its item (count, durability, components) is drawn at once. A
     * different item in the same slot keeps vanilla's decision, which also
     * honours items whose model asks for no swap animation.
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;shouldInstantlyReplaceVisibleItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean aurora$slotOnlyReequip(ItemInHandRenderer self, ItemStack visible, ItemStack next,
                                           Operation<Boolean> original) {
        AuroraConfig cfg = AuroraConfig.get();
        LocalPlayer player = minecraft.player;
        if (!cfg.smoothAnimationsEnabled || cfg.animReequip != AuroraConfig.ReequipMode.SLOT_ONLY || player == null) {
            return original.call(self, visible, next);
        }
        int slot = player.getInventory().getSelectedSlot();
        if (aurora$visibleSlot != -1 && slot != aurora$visibleSlot) return false;
        if (ItemStack.isSameItem(visible, next)) return true;
        return original.call(self, visible, next);
    }

    /** Records which slot the drawn stack belongs to once the drawn stack is the held one. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void aurora$trackVisibleSlot(CallbackInfo ci) {
        LocalPlayer player = minecraft.player;
        if (player != null && mainHandItem == player.getMainHandItem()) aurora$visibleSlot = player.getInventory().getSelectedSlot();
    }
}
