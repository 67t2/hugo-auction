package de.hugosmp.auction.net;

import de.hugosmp.auction.HugoAuction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record EndPayload(int auctionId) implements CustomPayload {
    public static final Id<EndPayload> ID = new Id<>(Identifier.of(HugoAuction.MOD_ID, "end"));
    public static final PacketCodec<RegistryByteBuf, EndPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, EndPayload::auctionId,
            EndPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
