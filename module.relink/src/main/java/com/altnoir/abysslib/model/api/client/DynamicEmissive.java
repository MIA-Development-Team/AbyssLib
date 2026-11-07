package com.altnoir.abysslib.model.api.client;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side registration for position-dependent fullbright texture overlays. */
public final class DynamicEmissive {
    public static final String TEXTURE_SUFFIX = "_active_e";

    @FunctionalInterface
    public interface Provider {
        /** Called while building a chunk; implementations must be safe to call from worker threads. */
        boolean isActive(BlockAndTintGetter level, BlockPos pos, BlockState state);
    }

    private static final Map<Identifier, Provider> PROVIDERS = new ConcurrentHashMap<>();

    private DynamicEmissive() {}

    /** Register before model baking; request a chunk rebuild when the result changes. */
    public static void register(Identifier blockId, Provider provider) {
        if (PROVIDERS.putIfAbsent(blockId, provider) != null) {
            throw new IllegalArgumentException("Dynamic emissive provider already registered for " + blockId);
        }
    }

    @Nullable
    public static Provider provider(Identifier blockId) {
        return PROVIDERS.get(blockId);
    }
}
