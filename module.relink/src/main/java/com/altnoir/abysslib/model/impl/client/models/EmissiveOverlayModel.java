package com.altnoir.abysslib.model.impl.client.models;

import com.altnoir.abysslib.client.ALClientConfig;
import com.altnoir.abysslib.model.api.client.DynamicEmissive;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OptiFine 式「发光叠加层」（{@code iron_ore.png} → {@code iron_ore_e.png}）：**本库扩展**。
 * <p>
 * 行为对齐 OptiFine（{@code assets/minecraft/optifine/emissive.properties} 的 {@code suffix.emissive}，默认 {@code _e}）：
 * <ul>
 *     <li>对每个已烘焙 quad，取其贴图名 {@code <name>}，若图集里存在 {@code <name> + 后缀} 的贴图，
 *     就额外输出一层用该贴图的 quad（满亮、关方向明暗与 AO，见 {@link EmissiveQuads}）；
 *     <li>叠加层**跟随基贴图的渲染层**；基贴图只有 {@code SOLID} 层时改用 {@code CUTOUT}（与 OptiFine 一致，
 *     这样叠加图里的透明像素会被 alpha 剔除而不是画成黑块）；
 *     <li>基贴图 quad 完全不动（叠加层是**副本**），因此不会污染共享的原版模型实例；
 *     <li>逐贴图可用配置项 {@code emissiveExclude} 排除（前缀匹配基贴图 id）。
 * </ul>
 * 静态模型在图集上传后预烘焙叠加面。其他模型在网格构建时使用该次重载的贴图索引。
 */
