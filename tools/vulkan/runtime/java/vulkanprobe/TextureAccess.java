package vulkanprobe.mixin;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;
@Mixin(TextureManager.class)
public interface TextureAccess {
    @Accessor("byPath") Map<Identifier, AbstractTexture> aurora$allTextures();
}
