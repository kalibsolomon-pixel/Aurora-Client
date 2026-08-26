package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Suppresses vanilla's auto-generated "N x ItemName" tooltip lines emitted
 * by {@link ItemContainerContents#addToTooltip} for shulker boxes when the
 * Aurora "Show Stack Counts" toggle is off.
 *
 * <p>The Aurora preview grid replaces those lines visually, so hiding the
 * vanilla list keeps the tooltip compact while still showing the contents
 * via the grid icons.
 */
@Mixin(ItemContainerContents.class)
public class ItemContainerContentsTooltipMixin {

    @Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
    private void aurora$suppressVanillaCounts(Item.TooltipContext ctx,
                                              Consumer<Component> out,
                                              TooltipFlag flag,
                                              DataComponentGetter getter,
                                              CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.containerPreviewEnabled
                && cfg.containerPreviewShulker
                && !cfg.containerPreviewShowCounts) {
            ci.cancel();
        }
    }
}
