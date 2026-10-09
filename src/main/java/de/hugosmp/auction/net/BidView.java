package de.hugosmp.auction.net;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.RegistryByteBuf;

public record BidView(String name, long amount) {
    public static final PacketCodec<RegistryByteBuf, BidView> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, BidView::name,
            PacketCodecs.VAR_LONG, BidView::amount,
            BidView::new);
}
