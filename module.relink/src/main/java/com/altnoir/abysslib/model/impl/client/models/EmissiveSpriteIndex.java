package com.altnoir.abysslib.model.impl.client.models;

import com.altnoir.abysslib.client.ALClientConfig;
import com.altnoir.abysslib.model.api.client.DynamicEmissive;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class EmissiveSpriteIndex {
    private final String suffix;
    private final List<String> excluded;
    private volatile Overlays overlays = new Overlays(Map.of(), Map.of());

    public EmissiveSpriteIndex(boolean regularEnabled) {
        this.suffix = regularEnabled ? ALClientConfig.emissiveSuffix : "";
        this.excluded = ALClientConfig.emissiveExclude;
    }

    public void bind(Map<ResourceLocation, TextureAtlasSprite> textures) {
        this.overlays = new Overlays(
                findPairs(textures, this.suffix, this.excluded),
                findPairs(textures, DynamicEmissive.TEXTURE_SUFFIX, List.of()));
    }

    Map<TextureAtlasSprite, TextureAtlasSprite> sprites(boolean dynamic) {
        Overlays current = this.overlays;
        return dynamic ? current.dynamic() : current.regular();
    }

    private static Map<TextureAtlasSprite, TextureAtlasSprite> findPairs(
            Map<ResourceLocation, TextureAtlasSprite> textures, String suffix, List<String> excluded) {
        if (suffix.isEmpty()) return Map.of();

        Map<TextureAtlasSprite, TextureAtlasSprite> result = new IdentityHashMap<>();
        textures.forEach((id, overlay) -> {
            String path = id.getPath();
            if (!path.endsWith(suffix) || path.length() == suffix.length()) return;
            ResourceLocation baseId = id.withPath(path.substring(0, path.length() - suffix.length()));
            TextureAtlasSprite base = textures.get(baseId);
            if (base != null && !isExcluded(baseId, excluded)) result.put(base, overlay);
        });
        return result.isEmpty() ? Map.of() : Collections.unmodifiableMap(result);
    }

    private static boolean isExcluded(ResourceLocation id, List<String> excluded) {
        if (excluded.isEmpty()) return false;
        String name = id.toString();
        for (String prefix : excluded) {
            if (!prefix.isEmpty() && name.startsWith(prefix)) return true;
        }
        return false;
    }

    private record Overlays(Map<TextureAtlasSprite, TextureAtlasSprite> regular,
                            Map<TextureAtlasSprite, TextureAtlasSprite> dynamic) {
    }
}
