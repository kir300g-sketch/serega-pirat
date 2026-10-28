package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BambooResultPayload(int hits, int total) implements CustomPayload {
    public static final CustomPayload.Id<BambooResultPayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "bamboo_result"));
    public static final PacketCodec<RegistryByteBuf, BambooResultPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, BambooResultPayload::hits,
            PacketCodecs.VAR_INT, BambooResultPayload::total,
            BambooResultPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
