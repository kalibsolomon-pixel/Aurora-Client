package com.aurora.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which Aurora feature ids have a custom PNG icon shipped under
 * {@code assets/aurora/textures/gui/module_icons/&lt;id&gt;.png}.
 *
 * <p>Populated at resource-pack (re)load time by scanning the Minecraft
 * resource manager. {@link FeatureTile} consults {@link #has(String)} to
 * decide whether to blit the texture or fall back to the legacy Unicode
 * glyph from {@link FeatureIcons}, which makes adding new icons a pure
 * drop-in operation — no code change required for each new PNG.
 *
 * <p>Source PNGs are expected at any resolution; 1536&nbsp;×&nbsp;1536 is
 * the supplied authoring size. They render through Minecraft's
 * {@code GUI_TEXTURED} pipeline at whatever target size the tile picks.
 */
public final class ModuleIconRegistry implements ResourceManagerReloadListener {

    private static final String NAMESPACE = "aurora";
    private static final String DIR = "textures/gui/module_icons";
    private static final String EXT = ".png";

    private static final Set<String> AVAILABLE = new HashSet<>();
    /** Texture-ids we've already pushed through {@link TextureManager} this session. */
    private static final Set<String> REGISTERED = new HashSet<>();

    /**
     * True when {@code <id>.png} exists in the active resource pack. Also
     * lazily registers the texture with the {@link TextureManager} on
     * first hit so the blur=true sampler kicks in before the first blit.
     * Lazy registration avoids the timing issue where calling
     * {@code TextureManager.registerAndLoad} during the resource reload
     * pass would miss the new resource manager.
     */
    public static boolean has(String id) {
        if (id == null || !AVAILABLE.contains(id)) return false;
        if (REGISTERED.add(id)) {
            try {
                Minecraft.getInstance().getTextureManager().registerAndLoad(
                        locationFor(id), new SharpIconTexture(locationFor(id)));
            } catch (Throwable t) {
                // Roll the id back out of REGISTERED so a later reload
                // can retry; also surface in logs for diagnosis.
                REGISTERED.remove(id);
                org.slf4j.LoggerFactory.getLogger("Aurora")
                        .warn("Failed to register module icon {}: {}", id, t.toString());
            }
        }
        return true;
    }

    /** Identifier the tile blits — {@code aurora:textures/gui/module_icons/&lt;id&gt;.png}. */
    public static Identifier locationFor(String id) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, DIR + "/" + id + EXT);
    }

    @Override
    public void onResourceManagerReload(ResourceManager rm) {
        AVAILABLE.clear();
        // A new resource pack may ship a different PNG for the same id —
        // drop the registration cache so the next render re-registers
        // through TextureManager and the new bytes are picked up.
        REGISTERED.clear();
        // Limit the scan to our own namespace + directory so we don't
        // pay for the entire pack's texture index.
        var found = rm.listResources(DIR,
                rl -> rl.getNamespace().equals(NAMESPACE) && rl.getPath().endsWith(EXT));
        for (Identifier rl : found.keySet()) {
            String path = rl.getPath();
            int slash = path.lastIndexOf('/');
            int dot = path.lastIndexOf('.');
            if (slash < 0 || dot <= slash) continue;
            AVAILABLE.add(path.substring(slash + 1, dot));
        }
    }

    /**
     * {@link ReloadableTexture} that mirrors {@code SimpleTexture}'s load
     * path but rewrites the {@link TextureMetadataSection} to disable blur
     * (i.e. {@code FilterMode.NEAREST}). Mipmap strategy and clamp are
     * preserved from whatever the resource pack's optional {@code .mcmeta}
     * specified, so packs can still override mipmap behavior if needed.
     */
    private static final class SharpIconTexture extends ReloadableTexture {
        SharpIconTexture(Identifier id) { super(id); }

        @Override
        public TextureContents loadContents(ResourceManager rm) throws IOException {
            TextureContents base = TextureContents.load(rm, resourceId());
            // base.metadata() is nullable — present only when an .mcmeta
            // ships next to the PNG. Fall back to vanilla defaults
            // (no clamp, AUTO mipmap, no alpha-cutoff bias) and stamp
            // blur=false so the GPU sampler uses NEAREST filtering.
            TextureMetadataSection src = base.metadata();
            boolean clamp = src != null && src.clamp();
            MipmapStrategy mip = src != null ? src.mipmapStrategy() : MipmapStrategy.AUTO;
            float bias = src != null ? src.alphaCutoffBias() : 0f;
            return new TextureContents(base.image(),
                    new TextureMetadataSection(false, clamp, mip, bias));
        }
    }
}
