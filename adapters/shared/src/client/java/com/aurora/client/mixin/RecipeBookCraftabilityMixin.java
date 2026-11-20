package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Speeds up the recipe book's yes/no check for a single craft. */
@Mixin(StackedItemContents.class)
public abstract class RecipeBookCraftabilityMixin {
    @Shadow @Final private StackedContents<Holder<Item>> raw;

    @Inject(method = "canCraft(Ljava/util/List;Lnet/minecraft/world/entity/player/StackedContents$Output;)Z",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void aurora$checkIngredients(List<? extends StackedContents.IngredientInfo<Holder<Item>>> ingredients,
                                        StackedContents.Output<Holder<Item>> output,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (output != null || !RenderSystem.isOnRenderThread() || !AuroraConfig.get().perfRecipeBookCraftability) return;
        if (raw.getResultUpperBound(ingredients) == 0) {
            cir.setReturnValue(false);
        } else if (ingredients.size() <= 1) {
            cir.setReturnValue(true);
        }
        // With multiple ingredients competing for the same items, vanilla solves the assignment.
    }
}
