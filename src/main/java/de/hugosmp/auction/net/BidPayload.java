package de.hugosmp.auction.net;

import de.hugosmp.auction.HugoAuction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BidPayload(int auctionId, long amount) implements CustomPayload {
    public static final Id<BidPayload> ID = new Id<>(Identifier.of(HugoAuction.MOD_ID, "bid"));
    public static final PacketCodec<RegistryByteBuf, BidPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, BidPayload::auctionId,
            PacketCodecs.VAR_LONG, BidPayload::amount,
            BidPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
