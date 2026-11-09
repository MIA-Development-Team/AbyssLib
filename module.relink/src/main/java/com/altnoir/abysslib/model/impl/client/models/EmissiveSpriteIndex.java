package com.altnoir.abysslib.model.impl.client.models;

import com.altnoir.abysslib.client.ALClientConfig;
import com.altnoir.abysslib.model.api.client.DynamicEmissive;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

public final class EmissiveSpriteIndex {
    private static volatile Overlays overlays = new Overlays(Map.of(), Map.of());

    private EmissiveSpriteIndex() {
    }

    public static void update(Identifier atlas, Map<Identifier, TextureAtlasSprite> textures) {
        Overlays previous = overlays;
        String suffix = ALClientConfig.overlayEnabled() ? ALClientConfig.emissiveSuffix : "";
        overlays = new Overlays(
                replaceAtlas(previous.regular(), atlas, textures, suffix, true),
                replaceAtlas(previous.dynamic(), atlas, textures, DynamicEmissive.TEXTURE_SUFFIX, false));
    }

    static Map<TextureAtlasSprite, TextureAtlasSprite> sprites(boolean dynamic) {
        Overlays current = overlays;
        return dynamic ? current.dynamic() : current.regular();
    }

    public static Function<TextureAtlasSprite, TextureAtlasSprite> bakingLookup(
            Function<Identifier, TextureAtlasSprite> textureGetter) {
        String suffix = ALClientConfig.emissiveSuffix;
        Map<TextureAtlasSprite, TextureAtlasSprite> pairs = new IdentityHashMap<>();
        // Model baking uses the pending atlas, which may not have been uploaded yet.
        return base -> {
            if (pairs.containsKey(base)) return pairs.get(base);
            TextureAtlasSprite overlay = findOverlay(base.contents().name(), suffix, textureGetter);
            pairs.put(base, overlay);
            return overlay;
        };
    }

    private static Map<TextureAtlasSprite, TextureAtlasSprite> replaceAtlas(
            Map<TextureAtlasSprite, TextureAtlasSprite> previous, Identifier atlas,
            Map<Identifier, TextureAtlasSprite> textures, String suffix, boolean filterExcluded) {
        Map<TextureAtlasSprite, TextureAtlasSprite> result = new IdentityHashMap<>();
        previous.forEach((base, overlay) -> {
            if (!base.atlasLocation().equals(atlas)) result.put(base, overlay);
        });
        if (!suffix.isEmpty()) {
            textures.forEach((id, overlay) -> {
                String path = id.getPath();
                if (!path.endsWith(suffix) || path.length() == suffix.length()) return;
                Identifier baseId = id.withPath(path.substring(0, path.length() - suffix.length()));
                TextureAtlasSprite base = textures.get(baseId);
                if (base != null && (!filterExcluded || !ALClientConfig.isExcluded(baseId))) result.put(base, overlay);
            });
        }
        return result.isEmpty() ? Map.of() : Collections.unmodifiableMap(result);
    }

    @Nullable
    private static TextureAtlasSprite findOverlay(Identifier base, String suffix,
                                                  Function<Identifier, TextureAtlasSprite> textureGetter) {
        Identifier overlayId = Identifier.tryBuild(base.getNamespace(), base.getPath() + suffix);
        if (overlayId == null) return null;
        TextureAtlasSprite overlay = textureGetter.apply(overlayId);
        if (overlay == null || !overlay.contents().name().equals(overlayId)) return null;
        return ALClientConfig.isExcluded(base) ? null : overlay;
    }

    private record Overlays(Map<TextureAtlasSprite, TextureAtlasSprite> regular,
                            Map<TextureAtlasSprite, TextureAtlasSprite> dynamic) {
    }
}
