package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Сервер -> клиент. trackId = -1 значит передышка (тогда ticksLeft = сколько ещё отдыхать). */
public record StatePayload(int trackId, int ticksLeft, int totalTicks, int listened, int flags) implements CustomPayload {
    public static final CustomPayload.Id<StatePayload> ID =
            new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "state"));

    public static final PacketCodec<RegistryByteBuf, StatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, StatePayload::trackId,
            PacketCodecs.VAR_INT, StatePayload::ticksLeft,
            PacketCodecs.VAR_INT, StatePayload::totalTicks,
            PacketCodecs.VAR_INT, StatePayload::listened,
            PacketCodecs.VAR_INT, StatePayload::flags,
            StatePayload::new);

    public boolean paused() {
        return (flags & 1) != 0;
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
