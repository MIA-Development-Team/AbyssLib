package com.altnoir.abysslib.model.impl.client.models;

import com.altnoir.abysslib.model.api.client.utils.ALModelUtils;
import com.altnoir.abysslib.client.ALClientConfig;
import com.altnoir.abysslib.model.api.client.DynamicEmissive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * 性能：未启用 / 该方块没有叠加层时，{@code getQuads} 直接返回原列表（不拷贝、不分配）；
 * 「该状态有无叠加层 + 它的渲染层」与「某贴图有无 {@code _e} 同族图」都带缓存
 * （{@code ConcurrentHashMap}，区块网格在工作线程构建），配置指纹一变即失效。
 * <p>
 * 开关与后缀来自 {@link ALClientConfig}（默认关闭）。
 */
public class EmissiveOverlayModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Boolean> DYNAMIC_ACTIVE = new ModelProperty<>();
    @Nullable
    private final DynamicEmissive.Provider dynamicProvider;

    /** 探测用方向（含 null = 不剔面）。 */
    private static final Direction[] PROBE_SIDES = {
            null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    /** 叠加贴图缓存：键是**叠加贴图的 id**（基贴图 + 当前后缀），所以改后缀后不会命中旧结果。 */
    private final Map<ResourceLocation, Optional<TextureAtlasSprite>> overlaySprites = new ConcurrentHashMap<>();
    /** 每个方块状态的渲染方案：连同配置指纹一起缓存。 */
    private final Map<BlockState, CachedPlan> plans = new ConcurrentHashMap<>();

    /** 叠加层的渲染方案：拿哪一层当基准、叠加层用哪一层、是否独立成一趟。 */
    private record Plan(RenderType baseType, RenderType overlayType, boolean separatePass) {
    }

    /** 某状态的基贴图渲染层 + 叠加层方案，附计算时的配置指纹。 */
    private record CachedPlan(String configKey, ChunkRenderTypeSet base, Optional<Plan> plan) {
    }

    public EmissiveOverlayModel(BakedModel originalModel) {
        super(originalModel);
        this.dynamicProvider = null;
    }

    public EmissiveOverlayModel(BakedModel originalModel, DynamicEmissive.Provider dynamicProvider) {
        super(originalModel);
        this.dynamicProvider = dynamicProvider;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        ModelData result = originalModel.getModelData(level, pos, state, data);
        return dynamicProvider == null ? result : result.derive()
                .with(DYNAMIC_ACTIVE, dynamicProvider.isActive(level, pos, state)).build();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        final CachedPlan cached = cachedPlan(state, rand, data);
        final Plan plan = cached.plan().orElse(null);
        if (plan == null) {
            return cached.base();
        }
        return ChunkRenderTypeSet.union(cached.base(), ChunkRenderTypeSet.of(plan.overlayType()));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        final List<BakedQuad> base = originalModel.getQuads(state, side, rand, data, renderType);
        if (state == null || renderType == null) {
            return base;
        }
        final Plan plan = cachedPlan(state, rand, data).plan().orElse(null);
        if (plan == null || renderType != plan.overlayType()) {
            return base;
        }
        final List<BakedQuad> overlays = EmissiveQuads.fullbright(
                originalModel.getQuads(state, side, rand, data, plan.baseType()),
                quad -> overlaySprite(quad.getSprite()));
        if (plan.separatePass()) {
            // 叠加层单独一趟（SOLID → CUTOUT），这一趟只画叠加层
            return overlays;
        }
        if (overlays.isEmpty()) {
            return base;
        }
        final List<BakedQuad> merged = new ArrayList<>(base.size() + overlays.size());
        merged.addAll(base);
        merged.addAll(overlays);
        return merged;
    }

    /** 叠加层与基贴图同层时由 5 参 {@code getQuads} 负责；3 参（原版方块渲染不走这里）保持原样。 */
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return originalModel.getQuads(state, side, rand);
    }

    /**
     * 取（必要时计算并缓存）该状态的渲染方案。
     * <p>
     * 关闭功能时不写缓存：此时每条查询都直接返回"基贴图渲染层 + 无叠加层"，不必为全部方块状态留缓存条目。
     */
    private CachedPlan cachedPlan(BlockState state, RandomSource rand, ModelData data) {
        if (dynamicProvider != null) {
            final ChunkRenderTypeSet base = originalModel.getRenderTypes(state, rand, data);
            final Optional<Plan> plan = !Boolean.TRUE.equals(data.get(DYNAMIC_ACTIVE)) || base.isEmpty()
                    ? Optional.empty() : probe(state, rand, data, base);
            return new CachedPlan("dynamic", base, plan);
        }
        final String configKey = ALClientConfig.overlayConfigKey();
        final CachedPlan cached = plans.get(state);
        if (cached != null && cached.configKey().equals(configKey)) {
            return cached;
        }
        final ChunkRenderTypeSet base = originalModel.getRenderTypes(state, rand, data);
        final Optional<Plan> plan = (!ALClientConfig.overlayEnabled() || base.isEmpty())
                ? Optional.empty()
                : probe(state, rand, data, base);
        final CachedPlan created = new CachedPlan(configKey, base, plan);
        if (ALClientConfig.overlayEnabled()) {
            plans.put(state, created);
        }
        return created;
    }

    /** 探测该状态是否真有带 {@code _e} 同族贴图的 quad，并算出叠加层用哪一层画。 */
    private Optional<Plan> probe(BlockState state, RandomSource rand, ModelData data, ChunkRenderTypeSet base) {
        final RenderType baseType = primaryType(base);
        for (Direction side : PROBE_SIDES) {
            for (BakedQuad quad : originalModel.getQuads(state, side, rand, data, baseType)) {
                if (overlaySprite(quad.getSprite()) != null) {
                    final RenderType overlayType = overlayType(base);
                    final boolean separatePass = !base.contains(overlayType);
                    ALModelUtils.LOGGER.debug("AbyssLib/ReLink: emissive overlay enabled for '{}' (base={}, overlay={}, separatePass={})",
                            state, typeName(baseType), typeName(overlayType), separatePass);
                    return Optional.of(new Plan(baseType, overlayType, separatePass));
                }
            }
        }
        return Optional.empty();
    }

    /** 基贴图 {@code <name>} → 叠加贴图 {@code <name> + 后缀}（不存在返回 null，带缓存、配置实时读取）。 */
    @Nullable
    private TextureAtlasSprite overlaySprite(TextureAtlasSprite baseSprite) {
        final String suffix = dynamicProvider == null ? ALClientConfig.emissiveSuffix : DynamicEmissive.TEXTURE_SUFFIX;
        if (suffix.isEmpty()) {
            return null;
        }
        final ResourceLocation base = baseSprite.contents().name();
        if (dynamicProvider == null && ALClientConfig.isExcluded(base)) {
            return null;
        }
        final ResourceLocation overlayId = base.withSuffix(suffix);
        return overlaySprites.computeIfAbsent(overlayId, id -> {
            // 直接查图集：命不中会拿到 missing sprite，且**不会**像 Material 贴图获取器那样刷
            // "Failed to retrieve texture ... from atlas"（大整合包里每张基贴图都会查一次 `_e`）。
            final TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager()
                    .getAtlas(TextureAtlas.LOCATION_BLOCKS)
                    .getSprite(id);
            if (sprite == null || MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name())) {
                return Optional.empty();
            }
            return Optional.of(sprite);
        }).orElse(null);
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

    /** 日志用短名（{@code RenderType} 的 toString 太长）。 */
    private static String typeName(RenderType type) {
        if (type == RenderType.solid()) {
            return "solid";
        }
        if (type == RenderType.cutoutMipped()) {
            return "cutout_mipped";
        }
        if (type == RenderType.cutout()) {
            return "cutout";
        }
        if (type == RenderType.translucent()) {
            return "translucent";
        }
        return "other";
    }
}
