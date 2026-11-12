package com.aurora.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The client's font manager (a private field), for {@link com.aurora.client.ui.util.AuroraFontQuality}. */
@Mixin(Minecraft.class)
public interface MinecraftFontManagerAccessor {
    @Accessor("fontManager")
    FontManager aurora$fontManager();
}
