package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TiltAttemptPayload(int count) implements CustomPayload {
    public static final CustomPayload.Id<TiltAttemptPayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "tilt_attempt"));
    public static final PacketCodec<RegistryByteBuf, TiltAttemptPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, TiltAttemptPayload::count,
            TiltAttemptPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
