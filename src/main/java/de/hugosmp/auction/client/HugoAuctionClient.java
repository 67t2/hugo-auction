package de.hugosmp.auction.client;

import de.hugosmp.auction.HugoAuction;
import de.hugosmp.auction.net.AuctionListPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class HugoAuctionClient implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hugoauction.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyBinding.Category.create(Identifier.of(HugoAuction.MOD_ID, "main"))
        ));

        ClientPlayNetworking.registerGlobalReceiver(AuctionListPayload.ID,
                (payload, context) -> AuctionClient.update(payload.auctions()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(new AuctionScreen());
                }
            }
        });
    }
}
