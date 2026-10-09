package de.hugosmp.auction;

import de.hugosmp.auction.net.AuctionListPayload;
import de.hugosmp.auction.net.BidPayload;
import de.hugosmp.auction.net.CreatePayload;
import de.hugosmp.auction.net.EndPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HugoAuction implements ModInitializer {
    public static final String MOD_ID = "hugoauction";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(AuctionListPayload.ID, AuctionListPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CreatePayload.ID, CreatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BidPayload.ID, BidPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EndPayload.ID, EndPayload.CODEC);

        AuctionManager mgr = AuctionManager.INSTANCE;

        ServerLifecycleEvents.SERVER_STARTED.register(mgr::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(mgr::stop);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 == 0) mgr.tick();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> mgr.onJoin(handler.player));

        ServerPlayNetworking.registerGlobalReceiver(CreatePayload.ID,
                (payload, ctx) -> mgr.create(ctx.player(), payload.minBid(), payload.minutes()));
        ServerPlayNetworking.registerGlobalReceiver(BidPayload.ID,
                (payload, ctx) -> mgr.bid(ctx.player(), payload.auctionId(), payload.amount()));
        ServerPlayNetworking.registerGlobalReceiver(EndPayload.ID,
                (payload, ctx) -> mgr.end(ctx.player(), payload.auctionId()));

        LOGGER.info("6-7 T2 Auction House geladen.");
    }
}
