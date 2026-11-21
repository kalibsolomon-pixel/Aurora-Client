package com.aurora.client.mixin;

import com.aurora.client.util.GuiEdgeOnQuad;
import com.aurora.client.util.GuiFlatFaces;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * GUI Flat Faces (ADR 0009): a quad's {@link GuiFlatFaces#zeroAreaUnder}
 * table, computed from its corners the first time a GUI atlas drawing asks.
 * Harmless alone: nothing else reads it.
 */
@Mixin(BakedQuad.class)
public abstract class BakedQuadEdgeOnMixin implements GuiEdgeOnQuad {
    @Shadow @Final private Vector3fc position0;
    @Shadow @Final private Vector3fc position1;
    @Shadow @Final private Vector3fc position2;
    @Shadow @Final private Vector3fc position3;

    /** 0 until computed; the corners are final, so the table never changes. */
    @Unique private int aurora$zeroAreaUnder;

    @Override
    public int aurora$zeroAreaUnder() {
        int table = aurora$zeroAreaUnder;
        if (table == 0) {
            table = GuiFlatFaces.zeroAreaUnder(position0, position1, position2, position3) | GuiFlatFaces.COMPUTED;
            aurora$zeroAreaUnder = table;
        }
        return table;
    }
}
