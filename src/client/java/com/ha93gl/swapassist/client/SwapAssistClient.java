package com.ha93gl.swapassist.client;

import net.fabricmc.api.ClientModInitializer;

public class SwapAssistClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("Swap Assist loaded!");
    }
}