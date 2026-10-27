package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FlowerStatePayload(int cdz, int cdx, int cdc, int omega) implements CustomPayload {
    public static final CustomPayload.Id<FlowerStatePayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "flower_state"));
    public static final PacketCodec<RegistryByteBuf, FlowerStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, FlowerStatePayload::cdz,
            PacketCodecs.VAR_INT, FlowerStatePayload::cdx,
            PacketCodecs.VAR_INT, FlowerStatePayload::cdc,
            PacketCodecs.VAR_INT, FlowerStatePayload::omega,
            FlowerStatePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
