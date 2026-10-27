package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BambooPayload(String seq, int beat) implements CustomPayload {
    public static final CustomPayload.Id<BambooPayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "bamboo"));
    public static final PacketCodec<RegistryByteBuf, BambooPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, BambooPayload::seq,
            PacketCodecs.VAR_INT, BambooPayload::beat,
            BambooPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
