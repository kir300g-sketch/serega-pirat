package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Сервер -> клиент: показать комбинацию букв (ticks = 0 или пустая строка — закрыть). */
public record QtePayload(String letters, int ticks) implements CustomPayload {
    public static final CustomPayload.Id<QtePayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "qte"));
    public static final PacketCodec<RegistryByteBuf, QtePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, QtePayload::letters,
            PacketCodecs.VAR_INT, QtePayload::ticks,
            QtePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
