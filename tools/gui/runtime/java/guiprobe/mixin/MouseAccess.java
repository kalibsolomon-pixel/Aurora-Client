package guiprobe.mixin;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(MouseHandler.class)
public interface MouseAccess {
    @Accessor("xpos") void gui$setX(double x);
    @Accessor("ypos") void gui$setY(double y);
}
