package com.herobrine.ritual.client;

import com.herobrine.ritual.HerobrineMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = HerobrineMod.MODID, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HerobrineMod.HEROBRINE_BOSS.get(), HerobrineRenderer::new);
        event.registerEntityRenderer(HerobrineMod.HEROBRINE_COMPANION.get(), HerobrineRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(HerobrineMod.COMPANION_MENU.get(), CompanionScreen::new);
    }
}
