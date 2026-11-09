package com.altnoir.abysslib.client;

import com.altnoir.abysslib.model.AbyssLibReLink;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * AbyssLib 客户端配置（配置文件：{@code config/abysslib-client.toml}）。
 * <p>
 * 目前只有一组「OptiFine 式发光层」设置：给方块贴图叠加一张同后缀的贴图（默认 {@code _e}），
 * 叠加层满亮、不受环境光照影响（只画发光像素即可，例如 {@code iron_ore.png} → {@code iron_ore_e.png}）。
 * <b>默认关闭</b>。
 * <p>
 * 写法与 PoopSky 的 {@code ClientConfig} 一致：静态字段缓存配置值，由 {@link #onLoad} 在
 * {@code ModConfigEvent} 时同步，渲染热路径直接读静态字段。
 */
@OnlyIn(Dist.CLIENT)
public final class ALClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** 是否启用发光叠加层（默认关闭）。 */
    public static boolean emissiveLayer = false;

    /** 发光叠加层的贴图后缀（默认 {@code "_e"}；留空 = 关闭）。 */
    public static String emissiveSuffix = "_e";

    /** 不应用发光叠加层的贴图 / 命名空间前缀列表。 */
    public static List<String> emissiveExclude = List.of();

    /** 是否已经从配置文件加载过一次（用于区分"启动加载"与"运行中改动"）。 */
    private static boolean loaded = false;

    private static final ModConfigSpec.BooleanValue EMISSIVE_LAYER = BUILDER
            .comment("Enable OptiFine-style emissive overlay layers.",
                    "For every block texture <name>, an extra fullbright layer is drawn from <name> + suffix",
                    "(example: minecraft:block/iron_ore -> minecraft:block/iron_ore_e, draw only the glowing pixels there).",
                    "Applies immediately: disabling rebuilds chunks, enabling reloads client resources automatically.")
            .translation(configKey("emissiveLayer"))
            .define("emissiveLayer", false);

    private static final ModConfigSpec.ConfigValue<String> EMISSIVE_SUFFIX = BUILDER
            .comment("Suffix of the emissive overlay texture. OptiFine uses \"_e\".",
                    "Empty string disables the feature. Changing the suffix while enabled reloads client resources.")
            .translation(configKey("emissiveSuffix"))
            .define("emissiveSuffix", "_e");

    private static final ModConfigSpec.ConfigValue<List<? extends String>> EMISSIVE_EXCLUDE = BUILDER
            .comment("Skip emissive overlays for these textures / namespaces (prefix match on the base texture id).",
                    "Examples: \"minecraft:block/iron_ore\" (single texture), \"minecraft:block/\" (all vanilla block textures), \"create:\" (whole namespace).")
            .translation(configKey("emissiveExclude"))
            .defineListAllowEmpty("emissiveExclude", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec CLIENT_SPEC = BUILDER.build();

    private ALClientConfig() {
    }

    private static String configKey(String path) {
        return AbyssLibReLink.MOD_ID + ".configuration." + path;
    }

    /** 配置改动后需要做的事。 */
    public enum Reload {
        /** 什么都没变。 */
        NONE,
        /** 关闭叠加层时重建区块网格。 */
        CHUNKS,
        /** 开启叠加层或修改配对规则时重载资源。 */
        MODELS
    }

    /**
     * 由 {@code ModConfigEvent} 调用，把配置值同步进静态缓存字段，并判断需要做什么。
     * <p>
     * 首次加载返回 {@link Reload#NONE}（启动时模型本来就会按新值烘焙，无需额外动作）。
     */
    public static Reload onLoad(ModConfig config) {
        if (config.getSpec() != CLIENT_SPEC) {
            return Reload.NONE;
        }
        final boolean wasEnabled = overlayEnabled();
        final String wasSuffix = emissiveSuffix;
        final List<String> wasExclude = emissiveExclude;
        final boolean firstLoad = !loaded;

        emissiveLayer = EMISSIVE_LAYER.get();
        emissiveSuffix = EMISSIVE_SUFFIX.get();
        emissiveExclude = List.copyOf(EMISSIVE_EXCLUDE.get());
        loaded = true;

        if (firstLoad) {
            return Reload.NONE;
        }
        final boolean nowEnabled = overlayEnabled();
        if (!wasEnabled && nowEnabled) {
            // 关闭 → 开启：之前根本没包装模型，必须重新烘焙
            return Reload.MODELS;
        }
        if (wasEnabled && !nowEnabled) {
            // 开启 → 关闭：包装还在，只需重建区块网格
            return Reload.CHUNKS;
        }
        if (nowEnabled && (!wasSuffix.equals(emissiveSuffix) || !wasExclude.equals(emissiveExclude))) {
            return Reload.MODELS;
        }
        return Reload.NONE;
    }

    /**
     * {@return 影响"是否发光 / 用哪张贴图"的配置指纹}
     */
    public static String overlayConfigKey() {
        return emissiveLayer + "\u0000" + emissiveSuffix + "\u0000" + String.join("\u0000", emissiveExclude);
    }

    /** {@return 当前是否应启用发光叠加层（开关打开且后缀非空）} */
    public static boolean overlayEnabled() {
        return emissiveLayer && !emissiveSuffix.isEmpty();
    }

    /** {@return 该基贴图是否被 {@code emissiveExclude} 排除（前缀匹配）} */
    public static boolean isExcluded(ResourceLocation texture) {
        if (emissiveExclude.isEmpty()) {
            return false;
        }
        final String id = texture.toString();
        for (String prefix : emissiveExclude) {
            if (!prefix.isEmpty() && id.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
