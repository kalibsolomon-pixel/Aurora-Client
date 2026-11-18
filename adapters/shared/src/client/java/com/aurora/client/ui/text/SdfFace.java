package com.aurora.client.ui.text;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;

final class SdfFace implements AutoCloseable {
    private final ResourceManager resources;
    private final Identifier file;
    private final SdfAtlas atlas;
    private final Int2ObjectOpenHashMap<SdfAtlas.Cell> cells = new Int2ObjectOpenHashMap<>();
    private SdfStrike strike;

    SdfFace(ResourceManager resources, Identifier file, SdfAtlas atlas) {
        this.resources = resources;
        this.file = file.withPrefix("font/");
        this.atlas = atlas;
    }

    boolean cached(int codePoint) { return cells.containsKey(codePoint); }

    SdfAtlas.Cell cell(int codePoint) throws IOException {
        if (cells.containsKey(codePoint)) return cells.get(codePoint);
        if (strike == null) {
            try (var input = resources.open(file)) {
                strike = SdfStrike.open(input);
            }
        }
        SdfStrike.Bitmap bitmap = strike.render(codePoint);
        SdfAtlas.Cell cell = bitmap == null ? null : atlas.add(bitmap);
        cells.put(codePoint, cell);
        return cell;
    }

    @Override public void close() {
        if (strike != null) strike.close();
    }
}
