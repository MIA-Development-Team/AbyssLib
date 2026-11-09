package com.altnoir.abysslib.client;

import com.altnoir.abysslib.model.AbyssLibReLink;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Client options for emissive texture overlays.
 */
public final class ALClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * 是否启用发光叠加层（默认开启）。
     */
    public static boolean emissiveLayer = true;

    /**
     * 发光叠加层的贴图后缀（默认 {@code "_e"}；留空 = 关闭）。
     */
    public static String emissiveSuffix = "_e";

    /**
     * 不应用发光叠加层的贴图 / 命名空间前缀列表。
     */
    public static List<String> emissiveExclude = List.of();

    private static boolean loaded = false;

    private static final ModConfigSpec.BooleanValue EMISSIVE_LAYER = BUILDER
            .comment("Enable OptiFine-style emissive overlay layers.",
                    "For every block texture <name>, an extra fullbright layer is drawn from <name> + suffix",
                    "(example: minecraft:block/iron_ore -> minecraft:block/iron_ore_e, draw only the glowing pixels there).",
                    "Applies immediately: disabling rebuilds chunks, enabling reloads client resources automatically.")
            .translation(configKey("emissiveLayer"))
            .define("emissiveLayer", true);

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

    public enum Reload {
        NONE,
        CHUNKS,
        MODELS
    }

    /**
     * Syncs the config cache and returns the required client refresh.
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
            return Reload.MODELS;
        }
        if (wasEnabled && !nowEnabled) {
            return Reload.CHUNKS;
        }
        if (nowEnabled && (!wasSuffix.equals(emissiveSuffix) || !wasExclude.equals(emissiveExclude))) {
            return Reload.MODELS;
        }
        return Reload.NONE;
    }

    public static String overlayConfigKey() {
        return emissiveLayer + "\u0000" + emissiveSuffix + "\u0000" + String.join("\u0000", emissiveExclude);
    }

    public static boolean overlayEnabled() {
        return emissiveLayer && !emissiveSuffix.isEmpty();
    }

    public static boolean isExcluded(Identifier texture) {
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
