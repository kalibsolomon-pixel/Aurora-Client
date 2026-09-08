package com.aurora.client.mixin.hitreg;

//version 1.19.4
//import net.minecraft.client.renderer.LightTexture;
//import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;

//import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
//import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
//import org.joml.Matrix4fc;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.aurora.client.hitreg.Hitreg;
import com.aurora.client.hitreg.settings.Toggle;

/**
 * From BetterHitreg by Jass (modrinth.com/mod/betterhitreg), integrated into
 * Aurora with the author's permission. Logic is upstream's, moved verbatim —
 * see {@link com.aurora.client.hitreg.BetterHitreg} for the integration notes.
 */
@Mixin(LevelRenderer.class)
public class ChunkMixin {
    //version 26+
    @Shadow
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    //version 26+
    @Inject(method = "prepareChunkRenders", at = @At("HEAD"))
    private void onPrepareChunkRenders(Matrix4fc modelViewMatrix, double x, double y, double z, CallbackInfoReturnable<ChunkSectionsToRender> cir) {
        if (Toggle.VOID_WORLD.toggled() && Hitreg.client.player != null && Hitreg.client.level != null) visibleSections.clear();
    }

    //version 1.19.4
//    @Shadow
//    private ObjectArrayList renderChunksInFrustum;

    //version 1.19.4
//    @Inject(method = "applyFrustum", at = @At("TAIL"))
//    private void onApplyFrustum(Frustum frustum, CallbackInfo ci) {
//        if (Toggle.VOID_WORLD.toggled() && Hitreg.client.player != null && Hitreg.client.level != null) {
//            renderChunksInFrustum.clear();
//        }
//    }
}