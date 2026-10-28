package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер (ФП АМ): идти в точку. */
public record MovePayload(double x, double y, double z) implements CustomPayload {
    public static final CustomPayload.Id<MovePayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "move"));
    public static final PacketCodec<RegistryByteBuf, MovePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, MovePayload::x,
            PacketCodecs.DOUBLE, MovePayload::y,
            PacketCodecs.DOUBLE, MovePayload::z,
            MovePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