public class EmissiveOverlayModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Boolean> DYNAMIC_ACTIVE = new ModelProperty<>();
    private final EmissiveSpriteIndex spriteIndex;
    private final boolean staticGeometry;
    @Nullable
    private final DynamicEmissive.Provider dynamicProvider;
    @Nullable
    private volatile StaticFaces bakedFaces;

    /** 探测用方向（含 null = 不剔面）。 */
    private static final Direction[] PROBE_SIDES = {
            null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    @Nullable
    private final Map<BlockState, CachedPlan> plans;

    /** 叠加层的渲染方案：拿哪一层当基准、叠加层用哪一层、是否独立成一趟。 */
    private record Plan(RenderType baseType, RenderType overlayType, boolean separatePass) {
    }

    private record CachedPlan(ChunkRenderTypeSet base, @Nullable Plan plan) {
    }

    public EmissiveOverlayModel(BakedModel originalModel, EmissiveSpriteIndex spriteIndex,
                                @Nullable DynamicEmissive.Provider dynamicProvider) {
        super(originalModel);
        this.spriteIndex = spriteIndex;
        this.dynamicProvider = dynamicProvider;
        this.staticGeometry = dynamicProvider == null && originalModel.getClass() == SimpleBakedModel.class;
        this.plans = dynamicProvider == null && !this.staticGeometry ? new ConcurrentHashMap<>() : null;
    }

    public boolean hasStaticGeometry() {
        return this.staticGeometry;
    }

    public void bakeOverlays(RandomSource random) {
        var sprites = this.spriteIndex.sprites(false);
        if (sprites.isEmpty()) return;

        Map<Direction, Face> culled = null;
        Face unculled = bakeFace(null, random, sprites);
        for (int i = 1; i < PROBE_SIDES.length; i++) {
            Direction side = PROBE_SIDES[i];
            Face face = bakeFace(side, random, sprites);
            if (face != null) {
                if (culled == null) culled = new EnumMap<>(Direction.class);
                culled.put(side, face);
            }
        }
        if (unculled != null || culled != null) {
            this.bakedFaces = new StaticFaces(unculled, culled == null ? Map.of() : Map.copyOf(culled));
        }
    }

    @Nullable
    private Face bakeFace(@Nullable Direction side, RandomSource random, Map<TextureAtlasSprite, TextureAtlasSprite> sprites) {
        var base = this.originalModel.getQuads(null, side, random);
        var overlays = EmissiveQuads.fullbright(base, quad -> sprites.get(quad.getSprite()));
        return overlays.isEmpty() ? null : new Face(List.copyOf(overlays), List.copyOf(merge(base, overlays)));
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        ModelData result = originalModel.getModelData(level, pos, state, data);
        return dynamicProvider == null || this.spriteIndex.sprites(true).isEmpty() ? result : result.derive()
                .with(DYNAMIC_ACTIVE, dynamicProvider.isActive(level, pos, state)).build();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        if (!hasOverlays(data)) return originalModel.getRenderTypes(state, rand, data);
        if (this.staticGeometry) {
            var base = originalModel.getRenderTypes(state, rand, data);
            return base.isEmpty() ? base : withOverlayLayer(base, overlayType(base));
        }
        var cached = cachedPlan(state, rand, data);
        var plan = cached.plan();
        if (plan == null) {
            return cached.base();
        }
        return withOverlayLayer(cached.base(), plan.overlayType());
    }

    private static ChunkRenderTypeSet withOverlayLayer(ChunkRenderTypeSet base, RenderType overlay) {
        return base.contains(overlay) ? base : ChunkRenderTypeSet.union(base, ChunkRenderTypeSet.of(overlay));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        final List<BakedQuad> base = originalModel.getQuads(state, side, rand, data, renderType);
        if (state == null || renderType == null || !hasOverlays(data)) {
            return base;
        }
        if (this.staticGeometry) {
            var baseTypes = originalModel.getRenderTypes(state, rand, data);
            if (baseTypes.isEmpty() || renderType != overlayType(baseTypes)) return base;
            var faces = this.bakedFaces;
            var face = side == null ? faces.unculled() : faces.culled().get(side);
            if (baseTypes.contains(renderType)) return face == null ? base : face.merged();
            return face == null ? List.of() : face.overlays();
        }
        final Plan plan = cachedPlan(state, rand, data).plan();
        if (plan == null || renderType != plan.overlayType()) {
            return base;
        }
        var source = plan.baseType() == renderType ? base : originalModel.getQuads(state, side, rand, data, plan.baseType());
        var sprites = this.spriteIndex.sprites(this.dynamicProvider != null);
        final List<BakedQuad> overlays = EmissiveQuads.fullbright(source, quad -> sprites.get(quad.getSprite()));
        if (plan.separatePass()) {
            // 叠加层单独一趟（SOLID → CUTOUT），这一趟只画叠加层
            return overlays;
        }
        return overlays.isEmpty() ? base : merge(base, overlays);
    }

    /** 叠加层与基贴图同层时由 5 参 {@code getQuads} 负责；3 参（原版方块渲染不走这里）保持原样。 */
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return originalModel.getQuads(state, side, rand);
    }

    private boolean hasOverlays(ModelData data) {
        if (dynamicProvider != null) {
            return Boolean.TRUE.equals(data.get(DYNAMIC_ACTIVE)) && !this.spriteIndex.sprites(true).isEmpty();
        }
        return ALClientConfig.overlayEnabled() && (this.staticGeometry
                ? this.bakedFaces != null : !this.spriteIndex.sprites(false).isEmpty());
    }

    private CachedPlan cachedPlan(BlockState state, RandomSource rand, ModelData data) {
        boolean dynamic = this.dynamicProvider != null;
        if (this.plans != null) {
            CachedPlan cached = this.plans.get(state);
            if (cached != null) return cached;
        }
        final ChunkRenderTypeSet base = originalModel.getRenderTypes(state, rand, data);
        var sprites = this.spriteIndex.sprites(dynamic);
        final CachedPlan created = new CachedPlan(base, base.isEmpty() ? null : probe(state, rand, data, base, sprites));
        if (this.plans != null) this.plans.putIfAbsent(state, created);
        return created;
    }

    /** 探测该状态是否真有带 {@code _e} 同族贴图的 quad，并算出叠加层用哪一层画。 */
    @Nullable
    private Plan probe(BlockState state, RandomSource rand, ModelData data, ChunkRenderTypeSet base,
                       Map<TextureAtlasSprite, TextureAtlasSprite> sprites) {
        final RenderType baseType = primaryType(base);
        for (Direction side : PROBE_SIDES) {
            for (BakedQuad quad : originalModel.getQuads(state, side, rand, data, baseType)) {
                if (sprites.containsKey(quad.getSprite())) {
                    final RenderType overlayType = overlayType(base);
                    return new Plan(baseType, overlayType, !base.contains(overlayType));
                }
            }
        }
        return null;
    }

    /** 叠加层要用的渲染层：优先复用基贴图已有的带 alpha 剔除/混合的层，只有 SOLID 时才新开 CUTOUT。 */
    private static RenderType overlayType(ChunkRenderTypeSet base) {
        if (base.contains(RenderType.cutout())) {
            return RenderType.cutout();
        }
        if (base.contains(RenderType.cutoutMipped())) {
            return RenderType.cutoutMipped();
        }
        if (base.contains(RenderType.translucent())) {
            return RenderType.translucent();
        }
        return RenderType.cutout();
    }

    /** 取基贴图的主渲染层（探测 quad 用）。 */
    private static RenderType primaryType(ChunkRenderTypeSet base) {
        if (base.contains(RenderType.solid())) {
            return RenderType.solid();
        }
        if (base.contains(RenderType.cutoutMipped())) {
            return RenderType.cutoutMipped();
        }
        if (base.contains(RenderType.cutout())) {
            return RenderType.cutout();
        }
        if (base.contains(RenderType.translucent())) {
            return RenderType.translucent();
        }
        final List<RenderType> types = base.asList();
        return types.isEmpty() ? RenderType.solid() : types.get(0);
    }

    private static List<BakedQuad> merge(List<BakedQuad> base, List<BakedQuad> overlays) {
        var merged = new ArrayList<BakedQuad>(base.size() + overlays.size());
        merged.addAll(base);
        merged.addAll(overlays);
        return merged;
    }

    private record StaticFaces(@Nullable Face unculled, Map<Direction, Face> culled) {
    }

    private record Face(List<BakedQuad> overlays, List<BakedQuad> merged) {
    }
}
