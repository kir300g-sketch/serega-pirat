package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер: игрок набрал комбинацию. */
public record QteResultPayload(boolean success) implements CustomPayload {
    public static final CustomPayload.Id<QteResultPayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "qte_result"));
    public static final PacketCodec<RegistryByteBuf, QteResultPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, QteResultPayload::success,
            QteResultPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
