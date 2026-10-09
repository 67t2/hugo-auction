package de.hugosmp.auction.net;

import de.hugosmp.auction.HugoAuction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record AuctionListPayload(List<AuctionView> auctions) implements CustomPayload {
    public static final Id<AuctionListPayload> ID = new Id<>(Identifier.of(HugoAuction.MOD_ID, "list"));
    public static final PacketCodec<RegistryByteBuf, AuctionListPayload> CODEC =
            AuctionView.CODEC.collect(PacketCodecs.toList()).xmap(AuctionListPayload::new, AuctionListPayload::auctions);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
