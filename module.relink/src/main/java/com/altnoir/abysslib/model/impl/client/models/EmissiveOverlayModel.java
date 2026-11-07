package com.altnoir.abysslib.model.impl.client.models;

import com.altnoir.abysslib.client.ALClientConfig;
import com.altnoir.abysslib.model.api.client.DynamicEmissive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class EmissiveOverlayModel extends DelegateBlockStateModel {
    private final Map<Identifier, Optional<TextureAtlasSprite>> sprites = new ConcurrentHashMap<>();
    @Nullable
    private final DynamicEmissive.Provider dynamicProvider;

    public EmissiveOverlayModel(BlockStateModel delegate) {
        super(delegate);
        this.dynamicProvider = null;
    }

    public EmissiveOverlayModel(BlockStateModel delegate, DynamicEmissive.Provider dynamicProvider) {
        super(delegate);
        this.dynamicProvider = dynamicProvider;
    }

    @Override
    @Deprecated
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        if (this.dynamicProvider != null) {
            this.delegate.collectParts(random, parts);
        } else {
            collectParts(BlockAndTintGetter.EMPTY, BlockPos.ZERO, Blocks.AIR.defaultBlockState(), random, parts);
        }
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        if (this.dynamicProvider != null && !this.dynamicProvider.isActive(level, pos, state)) {
            this.delegate.collectParts(level, pos, state, random, parts);
            return;
        }
        List<BlockStateModelPart> original = new ArrayList<>();
        this.delegate.collectParts(level, pos, state, random, original);
        original.forEach(part -> parts.add(new OverlayPart(part, this)));
    }

    private List<BakedQuad> addOverlay(List<BakedQuad> base) {
        if ((this.dynamicProvider == null && !ALClientConfig.overlayEnabled()) || base.isEmpty()) return base;
        List<BakedQuad> result = null;
        for (BakedQuad quad : base) {
            TextureAtlasSprite sprite = overlaySprite(quad.materialInfo().sprite());
            if (sprite == null) continue;
            if (result == null) result = new ArrayList<>(base);
            result.add(EmissiveQuads.overlay(quad, sprite));
        }
        return result == null ? base : result;
    }

    @Nullable
    private TextureAtlasSprite overlaySprite(TextureAtlasSprite base) {
        String suffix = this.dynamicProvider == null ? ALClientConfig.emissiveSuffix : DynamicEmissive.TEXTURE_SUFFIX;
        if (suffix.isEmpty()) return null;
        Identifier baseId = base.contents().name();
        if (this.dynamicProvider == null && ALClientConfig.isExcluded(baseId)) return null;
        Identifier overlayId = baseId.withSuffix(suffix);
        return this.sprites.computeIfAbsent(overlayId, id -> {
            TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager()
                    .getAtlasOrThrow(base.atlasLocation()).getSprite(id);
            return sprite != null && sprite.contents().name().equals(id) ? Optional.of(sprite) : Optional.empty();
        }).orElse(null);
    }

    private record OverlayPart(BlockStateModelPart delegate,
                               EmissiveOverlayModel owner) implements BlockStateModelPart {
        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return this.owner.addOverlay(this.delegate.getQuads(direction));
        }

        @Override
        @Deprecated
        public boolean useAmbientOcclusion() {
            return this.delegate.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return this.delegate.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return this.delegate.materialFlags();
        }
    }
}
