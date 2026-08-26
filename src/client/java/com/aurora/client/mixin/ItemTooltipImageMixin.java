package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.preview.AuroraContainerTooltipData;
import com.aurora.client.hud.preview.EnderChestSnapshot;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.EnderChestBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Injects Aurora's rich container preview into {@link Item#getTooltipImage}
 * for shulker boxes and the local player's ender chest.
 *
 * <p>Shulker contents come from the stack's {@link DataComponents#CONTAINER}
 * component. Ender chest contents are mirrored from the open ender chest
 * screen via {@link EnderChestSnapshot} (the client-side
 * {@code PlayerEnderChestContainer} is not populated by vanilla on 1.21.x).
 *
 * <p>Bundles are unaffected because {@code BundleItem} overrides
 * {@code getTooltipImage} and Java polymorphism dispatches to that
 * override before this mixin's HEAD inject runs.
 */
@Mixin(Item.class)
public class ItemTooltipImageMixin {

    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    private void aurora$injectContainerPreview(ItemStack stack,
                                               CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.containerPreviewEnabled) return;

        Item self = (Item) (Object) this;

        // ---- Ender Chest (snapshot of the local screen's contents) ----
        if (cfg.containerPreviewEnderChest && self instanceof BlockItem bi
                && bi.getBlock() instanceof EnderChestBlock) {
            if (EnderChestSnapshot.hasData()) {
                cir.setReturnValue(Optional.of(new AuroraContainerTooltipData(
                        EnderChestSnapshot.view(), 9, 3,
                        cfg.containerPreviewShowCounts)));
            }
            return;
        }

        // ---- Shulker Box ----
        if (!cfg.containerPreviewShulker) return;
        if (!(self instanceof BlockItem bi2) || !(bi2.getBlock() instanceof ShulkerBoxBlock)) return;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null || contents == ItemContainerContents.EMPTY) return;

        NonNullList<ItemStack> buf = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.copyInto(buf);
        boolean anyNonEmpty = false;
        for (ItemStack s : buf) if (!s.isEmpty()) { anyNonEmpty = true; break; }
        if (!anyNonEmpty) return;

        cir.setReturnValue(Optional.of(new AuroraContainerTooltipData(
                new ArrayList<>(buf), 9, 3,
                cfg.containerPreviewShowCounts)));
    }
}
