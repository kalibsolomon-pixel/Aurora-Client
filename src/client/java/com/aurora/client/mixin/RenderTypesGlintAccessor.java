package com.aurora.client.mixin;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The four vanilla foil types, read past {@code GlintRenderTypesMixin} (whose
 * getters return Aurora's masked types while Alternative Glint is on).
 */
@Mixin(RenderTypes.class)
public interface RenderTypesGlintAccessor {
    @Accessor("GLINT") static RenderType aurora$glint() { throw new AssertionError(); }
    @Accessor("GLINT_TRANSLUCENT") static RenderType aurora$glintTranslucent() { throw new AssertionError(); }
    @Accessor("ENTITY_GLINT") static RenderType aurora$entityGlint() { throw new AssertionError(); }
    @Accessor("ARMOR_ENTITY_GLINT") static RenderType aurora$armorEntityGlint() { throw new AssertionError(); }
}
