package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Клиент -> сервер (Ластпик): поёт ли игрок сейчас и работает ли микрофон.
 * ВАЖНО: передаётся только два флага. Звук с микрофона НЕ записывается и НЕ отправляется.
 */
public record SingPayload(boolean singing, boolean micOk) implements CustomPayload {
    public static final CustomPayload.Id<SingPayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "sing"));
    public static final PacketCodec<RegistryByteBuf, SingPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, SingPayload::singing,
            PacketCodecs.BOOL, SingPayload::micOk,
            SingPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
