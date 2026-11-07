package com.altnoir.abysslib.model.neoforge.client;

import com.altnoir.abysslib.client.ALClientConfig;
import com.altnoir.abysslib.model.api.client.DynamicEmissive;
import com.altnoir.abysslib.model.api.client.models.ALModelAttributes;
import com.altnoir.abysslib.model.api.client.models.neoforge.FactoryManagerImpl;
import com.altnoir.abysslib.model.api.client.neoforge.ALBlockStateModel;
import com.altnoir.abysslib.model.api.client.utils.ALUnbakedModelLoader;
import com.altnoir.abysslib.model.impl.client.DefaultModels;
import com.altnoir.abysslib.model.impl.client.models.EmissiveOverlayModel;
import com.altnoir.abysslib.model.impl.client.models.EmissiveWrappedModel;
import com.altnoir.abysslib.model.impl.loading.ALModelDefinitions;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public final class ALModelSetup {
    private ALModelSetup() {
    }

    public static void init(IEventBus modEventBus) {
        DefaultModels.init();
        modEventBus.addListener(ALModelSetup::onRegisterReloadListeners);
        modEventBus.addListener(ALModelSetup::onModifyBakingResult);
    }

    private static void onRegisterReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(ALModelDefinitions.RELOAD_ID, ALModelDefinitions.RELOAD_LISTENER);
        event.addDependency(ALModelDefinitions.RELOAD_ID, VanillaClientListeners.MODELS);
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        var models = event.getBakingResult().blockStateModels();
        Map<Identifier, Optional<BlockStateModel>> replacements = new HashMap<>();
        Map<BlockStateModel, BlockStateModel> wrappers = new IdentityHashMap<>();
        Map<DynamicEmissive.Provider, Map<BlockStateModel, BlockStateModel>> dynamicWrappers = new IdentityHashMap<>();
        models.replaceAll((state, original) -> {
            Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            JsonObject raw = ALModelDefinitions.getRawData(id);
            BlockStateModel model = replacements.computeIfAbsent(id, key -> Optional.ofNullable(replaceModel(key, event)))
                    .orElse(original);
            if (model == original && raw != null && ALModelAttributes.isEmissive(raw)) {
                model = wrappers.computeIfAbsent(original, EmissiveWrappedModel::new);
            }
            if (ALClientConfig.overlayEnabled()) {
                model = wrappers.computeIfAbsent(model, EmissiveOverlayModel::new);
            }
            DynamicEmissive.Provider provider = DynamicEmissive.provider(id);
            if (provider != null) {
                model = dynamicWrappers.computeIfAbsent(provider, ignored -> new IdentityHashMap<>())
                        .computeIfAbsent(model, key -> new EmissiveOverlayModel(key, provider));
            }
            return model;
        });
    }

    private static BlockStateModel replaceModel(Identifier id, ModelEvent.ModifyBakingResult event) {
        for (Identifier type : FactoryManagerImpl.getTypes()) {
            ALUnbakedModelLoader loader = FactoryManagerImpl.get(type);
            if (loader == null) continue;
            JsonObject json = ALModelDefinitions.getData(type, id);
            if (json == null) continue;
            var model = loader.loadModel(json);
            if (model != null) {
                return new ALBlockStateModel(model, material -> event.getTextureGetter().apply(material.sprite()));
            }
        }
        return null;
    }
}
