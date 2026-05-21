package com.example.vibecraftmod;

import com.example.vibecraftmod.hud.ArmorHudOverlay;
import com.example.vibecraftmod.hud.HostileRadarOverlay;
import com.example.vibecraftmod.hud.HudOverlay;
import com.example.vibecraftmod.network.ModPackets;
import com.example.vibecraftmod.settings.ModSettings;
import com.example.vibecraftmod.ui.StandardWidgets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class VibeCraftMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModSettings.init(FabricLoader.getInstance().getConfigDir());
        StandardWidgets.registerAll();
        EntityHighlightManager.register();
        ModPackets.register();
        ModKeybindings.register();
        HudOverlay.register();
        ArmorHudOverlay.register();
        HostileRadarOverlay.register();
    }
}

