package com.altnoir.abysslib.model;

import com.altnoir.abysslib.client.ALClientConfig;
import com.altnoir.abysslib.model.neoforge.client.ALModelSetup;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

/**
 * <b>AbyssLib - ReLink</b>（modid {@code abysslib_relink}）：贴图材质链接模块的客户端入口。
 * <p>
 * 负责内置模型加载器（CTM / 动态模型 / 发光）的客户端接线：
 * 注册默认模型类型、几何加载器 {@code relink:model}、顶层模型替换事件，
 * 以及 OptiFine 式发光叠加层的客户端配置（{@link ALClientConfig}）。
 * 全部为客户端内容，专用服务端不会加载（{@code @Mod(dist = CLIENT)}）。
 * <p>
 * <b>命名空间</b>：本模块的资源键/注册表 id 一律用 {@link #NAMESPACE}（{@code relink}），
 * 与 modid（{@code abysslib_relink}）是两回事 —— modid 只用于模组加载与依赖声明，
 * 而 {@code relink:loader} / {@code relink:ctm} / {@code assets/<模组>/relink/} 这些
 * 面向资源包的写法用的是命名空间。
 */
@Mod(value = AbyssLibReLink.MOD_ID, dist = Dist.CLIENT)
public class AbyssLibReLink {
    /** 本模块的 modid（只能小写字母/数字/下划线）。 */
    public static final String MOD_ID = "abysslib_relink";

    /** 本模块拥有的资源键 / 注册表命名空间。资源包与数据包看到的是这个名字。 */
    public static final String NAMESPACE = "relink";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AbyssLibReLink(IEventBus modEventBus, ModContainer modContainer) {
        // 配置文件默认名 = <modid>-client.toml，即 config/abysslib_relink-client.toml
        modContainer.registerConfig(ModConfig.Type.CLIENT, ALClientConfig.CLIENT_SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(AbyssLibReLink::onConfigLoad);

        ALModelSetup.init(modEventBus);
    }

    private static void onConfigLoad(ModConfigEvent event) {
        final ALClientConfig.Reload reload = ALClientConfig.onLoad(event.getConfig());
        if (reload == ALClientConfig.Reload.NONE || !(event instanceof ModConfigEvent.Reloading)) {
            return;
        }
        // 运行中改动了发光叠加层设置：区块网格里已经烘焙了旧的 quad，必须让客户端重算才能立刻生效
        // （否则玩家关掉开关后矿物还会继续发光）。
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (reload == ALClientConfig.Reload.MODELS) {
            LOGGER.info("AbyssLib/ReLink: emissive overlay settings changed, reloading client resources");
            minecraft.execute(minecraft::reloadResourcePacks);
        } else if (minecraft.level != null) {
            LOGGER.info("AbyssLib/ReLink: emissive overlay settings changed, rebuilding chunks");
            minecraft.execute(minecraft.levelRenderer::allChanged);
        }
    }
}
