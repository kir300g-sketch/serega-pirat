package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Сервер -> клиент (Ковбой): где ближайший вор (для стрелки над хотбаром). */
public record CompassPayload(boolean valid, double x, double z) implements CustomPayload {
    public static final CustomPayload.Id<CompassPayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "compass"));
    public static final PacketCodec<RegistryByteBuf, CompassPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, CompassPayload::valid,
            PacketCodecs.DOUBLE, CompassPayload::x,
            PacketCodecs.DOUBLE, CompassPayload::z,
            CompassPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
