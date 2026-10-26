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

    /** Сколько треков ещё действуют санкции (0..15). */
    public int sanctions() {
        return (flags >> 4) & 15;
    }

    /** Вступление трека (настройка микрофона): музыка ещё не играет. */
    public boolean intro() {
        return (flags & 2) != 0;
    }

    /** Игрок прижался к стене и «заткнул уши» (после 15 треков): музыка глуше. */
    public boolean coverEars() {
        return (flags & 4) != 0;
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
