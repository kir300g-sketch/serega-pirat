package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер: птица в Флаппи Бёрд разбилась (счёт — для сообщения в чат). */
public record FlappyDeathPayload(int score) implements CustomPayload {
    public static final CustomPayload.Id<FlappyDeathPayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "flappy_death"));
    public static final PacketCodec<RegistryByteBuf, FlappyDeathPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, FlappyDeathPayload::score,
            FlappyDeathPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
