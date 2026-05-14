package com.example.vibecraftmod;

import com.example.vibecraftmod.hud.ArmorHudOverlay;
import com.example.vibecraftmod.hud.ClaudeHudOverlay;
import com.example.vibecraftmod.network.ModPackets;
import com.example.vibecraftmod.settings.ClaudeSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class VibeCraftMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClaudeSettings.init(FabricLoader.getInstance().getConfigDir());
        ModPackets.register();
        ModKeybindings.register();
        ClaudeHudOverlay.register();
        ArmorHudOverlay.register();
    }
}
