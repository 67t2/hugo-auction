package de.hugosmp.auction.net;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.List;

public record AuctionView(int id, String sellerName, ItemStack stack, long minBid, long remainingMs, List<BidView> bids) {
    public static final PacketCodec<RegistryByteBuf, AuctionView> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, AuctionView::id,
            PacketCodecs.STRING, AuctionView::sellerName,
            ItemStack.OPTIONAL_PACKET_CODEC, AuctionView::stack,
            PacketCodecs.VAR_LONG, AuctionView::minBid,
            PacketCodecs.VAR_LONG, AuctionView::remainingMs,
            BidView.CODEC.collect(PacketCodecs.toList()), AuctionView::bids,
            AuctionView::new);
}
