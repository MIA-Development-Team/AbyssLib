package com.altnoir.abysslib.model.neoforge.client;

import com.altnoir.abysslib.model.ALAthenaCompat;
import com.altnoir.abysslib.model.api.client.DynamicEmissive;
import com.altnoir.abysslib.model.api.client.models.ALModelAttributes;
import com.altnoir.abysslib.model.api.client.models.neoforge.FactoryManagerImpl;
import com.altnoir.abysslib.model.api.client.neoforge.ALUnbakedModel;
import com.altnoir.abysslib.model.api.client.utils.ALUnbakedModelLoader;
import com.altnoir.abysslib.model.api.client.utils.ALModelUtils;
import com.altnoir.abysslib.model.impl.client.DefaultModels;
import com.altnoir.abysslib.model.impl.client.models.EmissiveOverlayModel;
import com.altnoir.abysslib.model.impl.client.models.EmissiveWrappedModel;
import com.altnoir.abysslib.model.impl.loading.ALModelDefinitions;
import com.altnoir.abysslib.client.ALClientConfig;
import com.google.gson.JsonObject;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 内置模型加载器的客户端接线（由 {@code AbyssLibReLink} 调用，专用服务端不会加载本类）。
 * <p>
 * 与上游 neoforge 模块的差异：
 * <ul>
 *     <li>上游用 {@code @Mod("athena")} 入口类；本库由 {@code AbyssLibReLink} 统一调用 {@link #init}。</li>
 *     <li>上游用 mixin 注入 {@code ModelBakery} 内 lambda（{@code method_61072}）来替换顶层模型；
 *     本库改用公开事件 {@link ModelEvent.ModifyBakingResult}（见 {@link #onModifyBakingResult}），
 *     避免依赖合成方法名（MDG 不生成 refmap，合成名在 dev/prod 不可靠）。</li>
 *     <li>几何加载器 id 由 {@code athena:athena} 改为 {@code relink:model}
 *     （{@code athena:athena} 仍会作为兼容别名注册，除非上游 Athena 在场，见 {@link ALAthenaCompat}）。</li>
 *     <li>新增「包裹原版模型发光」模式（本库扩展）：blockstate 根只写 {@code "relink:emissive": true}
 *     时不替换模型，只把原版模型包一层全亮，见 {@link #wrapEmissive}。</li>
 * </ul>
 */
public final class ALModelSetup {

    /** 模型 JSON 中 {@code "loader"} 字段使用的几何加载器 id。 */
    public static final ResourceLocation GEOMETRY_LOADER_ID =
            ResourceLocation.fromNamespaceAndPath(DefaultModels.NAMESPACE, "model");

    private ALModelSetup() {
    }

    public static void init(IEventBus modEventBus) {
        DefaultModels.init();
        modEventBus.addListener(ALModelSetup::onRegisterGeometryLoaders);
        modEventBus.addListener(ALModelSetup::onModifyBakingResult);
    }

    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(GEOMETRY_LOADER_ID, new ALGeometryLoader());
        // 兼容层：上游 Athena 的几何加载器 id 是 athena:athena。上游在场时 ALAthenaCompat 会返回 false，
        // 避免与上游抢同一个 id 造成注册冲突。
        if (ALAthenaCompat.enabled()) {
            event.register(ALAthenaCompat.ATHENA_GEOMETRY_LOADER_ID, new ALGeometryLoader());
        }
    }

    /**
     * 取代上游 neoforge {@code ModelBakeryMixin}：对每个已烘焙的顶层模型做三件事——
     * <ol>
     *     <li>依次询问所有已注册的模型类型（取决于 {@code relink:loader}），命中者用本库模型替换；</li>
     *     <li>否则，若该方块声明了 {@code relink:emissive}，则把**原版模型**包一层全亮（本库扩展）；</li>
     *     <li>若客户端配置启用了 OptiFine 式发光叠加层，再包一层 {@link EmissiveOverlayModel}（本库扩展）。</li>
     * </ol>
     * 与上游 mixin 的等价性：上游是在 {@code bakeUncached} 之前把 unbaked 模型换掉，效果同为
     * "该顶层模型最终由本库模型生成"。差异仅在作用域——本事件只覆盖顶层模型（{@code topLevelModels}），
     * 不含其它模型内部对被替换 id 的引用；本库的用法（blockstate 变体/模型文件）均属顶层。
     */
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        final boolean overlay = ALClientConfig.overlayEnabled();
        event.getModels().replaceAll((id, original) -> {
            BakedModel model = tryModelType(event, id);
            if (model == null) {
                model = wrapEmissive(id, original);
            }
            if (overlay) {
                model = wrapOverlay(id, model);
            }
            var dynamicProvider = DynamicEmissive.provider(id.id());
            if (dynamicProvider != null && !"inventory".equals(id.getVariant())) {
                model = new EmissiveOverlayModel(model, dynamicProvider);
            }
            return model;
        });
    }

    /** 按 {@code relink:loader} 声明的类型接管模型；没有命中任何类型时返回 {@code null}。 */
    @Nullable
    private static BakedModel tryModelType(ModelEvent.ModifyBakingResult event, ModelResourceLocation id) {
        for (ResourceLocation type : FactoryManagerImpl.getTypes()) {
            ALUnbakedModelLoader loader = FactoryManagerImpl.get(type);
            if (loader == null) {
                continue;
            }
            UnbakedModel model = loader.loadModel(id);
            if (model != null) {
                // "inventory" 变体在 loadModel 内已过滤。
                // FactoryManagerImpl 固定以 ALUnbakedModel::new 包装模型类型，故此处可直接用其
                // 不依赖 ModelBaker 的烘焙入口（ModifyBakingResult 只提供 ModelBakery）。
                ALModelUtils.LOGGER.debug("AbyssLib/ReLink: replaced top-level model '{}#{}' with model type {}", id.id(), id.getVariant(), type);
                return ((ALUnbakedModel) model).bake(event.getTextureGetter());
            }
        }
        return null;
    }

    /**
     * 本库扩展：blockstate 根（或 {@code assets/<ns>/abysslib/<方块>.json} 定义）里只写
     * {@code "relink:emissive": true}、不写 {@code relink:loader} 时，保留 {@code variants} 指向的
     * 原版模型，仅把它的 quad 全部改成满亮（形状/贴图/连接关系不变，无需任何贴图字段）。
     * <p>
     * 物品变体（{@code inventory}）不处理，与模型类型的行为保持一致。
     */
    private static BakedModel wrapEmissive(ModelResourceLocation id, BakedModel original) {
        if ("inventory".equals(id.getVariant())) {
            return original;
        }
        final JsonObject json = ALModelDefinitions.getRawData(id.id());
        if (json == null || !ALModelAttributes.isEmissive(json)) {
            return original;
        }
        ALModelUtils.LOGGER.debug("AbyssLib/ReLink: wrapped top-level model '{}#{}' with emissive (no relink:loader)", id.id(), id.getVariant());
        return new EmissiveWrappedModel(original);
    }

    /**
     * 本库扩展：OptiFine 式发光叠加层（{@code iron_ore.png} → {@code iron_ore_e.png}）。
     * 由客户端配置 {@code abysslib-client.toml} 的 {@code emissiveLayer} 开关控制，默认关闭；
     * 具体行为见 {@link EmissiveOverlayModel}。
     */
    private static BakedModel wrapOverlay(ModelResourceLocation id, BakedModel model) {
        if ("inventory".equals(id.getVariant())) {
            return model;
        }
        return new EmissiveOverlayModel(model);
    }
}
