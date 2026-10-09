package de.hugosmp.auction.net;

import de.hugosmp.auction.HugoAuction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CreatePayload(long minBid, int minutes) implements CustomPayload {
    public static final Id<CreatePayload> ID = new Id<>(Identifier.of(HugoAuction.MOD_ID, "create"));
    public static final PacketCodec<RegistryByteBuf, CreatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, CreatePayload::minBid,
            PacketCodecs.VAR_INT, CreatePayload::minutes,
            CreatePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
