package com.altnoir.abysslib.model.impl.client.models;

import com.altnoir.abysslib.client.ALClientConfig;
import com.altnoir.abysslib.model.api.client.DynamicEmissive;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class EmissiveOverlayModel extends DelegateBlockStateModel {
    @Nullable
    private final BlockStateModel bakedOverlay;
    @Nullable
    private final DynamicEmissive.Provider dynamicProvider;

    public EmissiveOverlayModel(BlockStateModel delegate, DynamicEmissive.Provider dynamicProvider) {
        this(delegate, null, dynamicProvider);
    }

    private EmissiveOverlayModel(BlockStateModel delegate, @Nullable BlockStateModel bakedOverlay,
                                 @Nullable DynamicEmissive.Provider dynamicProvider) {
        super(delegate);
        this.bakedOverlay = bakedOverlay;
        this.dynamicProvider = dynamicProvider;
    }

    public static BlockStateModel bake(BlockStateModel model, Function<TextureAtlasSprite, TextureAtlasSprite> overlayLookup) {
        if (model.getClass() != SingleVariant.class) return new EmissiveOverlayModel(model, null, null);
        List<BlockStateModelPart> parts = new ArrayList<>(1);
        model.collectParts(RandomSource.create(0), parts);
        if (!(parts.getFirst() instanceof SimpleModelWrapper part)) return new EmissiveOverlayModel(model, null, null);

        boolean hasOverlay = false;
        for (BakedQuad quad : part.quads().getAll()) {
            TextureAtlasSprite sprite = quad.materialInfo().sprite();
            if (!TextureAtlas.LOCATION_BLOCKS.equals(sprite.atlasLocation())) return new EmissiveOverlayModel(model, null, null);
            hasOverlay |= overlayLookup.apply(sprite) != null;
        }
        if (!hasOverlay) return model;

        QuadCollection.Builder quads = new QuadCollection.Builder().addAll(part.quads());
        addBakedOverlays(quads, null, part.getQuads(null), overlayLookup);
        for (Direction direction : Direction.values()) {
            addBakedOverlays(quads, direction, part.getQuads(direction), overlayLookup);
        }
        BlockStateModel overlay = new SingleVariant(new SimpleModelWrapper(
                quads.build(), part.useAmbientOcclusion(), part.particleMaterial()));
        return new EmissiveOverlayModel(model, overlay, null);
    }

    private static void addBakedOverlays(QuadCollection.Builder builder, @Nullable Direction direction,
                                         List<BakedQuad> base, Function<TextureAtlasSprite, TextureAtlasSprite> overlayLookup) {
        for (BakedQuad quad : base) {
            TextureAtlasSprite sprite = overlayLookup.apply(quad.materialInfo().sprite());
            if (sprite == null) continue;
            BakedQuad overlay = EmissiveQuads.overlay(quad, sprite);
            if (direction == null) builder.addUnculledFace(overlay);
            else builder.addCulledFace(direction, overlay);
        }
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
        boolean dynamic = this.dynamicProvider != null;
        if (!dynamic && !ALClientConfig.overlayEnabled()) {
            this.delegate.collectParts(level, pos, state, random, parts);
            return;
        }
        if (this.bakedOverlay != null) {
            this.bakedOverlay.collectParts(level, pos, state, random, parts);
            return;
        }
        var sprites = EmissiveSpriteIndex.sprites(dynamic);
        if (sprites.isEmpty() || dynamic && !this.dynamicProvider.isActive(level, pos, state)) {
            this.delegate.collectParts(level, pos, state, random, parts);
            return;
        }
        List<BlockStateModelPart> original = new ArrayList<>();
        this.delegate.collectParts(level, pos, state, random, original);
        for (BlockStateModelPart part : original) {
            parts.add(new OverlayPart(part, sprites));
        }
    }

    @Override
    @Nullable
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        if (this.bakedOverlay == null) return null;
        BlockStateModel model = ALClientConfig.overlayEnabled() ? this.bakedOverlay : this.delegate;
        return model.createGeometryKey(level, pos, state, random);
    }

    @Override
    @Deprecated
    public int materialFlags() {
        BlockStateModel model = this.bakedOverlay != null && ALClientConfig.overlayEnabled() ? this.bakedOverlay : this.delegate;
        return model.materialFlags();
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockStateModel model = this.bakedOverlay != null && ALClientConfig.overlayEnabled() ? this.bakedOverlay : this.delegate;
        return model.materialFlags(level, pos, state);
    }

    private record OverlayPart(BlockStateModelPart delegate,
                               Map<TextureAtlasSprite, TextureAtlasSprite> sprites) implements BlockStateModelPart {
        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            List<BakedQuad> base = this.delegate.getQuads(direction);
            List<BakedQuad> result = null;
            for (BakedQuad quad : base) {
                TextureAtlasSprite sprite = this.sprites.get(quad.materialInfo().sprite());
                if (sprite == null) continue;
                if (result == null) result = new ArrayList<>(base);
                result.add(EmissiveQuads.overlay(quad, sprite));
            }
            return result == null ? base : result;
        }

        @Override
        public TriState ambientOcclusion() {
            return this.delegate.ambientOcclusion();
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
