package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HymnPayload(boolean on) implements CustomPayload {
    public static final CustomPayload.Id<HymnPayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "hymn"));
    public static final PacketCodec<RegistryByteBuf, HymnPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, HymnPayload::on,
            HymnPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
