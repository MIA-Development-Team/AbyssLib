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
 * Client entry point for connected models and emissive layers.
 */
@Mod(value = AbyssLibReLink.MOD_ID, dist = Dist.CLIENT)
public class AbyssLibReLink {
    public static final String MOD_ID = "abysslib_relink";

    public static final String NAMESPACE = "relink";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AbyssLibReLink(IEventBus modEventBus, ModContainer modContainer) {
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
