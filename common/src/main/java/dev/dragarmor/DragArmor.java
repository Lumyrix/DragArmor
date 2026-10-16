package dev.dragarmor;

import dev.dragarmor.compat.Compat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class DragArmor implements ClientModInitializer {

    /** True while the server has asked this mod to stay disabled. Resets on join/leave. */
    public static volatile boolean disabled = false;

    @Override
    public void onInitializeClient() {
        Compat.registerPayload();
        ClientPlayNetworking.registerGlobalReceiver(ControlPayload.TYPE,
                (payload, context) -> disabled = payload.disable());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> disabled = false);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disabled = false);
        Compat.registerFrame(DragArmorLogic::frame);
        ClientTickEvents.END_CLIENT_TICK.register(MerchantTrading::tick);
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("lumlib")) {
            throw new IllegalStateException("LumLib is required by DragArmor: https://modrinth.com/mod/lumlib");
        }
        DragArmorLumConfig.register();
    }
}
